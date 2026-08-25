/*
 * TNTLibrary - a custom-TNT framework and a set of creative, premium explosives.
 * Copyright (C) 2026 Carmelo Santana
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * See the LICENSE file at the project root for the full license text.
 */
package org.xpfarm.tntlibrary.gbomb;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The single source of truth for gravity-restore safety — the #1 review gate of the G-Bomb.
 *
 * <p>The G-Bomb disables entity gravity ({@code setGravity(false)}) for the launch→hang→slam sequence.
 * Because the {@code NoGravity} NBT flag persists across chunk/entity unload, every entity we disabled
 * <em>must</em> have its gravity restored on <em>every</em> path: normal slam, task cancel, plugin
 * disable, chunk/entity unload, and server stop. This ledger is the pure, headless bookkeeping that
 * guarantees it: it records which entities (keyed by {@link UUID}) had gravity disabled and their
 * <em>prior</em> gravity flag, and hands them back for restoration. The runtime edge resolves the UUID
 * to a live entity and calls {@code setGravity(prior)} — no Bukkit import lives here.
 *
 * <h2>Safety invariants</h2>
 *
 * <ul>
 *   <li><b>First record wins.</b> A re-launch of an already-tracked entity is a no-op that preserves
 *       the original prior gravity value, so the true "before we touched it" flag can never be lost.
 *   <li><b>Every id is restorable.</b> Anything added is retrievable exactly once via {@link #forget}
 *       (single-entity restore) or {@link #drain} (batch restore).
 *   <li><b>Idempotent drain.</b> {@link #drain} clears the ledger and a second call returns an empty,
 *       non-throwing map. This is what makes a normal slam-restore followed by a disable-restore safe:
 *       it can never double-toggle gravity or throw.
 * </ul>
 *
 * <h2>Threading</h2>
 *
 * <p>Main-thread-only, mirroring {@code smartbomb/SmartBombWatcher} and {@code BombFuse}: every caller
 * is a Bukkit event or scheduled task on the server main thread, so the backing map holds no internal
 * synchronisation. Callers must not touch a ledger off the main thread.
 */
public final class GravityLedger {

    /** id → prior gravity flag for every entity we disabled gravity on. Main-thread only. */
    private final Map<UUID, Boolean> tracked = new HashMap<>();

    /**
     * Records that {@code id} had gravity disabled, remembering its {@code priorGravity} flag.
     *
     * <p>First record wins: a second record of an already-tracked id is a no-op that returns
     * {@code false} and preserves the originally recorded {@code priorGravity}, so a re-launch can
     * never lose the true prior value.
     *
     * @return {@code true} when newly recorded, {@code false} when already tracked (no change made)
     */
    public boolean record(UUID id, boolean priorGravity) {
        return tracked.putIfAbsent(id, priorGravity) == null;
    }

    /** Returns whether {@code id} is currently tracked. */
    public boolean contains(UUID id) {
        return tracked.containsKey(id);
    }

    /** Returns how many entities are currently tracked. */
    public int size() {
        return tracked.size();
    }

    /**
     * Removes {@code id} and returns its recorded prior gravity flag for restoration, or
     * {@link Optional#empty()} when it was not tracked. Idempotent: a second {@code forget} of the same
     * id returns empty.
     */
    public Optional<Boolean> forget(UUID id) {
        return Optional.ofNullable(tracked.remove(id));
    }

    /**
     * Returns an unmodifiable snapshot of every tracked {@code id → priorGravity} and clears the
     * ledger.
     *
     * <p>Idempotent: a second call returns an empty map and does not throw — the property that makes a
     * slam-restore followed by a disable-restore safe against double-toggling.
     */
    public Map<UUID, Boolean> drain() {
        Map<UUID, Boolean> snapshot = Collections.unmodifiableMap(new HashMap<>(tracked));
        tracked.clear();
        return snapshot;
    }
}
