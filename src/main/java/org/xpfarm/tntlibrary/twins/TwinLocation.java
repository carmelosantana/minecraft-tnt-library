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
import java.util.UUID;

/**
 * An immutable block position for "The Twins" bomb, decoupled from Bukkit so the index, pairing, and
 * beam logic stay headless-testable.
 *
 * <p>{@code worldId} is the world's {@link UUID} rather than a {@link org.bukkit.World} handle: the
 * runtime adapter converts {@code Block}&harr;{@code TwinLocation} at the edge, and everything
 * inward works on these plain values with no running server. The three coordinates are block
 * coordinates (integers).
 *
 * @param worldId the id of the world this position belongs to; never {@code null}
 * @param x the block x coordinate
 * @param y the block y coordinate
 * @param z the block z coordinate
 */
public record TwinLocation(UUID worldId, int x, int y, int z) {

    /** Validates the non-null world id. */
    public TwinLocation {
        Objects.requireNonNull(worldId, "worldId");
    }

    /** Whether {@code other} is in the same world as this position (by {@link #worldId()}). */
    public boolean sameWorld(TwinLocation other) {
        return worldId.equals(other.worldId);
    }

    /**
     * Straight-line (Euclidean) distance to {@code other}, treating both as block positions: the
     * square root of the summed squared integer coordinate deltas as doubles. World id is not
     * consulted — callers gate cross-world pairs with {@link #sameWorld(TwinLocation)} first.
     */
    public double distanceTo(TwinLocation other) {
        double dx = (double) x - other.x;
        double dy = (double) y - other.y;
        double dz = (double) z - other.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
