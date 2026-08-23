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
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.NotePlayEvent;
import org.xpfarm.tntlibrary.TntLibraryPlugin;
import org.xpfarm.tntlibrary.block.BombBlocks;
import org.xpfarm.tntlibrary.core.CustomTnt;
import org.xpfarm.tntlibrary.twins.TwinColor;
import org.xpfarm.tntlibrary.twins.TwinLocation;

/**
 * Keeps a placed bomb block behaving like a bomb, not like a note block.
 *
 * <ul>
 *   <li><b>Physics lock</b> — a note block re-derives its {@code instrument} from the block beneath it
 *       on any physics update, which would change its blockstate and break both the render override
 *       and {@link BombBlocks} identity. This cancels physics on bomb blocks so the claimed state
 *       stays put (the Oraxen-style lock).</li>
 *   <li><b>Sound mute</b> — a bomb block never plays the note-block ping.</li>
 *   <li><b>Break drop</b> — breaking a bomb block returns the bomb item (if the bomb is registered),
 *       never a vanilla note block.</li>
 * </ul>
 *
 * <h2>Scope &amp; performance</h2>
 *
 * <p>{@link BlockPhysicsEvent} is high-frequency, so every handler here cheaply short-circuits on
 * {@link Material#NOTE_BLOCK} before the {@link BombBlocks} state check. Only claimed states are ever
 * touched; ordinary note blocks and every other block fall straight through.
 *
 * <h2>Runtime-only</h2>
 *
 * <p>All three paths need a live server (block data, events), so they are verified at the runtime gate
 * rather than in JUnit.
 */
public final class BombGuardListener implements Listener {

    private final TntLibraryPlugin plugin;

    public BombGuardListener(TntLibraryPlugin plugin) {
        this.plugin = plugin;
    }

    /** Cancels physics on bomb blocks so their note-block instrument can never re-derive. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPhysics(BlockPhysicsEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.NOTE_BLOCK) {
            return;
        }
        if (BombBlocks.isBombBlock(block)) {
            event.setCancelled(true);
        }
    }

    /** A bomb block is silent — never plays the note-block ping. */
    @EventHandler(ignoreCancelled = true)
    public void onNotePlay(NotePlayEvent event) {
        if (BombBlocks.isBombBlock(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    /** Breaking a bomb block returns the bomb item, not a vanilla note block. */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Optional<String> bombId = BombBlocks.bombIdOf(block);
        if (bombId.isEmpty()) {
            return;
        }
        String id = bombId.get();
        event.setDropItems(false); // never drop the underlying note block
        Optional<CustomTnt> bomb = plugin.registry().get(id);
        if (bomb.isPresent() && event.getPlayer().getGameMode() != GameMode.CREATIVE) {
            block.getWorld().dropItemNaturally(block.getLocation().toCenterLocation(),
                    bomb.get().createItem());
        }

        // A broken Twin leaves the shared index (same key construction as placement/detonate), so a
        // stale location can never be picked as a partner. A no-op for non-Twin bombs.
        if (TwinColor.isVariant(id)) {
            plugin.placedTwinIndex().remove(
                    new TwinLocation(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ()));
        }
    }
}
