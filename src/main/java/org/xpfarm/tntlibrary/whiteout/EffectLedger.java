/*
 * TNTLibrary - a custom-TNT framework and a set of creative, premium explosives.
 * Copyright (C) 2026 Carmelo Santana
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * See the LICENSE file at the project root for the full license text.
 */
package org.xpfarm.tntlibrary.whiteout;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * The pure, headless bookkeeping for the White Out's no-leaked-state gate: which entities this
 * detonation debuffed (blindness/slowness/freeze), keyed by {@link UUID}, so every one can be cleared
 * on abort, cancel, or reload. Simpler than the G-Bomb's {@code GravityLedger} — there is no prior
 * value to remember, only membership — but it shares the idempotent {@link #drain()} discipline.
 *
 * <h2>Threading</h2>
 *
 * <p>Main-thread-only, mirroring {@code GravityLedger} and {@code SmartBombWatcher}: every caller is a
 * Bukkit event or scheduled task on the server main thread, so the backing set holds no synchronisation.
 */
public final class EffectLedger {

    /** Ids of every entity this detonation debuffed. Main-thread only. */
    private final Set<UUID> affected = new HashSet<>();

    /**
     * Records that {@code id} was debuffed.
     *
     * @return {@code true} when newly recorded, {@code false} when already tracked (no change)
     */
    public boolean record(UUID id) {
        return affected.add(id);
    }

    /** Whether {@code id} is currently tracked. */
    public boolean contains(UUID id) {
        return affected.contains(id);
    }

    /** How many entities are currently tracked. */
    public int size() {
        return affected.size();
    }

    /**
     * Removes {@code id}. Idempotent: a second {@code forget} of the same id returns {@code false}.
     *
     * @return {@code true} when {@code id} was tracked (and is now removed), {@code false} otherwise
     */
    public boolean forget(UUID id) {
        return affected.remove(id);
    }

    /**
     * Returns an unmodifiable snapshot of every tracked id and clears the ledger. Idempotent: a second
     * call returns an empty set and does not throw.
     */
    public Set<UUID> drain() {
        Set<UUID> snapshot = Collections.unmodifiableSet(new HashSet<>(affected));
        affected.clear();
        return snapshot;
    }
}
