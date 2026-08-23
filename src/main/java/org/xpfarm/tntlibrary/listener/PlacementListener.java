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
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.xpfarm.tntlibrary.TntLibraryPlugin;
import org.xpfarm.tntlibrary.command.Permissions;
import org.xpfarm.tntlibrary.core.CustomTnt;
import org.xpfarm.tntlibrary.item.BombItems;

/**
 * Turns placing a bomb item into a display-entity rig instead of a vanilla TNT block.
 *
 * <p>When a player places a stack that carries a bomb id ({@link BombItems#idOf}), this cancels the
 * vanilla placement — so no real TNT block ever appears — and, if the bomb is registered and the
 * player is permitted, spawns a {@link org.xpfarm.tntlibrary.rig.TntRig} rig at the target cell.
 * Because the event is cancelled the item is not auto-consumed, so exactly one is removed from the
 * used hand here (never in creative). A disabled or unregistered bomb is cancelled with a message so
 * it can never fall through and place as plain TNT.
 *
 * <h2>Bedrock safety</h2>
 *
 * <p>The whole flow is server-authoritative and its only player feedback is an action-bar {@link
 * Component} and a sound — both render identically for Bedrock players through Geyser. There is no
 * Java-only chat prompt or custom-attribute display anywhere in this path.
 *
 * <h2>Runtime-only</h2>
 *
 * <p>Event delivery, item reading, and entity spawning all need a live server, so this is verified at
 * the runtime gate rather than in JUnit.
 */
public final class PlacementListener implements Listener {

    private final TntLibraryPlugin plugin;

    public PlacementListener(TntLibraryPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        ItemStack inHand = event.getItemInHand();
        Optional<String> bombId = BombItems.idOf(inHand);
        if (bombId.isEmpty()) {
            return; // not a bomb item — leave vanilla placement untouched
        }
        String id = bombId.get();
        Player player = event.getPlayer();

        Optional<CustomTnt> bomb = plugin.registry().get(id);
        if (bomb.isEmpty()) {
            // A bomb item exists but its id is not a registered/enabled bomb: never let it place as
            // vanilla TNT.
            event.setCancelled(true);
            actionBar(player, Component.text("That bomb is disabled on this server.",
                    NamedTextColor.RED));
            return;
        }

        if (!player.hasPermission(Permissions.use(id))) {
            event.setCancelled(true);
            actionBar(player, Component.text("You don't have permission to place that bomb.",
                    NamedTextColor.RED));
            return;
        }

        // Cancel the vanilla block, then stand up our display-entity rig in its place.
        event.setCancelled(true);
        Location blockLoc = event.getBlockPlaced().getLocation();
        plugin.tntRig().spawnRig(blockLoc, bomb.get());

        consumeOne(player, event.getHand());
        player.playSound(blockLoc.toCenterLocation(), Sound.BLOCK_SAND_PLACE, 0.8f, 1.1f);
    }

    /**
     * Removes one item from the hand the placement used. A cancelled {@link BlockPlaceEvent} does not
     * consume the stack, so we do it ourselves; creative never consumes, matching vanilla.
     */
    private static void consumeOne(Player player, EquipmentSlot hand) {
        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        PlayerInventory inventory = player.getInventory();
        boolean offHand = hand == EquipmentSlot.OFF_HAND;
        ItemStack used = offHand ? inventory.getItemInOffHand() : inventory.getItemInMainHand();
        int amount = used.getAmount();
        ItemStack remaining;
        if (amount <= 1) {
            remaining = null;
        } else {
            used.setAmount(amount - 1);
            remaining = used;
        }
        if (offHand) {
            inventory.setItemInOffHand(remaining);
        } else {
            inventory.setItemInMainHand(remaining);
        }
    }

    private static void actionBar(Player player, Component message) {
        player.sendActionBar(message);
    }
}
