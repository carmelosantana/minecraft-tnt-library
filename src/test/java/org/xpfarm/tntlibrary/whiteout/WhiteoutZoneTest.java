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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Pins the pure spherical membership contract: {@link WhiteoutZone#covers} compares squared distance to
 * squared radius, so the closed ball (boundary included) is exactly the set the runtime keeps after the
 * bounded {@code getNearbyLivingEntities} box is filtered to a sphere.
 */
final class WhiteoutZoneTest {

    @Test
    void centerIsCovered() {
        WhiteoutZone zone = new WhiteoutZone(0, 0, 0, 10);
        assertTrue(zone.covers(0, 0, 0));
    }

    @Test
    void pointExactlyOnBoundaryIsCovered() {
        WhiteoutZone zone = new WhiteoutZone(0, 0, 0, 10);
        assertTrue(zone.covers(10, 0, 0));
        assertTrue(zone.covers(0, -10, 0));
        assertTrue(zone.covers(0, 0, 10));
    }

    @Test
    void pointJustOutsideBoundaryIsNotCovered() {
        WhiteoutZone zone = new WhiteoutZone(0, 0, 0, 10);
        assertFalse(zone.covers(10.0001, 0, 0));
    }

    @Test
    void diagonalInsideBallIsCovered() {
        WhiteoutZone zone = new WhiteoutZone(0, 0, 0, 5);
        assertTrue(zone.covers(3, 4, 0)); // distance 5, on boundary
        assertTrue(zone.covers(1, 2, 2)); // distance 3, inside
    }

    @Test
    void diagonalOutsideBallIsNotCovered() {
        WhiteoutZone zone = new WhiteoutZone(0, 0, 0, 5);
        assertFalse(zone.covers(3, 4, 1)); // sqrt(26) > 5
    }

    @Test
    void negativeCenterMeasuresFromThatCenter() {
        WhiteoutZone zone = new WhiteoutZone(-100, -50, -25, 10);
        assertTrue(zone.covers(-100, -50, -25));
        assertTrue(zone.covers(-90, -50, -25));
        assertFalse(zone.covers(-89.9999, -50, -25));
    }

    @Test
    void zeroRadiusCoversOnlyTheExactCenter() {
        WhiteoutZone zone = new WhiteoutZone(7, 8, 9, 0);
        assertTrue(zone.covers(7, 8, 9));
        assertFalse(zone.covers(7.0001, 8, 9));
    }
}
