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

    // ---- runtime (server-dependent) ----------------------------------------------------------

    /**
     * The key identifying a live {@link org.bukkit.block.Block}. Mirrors {@code BombBlocks}'s
     * pure-vs-runtime split: the {@link #format()}/{@link #parse(String)}/record members above are pure
     * data and unit-tested directly, while this adapter reaches into a running server (a {@code Block}
     * only exists with one loaded), so it is verified at the runtime gate rather than in JUnit. Bukkit
     * types are named fully-qualified so this section adds no imports that would pollute the pure part.
     */
    public static BlockKey from(org.bukkit.block.Block block) {
        return new BlockKey(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
    }

    /**
     * The key identifying a {@link org.bukkit.Location}, using its block coordinates. Runtime-only for
     * the same reason as {@link #from(org.bukkit.block.Block)}. A location detached from its world is a
     * broken caller, so a null world fails fast rather than fabricating a key.
     */
    public static BlockKey from(org.bukkit.Location loc) {
        java.util.Objects.requireNonNull(loc.getWorld(), "location world");
        return new BlockKey(loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }
}
