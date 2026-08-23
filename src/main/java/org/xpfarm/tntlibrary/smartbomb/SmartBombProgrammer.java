/*
 * TNTLibrary - a custom-TNT framework and a set of creative, premium explosives.
 * Copyright (C) 2026 Carmelo Santana
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * See the LICENSE file at the project root for the full license text.
 */
package org.xpfarm.tntlibrary.smartbomb;

import java.util.Objects;
import org.bukkit.block.Block;

/**
 * The single DRY write path for a placed Smart Bomb's programming.
 *
 * <p>The contract mandates that all three programming tiers — the chest-GUI, the sign/command entry,
 * and placement seeding (Tasks 6–7), plus the watcher's reads (Task 5) — go through one handler rather
 * than each touching the {@link SmartBombStoreService} directly. Centralising the {@link Block} →
 * {@link BlockKey} translation here means the key derivation and the store interaction can never drift
 * between call sites. Server-dependent (translates a live {@code Block}); verified at the runtime gate.
 */
public final class SmartBombProgrammer {

    private final SmartBombStoreService store;

    public SmartBombProgrammer(SmartBombStoreService store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    /** Persist the player's programming for this placed Smart Bomb. All three UI tiers call this. */
    public void apply(Block block, SmartBombParams params) {
        store.put(BlockKey.from(block), params);
    }

    /** The block's current programming, or {@code fallback} (the seeded defaults) if none stored. */
    public SmartBombParams current(Block block, SmartBombParams fallback) {
        return store.get(BlockKey.from(block)).orElse(fallback);
    }
}
