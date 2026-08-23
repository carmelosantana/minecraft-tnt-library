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
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.xpfarm.tntlibrary.TntLibraryPlugin;
import org.xpfarm.tntlibrary.command.Permissions;
import org.xpfarm.tntlibrary.core.CustomTnt;
import org.xpfarm.tntlibrary.rig.RigHandle;
import org.xpfarm.tntlibrary.rig.RigState;
import org.xpfarm.tntlibrary.rig.TntRig;

/**
 * Ignites a placed bomb rig when a player right-clicks it with flint &amp; steel.
 *
 * <p>The rig's {@code Interaction} entity is responsive, so right-clicking it fires {@link
 * PlayerInteractEntityEvent}. When the clicked entity is a tagged, still-{@link RigState#PLACED} rig
 * and the used hand holds {@link Material#FLINT_AND_STEEL}, this primes the rig with the bomb's fuse:
 * the {@link TntRig} runs the fuse and, when it elapses, the callback detonates the bomb and removes
 * the rig. Priming flips the rig's persisted state to {@link RigState#PRIMED} synchronously, which
 * naturally deduplicates the second (off-hand) interaction event of the same click.
 *
 * <h2>Phase-1 limitation — flint &amp; steel only</h2>
 *
 * <p>A placed bomb is a display-entity rig, not a real ignitable block, so redstone current and
 * spreading fire cannot ignite it this phase. Only this right-click path exists; that is deliberate,
 * not an omission.
 *
 * <h2>Bedrock safety</h2>
 *
 * <p>Right-clicking an {@code Interaction} entity, the action-bar feedback, and the sounds all work
 * identically for Bedrock players through Geyser. Nothing here uses a Java-only client feature.
 *
 * <h2>Runtime-only</h2>
 *
 * <p>Everything here needs a live server (events, entity tags, scheduling), so it is verified at the
 * runtime gate rather than in JUnit.
 */
public final class IgnitionListener implements Listener {

    private final TntLibraryPlugin plugin;

    public IgnitionListener(TntLibraryPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        TntRig tntRig = plugin.tntRig();
        Entity clicked = event.getRightClicked();

        Optional<String> bombId = tntRig.bombIdOf(clicked);
        if (bombId.isEmpty()) {
            return; // not a rig entity
        }
        String id = bombId.get();
        Player player = event.getPlayer();

        ItemStack used = handItem(player, event.getHand());
        if (used == null || used.getType() != Material.FLINT_AND_STEEL) {
            return; // wrong tool in this hand — let the other hand's event (if any) decide
        }

        Optional<RigState> state = tntRig.stateOf(clicked);
        if (state.isEmpty() || state.get() != RigState.PLACED) {
            return; // already primed (or unknown state) — nothing to do
        }

        if (!player.hasPermission(Permissions.use(id))) {
            event.setCancelled(true);
            player.sendActionBar(Component.text("You don't have permission to ignite that bomb.",
                    NamedTextColor.RED));
            return;
        }

        Optional<RigHandle> rig = tntRig.findRigAt(clicked.getLocation());
        Optional<CustomTnt> bomb = plugin.registry().get(id);
        if (rig.isEmpty() || bomb.isEmpty()) {
            return; // rig or definition vanished between click and resolve
        }

        // Consume the interaction so no vanilla use-entity behaviour also fires.
        event.setCancelled(true);

        RigHandle handle = rig.get();
        CustomTnt tnt = bomb.get();
        Location center = handle.blockLocation().add(0.5, 0.5, 0.5);

        tntRig.prime(handle, tnt.fuseTicks(), () -> {
            plugin.detonator().detonate(tnt, center, player);
            tntRig.removeRig(handle);
        });

        damageFlintAndSteel(player, event.getHand(), used);
        center.getWorld().playSound(center, Sound.ITEM_FLINTANDSTEEL_USE, 1.0f, 1.0f);
        center.getWorld().playSound(center, Sound.ENTITY_TNT_PRIMED, 1.0f, 1.0f);
        player.sendActionBar(Component.text("Fuse lit!", NamedTextColor.GOLD));
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
