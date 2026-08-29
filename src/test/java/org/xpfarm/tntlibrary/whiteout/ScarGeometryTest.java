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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Pins the pure scar geometry: {@link ScarGeometry#columnsCenterOut} enumerates exactly the integer
 * columns inside the radius-disk, ordered center-out so the runtime can sweep the concrete outward ring
 * by ring, and {@link ScarGeometry#ringCount} counts the distinct rings. Disk cardinalities are exact
 * integers, so exact assertions are used.
 */
final class ScarGeometryTest {

    @Test
    void radiusZeroIsJustTheCenterColumn() {
        int[][] cols = ScarGeometry.columnsCenterOut(0);
        assertEquals(1, cols.length);
        assertArrayEquals(new int[] {0, 0}, cols[0]);
    }

    @Test
    void firstColumnIsAlwaysTheCenter() {
        assertArrayEquals(new int[] {0, 0}, ScarGeometry.columnsCenterOut(5)[0]);
    }

    @Test
    void radiusOneHasFiveColumns() {
        // dx^2+dz^2 <= 1 -> (0,0),(±1,0),(0,±1)
        assertEquals(5, ScarGeometry.columnsCenterOut(1).length);
    }

    @Test
    void radiusTwoHasThirteenColumns() {
        // dx^2+dz^2 <= 4 -> 1 + 4(dist 1) + 4(dist 2) + 4(dist 4) = 13
        assertEquals(13, ScarGeometry.columnsCenterOut(2).length);
    }

    @Test
    void everyColumnLiesWithinTheRadiusDisk() {
        int radius = 8;
        for (int[] col : ScarGeometry.columnsCenterOut(radius)) {
            int dx = col[0];
            int dz = col[1];
            assertTrue(dx * dx + dz * dz <= radius * radius,
                    "column (" + dx + "," + dz + ") outside radius " + radius);
        }
    }

    @Test
    void columnsAreOrderedNonDecreasingByRing() {
        int[][] cols = ScarGeometry.columnsCenterOut(8);
        int prev = -1;
        for (int[] col : cols) {
            int ring = col[0] * col[0] + col[1] * col[1];
            assertTrue(ring >= prev, "ordering must be non-decreasing by squared distance");
            prev = ring;
        }
    }

    @Test
    void ringCountMatchesDistinctSquaredDistances() {
        assertEquals(1, ScarGeometry.ringCount(0)); // {0}
        assertEquals(2, ScarGeometry.ringCount(1)); // {0,1}
        assertEquals(4, ScarGeometry.ringCount(2)); // {0,1,2,4}
    }
}
