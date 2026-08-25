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
import java.util.UUID;
import org.bukkit.GameMode;
import org.bukkit.Location;
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
import org.xpfarm.tntlibrary.block.PlacedBombIndex;
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

    /**
     * Heals a bomb block's claimed note-block state after a neighbor update, recognizing the block by
     * <em>location</em> so a drift that has already happened stays recoverable.
     *
     * <p>On Paper 26.1.2 (MC 1.21.x) a note block re-derives its {@code instrument} from the block
     * beneath it inside {@code Block.updateShape()} — a path that is <em>not</em> gated behind this
     * (cancellable) {@link BlockPhysicsEvent}, so cancelling here cannot keep the claimed instrument
     * (verified at the runtime gate: the event fires and is cancelled, yet the instrument still drifts
     * to {@code harp}/{@code bass}). Left unhealed, the drift breaks both the render override (the block
     * reverts to a vanilla note block) and {@link BombBlocks} identity (the block starts behaving like a
     * playable note block).
     *
     * <p>State-based recognition cannot recover from this: once the instrument drifts, {@link
     * BombBlocks#bombIdOf(Block)} no longer matches, so a purely state-keyed heal can never re-arm and a
     * multi-tick source (a piston that keeps re-deriving over several ticks) settles drifted. So this
     * handler registers the block in {@link PlacedBombIndex} the moment it sees it still claimed — which
     * always happens before the first drift, since the event precedes the re-derivation — and then heals
     * <em>by location</em> on every later event. The next-tick heal re-asserts the claimed state with
     * physics suppressed; when the location no longer holds a note block (broken or detonated) it prunes
     * the index instead, so a spent bomb is never resurrected and stale entries do not accumulate.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPhysics(BlockPhysicsEvent event) {
        Block block = event.getBlock();
        UUID world = block.getWorld().getUID();
        int x = block.getX();
        int y = block.getY();
        int z = block.getZ();
        PlacedBombIndex index = plugin.placedBombIndex();
        if (block.getType() != Material.NOTE_BLOCK) {
            index.remove(world, x, y, z); // a tracked location that is no longer a note block: prune
            return;
        }
        Optional<String> byState = BombBlocks.bombIdOf(block);
        if (byState.isPresent()) {
            index.put(world, x, y, z, byState.get()); // (re)register while still claimed
        } else if (!index.contains(world, x, y, z)) {
            return; // an ordinary note block we have never seen claimed — not a bomb
        }
        event.setCancelled(true); // harmless on this version; the next-tick heal is what holds the state
        String id = byState.orElseGet(() -> index.get(world, x, y, z).orElseThrow());
        Location loc = block.getLocation();
        plugin.getServer().getScheduler().runTask(plugin, () -> heal(loc, id));
    }

    /** Re-asserts {@code id}'s claimed state at {@code loc}, or prunes the index if the block is gone. */
    private void heal(Location loc, String id) {
        Block block = loc.getBlock();
        UUID world = loc.getWorld().getUID();
        if (block.getType() != Material.NOTE_BLOCK) {
            plugin.placedBombIndex().remove(world, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
            return;
        }
        if (BombBlocks.bombIdOf(block).isEmpty()) {
            block.setBlockData(BombBlocks.blockDataFor(id), false); // heal the drift, no physics
        }
    }

    /** A bomb block is silent — never plays the note-block ping (even mid-drift, via the index). */
    @EventHandler(ignoreCancelled = true)
    public void onNotePlay(NotePlayEvent event) {
        if (bombIdAt(event.getBlock()).isPresent()) {
            event.setCancelled(true);
        }
    }

    /** Breaking a bomb block returns the bomb item, not a vanilla note block. */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Optional<String> bombId = bombIdAt(block);
        if (bombId.isEmpty()) {
            return;
        }
        String id = bombId.get();
        event.setDropItems(false); // never drop the underlying note block
        plugin.placedBombIndex().remove(
                block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
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

    /**
     * The bomb id at {@code block}: its claimed blockstate if it still matches, else the {@link
     * PlacedBombIndex} entry for its location (a drifted-but-tracked bomb). Empty for anything else.
     */
    private Optional<String> bombIdAt(Block block) {
        Optional<String> byState = BombBlocks.bombIdOf(block);
        if (byState.isPresent()) {
            return byState;
        }
        if (block.getType() != Material.NOTE_BLOCK) {
            return Optional.empty();
        }
        return plugin.placedBombIndex()
                .get(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }
}
