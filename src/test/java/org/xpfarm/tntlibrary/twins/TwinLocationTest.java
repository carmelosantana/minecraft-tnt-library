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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Pins the pure {@link TwinLocation} invariants — Euclidean {@code distanceTo} over integer block
 * coords and the world-id equality of {@code sameWorld}. Runs headless (no {@link org.bukkit.Bukkit}
 * calls).
 */
final class TwinLocationTest {

    private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final double EPS = 1e-9;

    @Test
    void distanceAlongOneAxisIsTheDelta() {
        TwinLocation origin = new TwinLocation(WORLD, 0, 0, 0);
        assertEquals(3.0, origin.distanceTo(new TwinLocation(WORLD, 3, 0, 0)), EPS);
    }

    @Test
    void distanceToSelfIsZero() {
        TwinLocation origin = new TwinLocation(WORLD, 0, 0, 0);
        assertEquals(0.0, origin.distanceTo(origin), EPS);
    }

    @Test
    void threeFourZeroGivesFive() {
        TwinLocation origin = new TwinLocation(WORLD, 0, 0, 0);
        assertEquals(5.0, origin.distanceTo(new TwinLocation(WORLD, 3, 4, 0)), EPS);
    }

    @Test
    void threeDimensionalDiagonalIsSqrtThree() {
        TwinLocation a = new TwinLocation(WORLD, 1, 1, 1);
        assertEquals(Math.sqrt(3), a.distanceTo(new TwinLocation(WORLD, 0, 0, 0)), EPS);
    }

    @Test
    void distanceIgnoresWorldId() {
        // distanceTo is a straight-line over coords; the caller gates cross-world with sameWorld.
        UUID other = UUID.fromString("00000000-0000-0000-0000-000000000002");
        TwinLocation a = new TwinLocation(WORLD, 0, 0, 0);
        assertEquals(3.0, a.distanceTo(new TwinLocation(other, 3, 0, 0)), EPS);
    }

    @Test
    void sameWorldTrueForMatchingWorldId() {
        TwinLocation a = new TwinLocation(WORLD, 1, 2, 3);
        TwinLocation b = new TwinLocation(WORLD, 9, 9, 9);
        assertTrue(a.sameWorld(b));
    }

    @Test
    void sameWorldFalseForDifferentWorldId() {
        UUID other = UUID.fromString("00000000-0000-0000-0000-000000000002");
        TwinLocation a = new TwinLocation(WORLD, 1, 2, 3);
        TwinLocation b = new TwinLocation(other, 1, 2, 3);
        assertFalse(a.sameWorld(b));
    }

    @Test
    void worldIdIsRequired() {
        assertThrows(NullPointerException.class, () -> new TwinLocation(null, 0, 0, 0));
    }

    @Test
    void equalityIsValueBased() {
        assertEquals(new TwinLocation(WORLD, 1, 2, 3), new TwinLocation(WORLD, 1, 2, 3));
    }
}
