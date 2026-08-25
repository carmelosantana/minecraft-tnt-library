/*
 * TNTLibrary - a custom-TNT framework and a set of creative, premium explosives.
 * Copyright (C) 2026 Carmelo Santana
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * See the LICENSE file at the project root for the full license text.
 */
package org.xpfarm.tntlibrary.block;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The in-memory, per-world register of <em>where</em> placed bomb blocks are, keyed by block position
 * and holding each block's placed id.
 *
 * <h2>Why a location index exists</h2>
 *
 * <p>A placed bomb is a vanilla {@code note_block} in a claimed state ({@code instrument=pling}, a
 * distinct {@code note}, {@code powered=false}). On Paper 26.1.2 a note block re-derives its {@code
 * instrument} from the block beneath it inside {@code Block.updateShape()} — a path that is not gated
 * behind the cancellable {@code BlockPhysicsEvent} — so a neighbor update can drift the block off {@code
 * pling}. Once drifted, {@link BombBlocks#bombIdOf(org.bukkit.block.Block)} no longer recognizes it,
 * because that recognition is <em>state-based</em> and the state is the very thing that drifted. This
 * index recognizes a bomb by its <em>location</em> instead, so the guard can heal a block back to its
 * claimed state even after the drift has already happened.
 *
 * <p>The guard populates this lazily: the first physics event of any drift fires while the block is
 * still {@code pling} (the event precedes the re-derivation), so the guard registers it there and heals
 * by location on every subsequent event. Lazy registration means no placement wiring is required and no
 * restart bookkeeping is needed — a non-drifted bomb persists on disk as {@code pling}, so its first
 * post-restart neighbor update re-registers it. Entries are pruned when a location is found to no longer
 * hold a note block (broken or detonated).
 *
 * <p>Backed by a {@code Map<UUID, Map<Position, String>>}: an outer map keyed by world id whose inner
 * maps are keyed by block {@link Position}. That keeps {@link #get} and {@link #remove} O(1), isolates
 * worlds, and lets the same coordinates in two worlds be distinct entries. A location holds at most one
 * id; re-registering overwrites.
 *
 * <p>Not thread-safe: like {@link org.xpfarm.tntlibrary.twins.PlacedTwinIndex}, all access is on the
 * server main thread (physics and break events), so no synchronization is needed. Pure logic — no
 * {@link org.bukkit.Bukkit} calls — so it is fully unit-testable headless.
 */
public final class PlacedBombIndex {

    /** An immutable block position (world id + integer block coordinates). */
    public record Position(int x, int y, int z) {}

    private final Map<UUID, Map<Position, String>> byWorld = new HashMap<>();

    /**
     * Records (or overwrites) the placed id at {@code (worldId, x, y, z)}.
     *
     * @throws NullPointerException if {@code worldId} or {@code bombId} is {@code null}
     */
    public void put(UUID worldId, int x, int y, int z, String bombId) {
        Objects.requireNonNull(worldId, "worldId");
        Objects.requireNonNull(bombId, "bombId");
        byWorld.computeIfAbsent(worldId, id -> new HashMap<>()).put(new Position(x, y, z), bombId);
    }

    /** The placed id recorded at {@code (worldId, x, y, z)}, or empty if none. */
    public Optional<String> get(UUID worldId, int x, int y, int z) {
        if (worldId == null) {
            return Optional.empty();
        }
        Map<Position, String> inWorld = byWorld.get(worldId);
        if (inWorld == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(inWorld.get(new Position(x, y, z)));
    }

    /** Removes any entry at {@code (worldId, x, y, z)}; a no-op if none is recorded. */
    public void remove(UUID worldId, int x, int y, int z) {
        if (worldId == null) {
            return;
        }
        Map<Position, String> inWorld = byWorld.get(worldId);
        if (inWorld == null) {
            return;
        }
        inWorld.remove(new Position(x, y, z));
        if (inWorld.isEmpty()) {
            byWorld.remove(worldId);
        }
    }

    /** Whether an entry is recorded at {@code (worldId, x, y, z)}. */
    public boolean contains(UUID worldId, int x, int y, int z) {
        return get(worldId, x, y, z).isPresent();
    }

    /** Total entries recorded across all worlds. */
    public int size() {
        int total = 0;
        for (Map<Position, String> inWorld : byWorld.values()) {
            total += inWorld.size();
        }
        return total;
    }
}
