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

import java.util.Optional;

/**
 * Keyed store of per-block Smart Bomb params: {@code blockLocation -> params}.
 *
 * <p>Keyed by a pure {@link BlockKey} rather than a {@code Location} so the store — and every
 * implementation — is headless-testable. Lookups return {@link Optional} so an absent key is expressed
 * in the type rather than via {@code null}.
 */
public interface SmartBombStore {

    /** Returns the params stored for {@code key}, or {@link Optional#empty()} if none. */
    Optional<SmartBombParams> get(BlockKey key);

    /** Inserts or replaces the params stored for {@code key}. */
    void put(BlockKey key, SmartBombParams params);

    /** Removes any params stored for {@code key}; a no-op if the key is absent. */
    void remove(BlockKey key);

    /** Returns whether {@code key} currently has stored params. */
    boolean contains(BlockKey key);

    /** Returns the number of stored entries. */
    int size();
}
