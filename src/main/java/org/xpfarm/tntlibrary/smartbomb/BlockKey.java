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
import java.util.Optional;

/**
 * Pure, server-free identity for one block: a world name plus integer coordinates.
 *
 * <p>Deliberately holds no {@code org.bukkit.Location} so the whole Smart Bomb store is
 * headless-testable; the runtime {@code Block}/{@code Location} adapter is a separate task. Because a
 * {@code BlockKey} is the persistence key, its {@link #format()} string doubles as the on-disk key, so
 * the format is stable and its inverse {@link #parse(String)} is total (never throws) — a hand-edited
 * or version-skewed line yields {@link Optional#empty()} instead of crashing a load.
 */
public record BlockKey(String world, int x, int y, int z) {

    /** The three coordinate fields that always trail the world name in a formatted key. */
    private static final int COORD_FIELDS = 3;

    /** Rejects a null or empty world so a formatted key always has a non-blank leading segment. */
    public BlockKey {
        Objects.requireNonNull(world, "world");
        if (world.isEmpty()) {
            throw new IllegalArgumentException("world must not be empty");
        }
    }

    /**
     * Renders the canonical {@code "world,x,y,z"} key: world name verbatim, coordinates as decimal
     * integers (negatives keep their {@code -}). Inverse of {@link #parse(String)}.
     */
    public String format() {
        return world + "," + x + "," + y + "," + z;
    }

    /**
     * Parses a {@link #format()} string back into a key, splitting from the RIGHT so a world name that
     * itself contains a comma still round-trips: the last three comma-separated fields are the
     * coordinates and everything before them is the world name. Fewer than four fields, non-integer
     * coordinates, or {@code null} input all yield {@link Optional#empty()} rather than throwing.
     */
    public static Optional<BlockKey> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        // Split from the right into exactly the three trailing coordinates plus a world remainder.
        int zComma = raw.lastIndexOf(',');
        if (zComma < 0) {
            return Optional.empty();
        }
        int yComma = raw.lastIndexOf(',', zComma - 1);
        if (yComma < 0) {
            return Optional.empty();
        }
        int xComma = raw.lastIndexOf(',', yComma - 1);
        if (xComma < 0) {
            return Optional.empty();
        }
        String world = raw.substring(0, xComma);
        if (world.isEmpty()) {
            return Optional.empty();
        }
        try {
            int x = Integer.parseInt(raw.substring(xComma + 1, yComma));
            int y = Integer.parseInt(raw.substring(yComma + 1, zComma));
            int z = Integer.parseInt(raw.substring(zComma + 1));
            return Optional.of(new BlockKey(world, x, y, z));
        } catch (NumberFormatException notAnInt) {
            return Optional.empty();
        }
    }
}
