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

import java.util.Objects;

/**
 * A placed Twin block: where it sits ({@link TwinLocation}) and which variant it is
 * ({@link TwinColor}). Immutable and Bukkit-free, so the pairing and beam logic can reason over a
 * world's placed Twins headlessly.
 *
 * @param location the block position of this Twin; never {@code null}
 * @param color the Twin's variant colour; never {@code null}
 */
public record PlacedTwin(TwinLocation location, TwinColor color) {

    /** Validates the non-null components. */
    public PlacedTwin {
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(color, "color");
    }
}
