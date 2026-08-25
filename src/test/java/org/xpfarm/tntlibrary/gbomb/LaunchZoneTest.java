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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Pins the pure spherical membership contract: {@link LaunchZone#covers} compares squared distance to
 * squared radius, so the closed ball (boundary included) is exactly the set the runtime keeps after
 * the bounded {@code getNearbyLivingEntities} box is filtered to a sphere.
 */
final class LaunchZoneTest {

    @Test
    void centerIsCovered() {
        LaunchZone zone = new LaunchZone(0, 0, 0, 10);
        assertTrue(zone.covers(0, 0, 0));
    }

    @Test
    void pointExactlyOnBoundaryIsCovered() {
        LaunchZone zone = new LaunchZone(0, 0, 0, 10);
        assertTrue(zone.covers(10, 0, 0));
        assertTrue(zone.covers(0, -10, 0));
        assertTrue(zone.covers(0, 0, 10));
    }

    @Test
    void pointJustOutsideBoundaryIsNotCovered() {
        LaunchZone zone = new LaunchZone(0, 0, 0, 10);
        assertFalse(zone.covers(10.0001, 0, 0));
    }

    @Test
    void diagonalInsideBallIsCovered() {
        LaunchZone zone = new LaunchZone(0, 0, 0, 5);
        // 3-4-0 -> distance 5, exactly on the boundary.
        assertTrue(zone.covers(3, 4, 0));
        // 1-2-2 -> distance 3, comfortably inside.
        assertTrue(zone.covers(1, 2, 2));
    }

    @Test
    void diagonalOutsideBallIsNotCovered() {
        LaunchZone zone = new LaunchZone(0, 0, 0, 5);
        // 3-4-1 -> distance sqrt(26) > 5.
        assertFalse(zone.covers(3, 4, 1));
    }

    @Test
    void negativeCenterMeasuresFromThatCenter() {
        LaunchZone zone = new LaunchZone(-100, -50, -25, 10);
        assertTrue(zone.covers(-100, -50, -25));
        assertTrue(zone.covers(-90, -50, -25)); // dx = 10, on boundary
        assertFalse(zone.covers(-89.9999, -50, -25)); // dx = 10.0001, outside
        assertTrue(zone.covers(-94, -46, -25)); // 3-4-0 diagonal, on boundary
    }

    @Test
    void zeroRadiusCoversOnlyTheExactCenter() {
        LaunchZone zone = new LaunchZone(7, 8, 9, 0);
        assertTrue(zone.covers(7, 8, 9));
        assertFalse(zone.covers(7.0001, 8, 9));
    }
}
