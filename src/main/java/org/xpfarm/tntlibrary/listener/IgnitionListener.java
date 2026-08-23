/*
 * TNTLibrary - a custom-TNT framework and a set of creative, premium explosives.
 * Copyright (C) 2026 Carmelo Santana
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * See the LICENSE file at the project root for the full license text.
 */
package org.xpfarm.tntlibrary.listener;

import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.xpfarm.tntlibrary.TntLibraryPlugin;
import org.xpfarm.tntlibrary.block.BombBlocks;
import org.xpfarm.tntlibrary.command.Permissions;
import org.xpfarm.tntlibrary.core.CustomTnt;

/**
 * Ignites a placed bomb block with real-TNT parity: flint &amp; steel (right-click or dispenser),
 * fire/lava spread, and redstone current all light the fuse — the same triggers that prime vanilla
 * TNT. Because a bomb is now a real block, none of the Phase-1 "flint &amp; steel only" limitation
 * remains.
 *
 * <h2>The three paths</h2>
 *
 * <ul>
 *   <li>{@link PlayerInteractEvent} — right-clicking a bomb block with flint &amp; steel lights it
 *       (permission-checked, tool damaged, event cancelled so no fire is placed and the note is not
 *       cycled). Right-clicking it with anything else is cancelled too, so the vanilla note never
 *       changes pitch.</li>
 *   <li>{@link BlockIgniteEvent} — when fire, lava, a dispensed flint &amp; steel, or an explosion
 *       ignites a cell, any bomb block among that cell and its neighbours is lit (environmental, no
 *       permission gate — matches fire priming vanilla TNT).</li>
 *   <li>{@link BlockRedstoneEvent} — a bomb block that becomes powered is lit, like a redstone-fed
 *       vanilla TNT.</li>
 * </ul>
 *
 * <p>The fuse itself ({@link org.xpfarm.tntlibrary.block.BombFuse}) refuses to double-light a block,
 * so overlapping triggers are harmless.
 *
 * <h2>Bedrock safety</h2>
 *
 * <p>Right-clicking a block, the action-bar feedback, and the sounds all work identically for Bedrock
 * players through Geyser.
 *
 * <h2>Runtime-only</h2>
 *
 * <p>Everything here needs a live server (events, block data, scheduling), so it is verified at the
 * runtime gate rather than in JUnit.
 */
public final class IgnitionListener implements Listener {

    private static final BlockFace[] SELF_AND_NEIGHBOURS = {
            BlockFace.SELF, BlockFace.UP, BlockFace.DOWN,
            BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST,
    };

    private final TntLibraryPlugin plugin;

    public IgnitionListener(TntLibraryPlugin plugin) {
        this.plugin = plugin;
    }

    /** Flint &amp; steel lights a bomb block; any other right-click on it is swallowed (no note cycle). */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        Block block = event.getClickedBlock();
        Optional<String> bombId = BombBlocks.bombIdOf(block);
        if (bombId.isEmpty()) {
            return;
        }
        // From here the block is a bomb: never let a vanilla note interaction run.
        event.setCancelled(true);

        Player player = event.getPlayer();
        ItemStack used = handItem(player, event.getHand());
        if (used == null || used.getType() != Material.FLINT_AND_STEEL) {
            return; // suppressed the note cycle; nothing else to do for this hand
        }

        Optional<CustomTnt> bomb = plugin.registry().get(bombId.get());
        if (bomb.isEmpty()) {
            return;
        }
        if (!player.hasPermission(Permissions.use(bombId.get()))) {
            player.sendActionBar(Component.text("You don't have permission to ignite that bomb.",
                    NamedTextColor.RED));
            return;
        }
        if (ignite(block, bomb.get(), player)) {
            damageFlintAndSteel(player, event.getHand(), used);
            block.getWorld().playSound(block.getLocation().toCenterLocation(),
                    Sound.ITEM_FLINTANDSTEEL_USE, 1.0f, 1.0f);
            player.sendActionBar(Component.text("Fuse lit!", NamedTextColor.GOLD));
        }
    }

    /** Fire, lava, dispensed flint &amp; steel, or an explosion lights any adjacent bomb block. */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockIgnite(BlockIgniteEvent event) {
        Block origin = event.getBlock();
        for (BlockFace face : SELF_AND_NEIGHBOURS) {
            Block block = origin.getRelative(face);
            Optional<String> bombId = BombBlocks.bombIdOf(block);
            if (bombId.isEmpty()) {
                continue;
            }
            plugin.registry().get(bombId.get()).ifPresent(bomb -> ignite(block, bomb, null));
        }
    }

    /** A bomb block that becomes powered lights, like a redstone-fed vanilla TNT. */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onRedstone(BlockRedstoneEvent event) {
        if (event.getNewCurrent() <= 0 || event.getNewCurrent() <= event.getOldCurrent()) {
            return; // only a rising edge to powered can prime
        }
        Block origin = event.getBlock();
        for (BlockFace face : SELF_AND_NEIGHBOURS) {
            Block block = origin.getRelative(face);
            Optional<String> bombId = BombBlocks.bombIdOf(block);
            if (bombId.isEmpty()) {
                continue;
            }
            if (block.isBlockPowered() || block.isBlockIndirectlyPowered()) {
                plugin.registry().get(bombId.get()).ifPresent(bomb -> ignite(block, bomb, null));
            }
        }
    }

    /**
     * Lights {@code block}'s fuse for {@code bomb}, wiring the fuse-elapsed callback to the shared
     * {@link org.xpfarm.tntlibrary.detonation.Detonator}. {@code igniter} may be {@code null} for an
     * environmental trigger. Returns whether a new fuse actually started (a block already burning is
     * left alone).
     */
    private boolean ignite(Block block, CustomTnt bomb, Player igniter) {
        if (plugin.bombFuse().isBurning(block)) {
            return false;
        }
        Location center = block.getLocation().toCenterLocation();
        return plugin.bombFuse().light(block, bomb.id(), bomb.fuseTicks(),
                () -> plugin.detonator().detonate(bomb, center, igniter));
    }

    /** The stack in the given hand, or {@code null} if that hand is empty. */
    private static ItemStack handItem(Player player, EquipmentSlot hand) {
        ItemStack stack = hand == EquipmentSlot.OFF_HAND
                ? player.getInventory().getItemInOffHand()
                : player.getInventory().getItemInMainHand();
        return (stack == null || stack.isEmpty()) ? null : stack;
    }

    /**
     * Applies one point of flint-and-steel durability, breaking the tool when it is spent — mirrors
     * vanilla ignition wear. Skipped in creative.
     */
    private static void damageFlintAndSteel(Player player, EquipmentSlot hand, ItemStack tool) {
        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        ItemMeta meta = tool.getItemMeta();
        if (!(meta instanceof Damageable damageable)) {
            return;
        }
        int damage = damageable.getDamage() + 1;
        int max = tool.getType().getMaxDurability();
        if (max > 0 && damage >= max) {
            setHand(player, hand, null); // tool broke
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
            return;
        }
        damageable.setDamage(damage);
        tool.setItemMeta(meta);
    }

    private static void setHand(Player player, EquipmentSlot hand, ItemStack stack) {
        if (hand == EquipmentSlot.OFF_HAND) {
            player.getInventory().setItemInOffHand(stack);
        } else {
            player.getInventory().setItemInMainHand(stack);
        }
    }
}
