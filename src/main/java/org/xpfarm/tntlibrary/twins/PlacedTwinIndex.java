/*
 * TNTLibrary - a custom-TNT framework and a set of creative, premium explosives.
 * Copyright (C) 2026 Carmelo Santana
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * See the LICENSE file at the project root for the full license text.
 */
package org.xpfarm.tntlibrary.twins;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * The in-memory, per-world register of placed Twins the runtime maintains — add on place, remove on
 * break/detonate. Bounded partner lookup reads a small per-world snapshot from here instead of
 * scanning millions of blocks.
 *
 * <p>Backed by a {@code Map<UUID, Map<TwinLocation, PlacedTwin>>}: an outer map keyed by world id
 * whose inner maps are keyed by full {@link TwinLocation}. That keeps {@link #contains} and
 * {@link #remove} O(1), isolates worlds from one another, and lets the same block coordinates in two
 * different worlds be distinct entries. A location holds at most one Twin; re-adding overwrites.
 *
 * <p>Not thread-safe: like {@code BombFuse}, all access is on the server main thread (placement and
 * break events, detonation), so no synchronization is needed. Pure logic — no {@link
 * org.bukkit.Bukkit} calls — so it is fully unit-testable headless.
 */
public final class PlacedTwinIndex {

    private final Map<UUID, Map<TwinLocation, PlacedTwin>> byWorld = new HashMap<>();

    /**
     * Records {@code twin}, or overwrites whatever Twin was recorded at its location. A location
     * holds at most one Twin, so re-adding at an occupied location replaces it (size unchanged).
     *
     * @throws NullPointerException if {@code twin} is {@code null}
     */
    public void add(PlacedTwin twin) {
        Objects.requireNonNull(twin, "twin");
        TwinLocation location = twin.location();
        byWorld.computeIfAbsent(location.worldId(), id -> new HashMap<>()).put(location, twin);
    }

    /** Removes any Twin recorded at {@code location}; a no-op if none is. */
    public void remove(TwinLocation location) {
        if (location == null) {
            return;
        }
        Map<TwinLocation, PlacedTwin> inWorld = byWorld.get(location.worldId());
        if (inWorld == null) {
            return;
        }
        inWorld.remove(location);
        if (inWorld.isEmpty()) {
            byWorld.remove(location.worldId());
        }
    }

    /** Whether a Twin is recorded at {@code location}. */
    public boolean contains(TwinLocation location) {
        if (location == null) {
            return false;
        }
        Map<TwinLocation, PlacedTwin> inWorld = byWorld.get(location.worldId());
        return inWorld != null && inWorld.containsKey(location);
    }

    /**
     * An immutable snapshot of every Twin recorded in {@code worldId} (empty if none). The returned
     * collection is a copy: it is unmodifiable and does not reflect later mutations of the index, so
     * a caller may iterate it while the index changes.
     */
    public Collection<PlacedTwin> inWorld(UUID worldId) {
        Map<TwinLocation, PlacedTwin> inWorld = byWorld.get(worldId);
        if (inWorld == null || inWorld.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableCollection(new ArrayList<>(inWorld.values()));
    }

    /** Total Twins recorded across all worlds. */
    public int size() {
        int total = 0;
        for (Map<TwinLocation, PlacedTwin> inWorld : byWorld.values()) {
            total += inWorld.size();
        }
        return total;
    }
}
