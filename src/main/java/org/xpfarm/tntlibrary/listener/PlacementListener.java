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
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.xpfarm.tntlibrary.TntLibraryPlugin;
import org.xpfarm.tntlibrary.block.BombBlocks;
import org.xpfarm.tntlibrary.command.Permissions;
import org.xpfarm.tntlibrary.core.CustomTnt;
import org.xpfarm.tntlibrary.item.BombItems;

/**
 * Turns placing a bomb item into a real, claimed {@code note_block} state — the block that renders as
 * the bomb's 3D cube on both editions (Java resource pack + Geyser Custom Blocks over that state).
 *
 * <p>When a player places a stack carrying a bomb id ({@link BombItems#idOf}), this <em>rewrites</em>
 * the just-placed block to the bomb's donor state ({@link BombBlocks#blockDataFor}) rather than
 * letting a vanilla TNT block stand. The event is <b>not</b> cancelled in the success path, so vanilla
 * consumes the item as normal; physics are suppressed on the rewrite so the note block's instrument
 * cannot re-derive from the block beneath it (the {@code BombGuardListener} keeps it locked
 * thereafter). A disabled/unregistered bomb, or one the player lacks permission for, <em>is</em>
 * cancelled with a message so it can never fall through and place as plain TNT.
 *
 * <h2>Bedrock safety</h2>
 *
 * <p>Placing a real block, the action-bar feedback, and the sound all behave identically for Bedrock
 * players through Geyser — a Bedrock player places the cube like any block.
 *
 * <h2>Runtime-only</h2>
 *
 * <p>Event delivery, item reading, and block mutation all need a live server, so this is verified at
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
            // A bomb item exists but its id is not a registered/enabled bomb: never place it.
            event.setCancelled(true);
            player.sendActionBar(Component.text("That bomb is disabled on this server.",
                    NamedTextColor.RED));
            return;
        }

        if (!player.hasPermission(Permissions.use(id))) {
            event.setCancelled(true);
            player.sendActionBar(Component.text("You don't have permission to place that bomb.",
                    NamedTextColor.RED));
            return;
        }

        // Success: let the place stand (so the item is consumed) but rewrite it to the claimed
        // note-block state with physics off, so the cube renders and the instrument stays locked.
        Block placed = event.getBlockPlaced();
        placed.setBlockData(BombBlocks.blockDataFor(id), false);
        placed.getWorld().playSound(placed.getLocation().toCenterLocation(),
                Sound.BLOCK_SAND_PLACE, 0.8f, 1.1f);
    }
}
