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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Pins the pure {@link TwinsBeam#samplePoints} contract — block-centre endpoints, ~1-block spacing,
 * inclusive endpoint count, and A&rarr;B order. Runs headless (no {@link org.bukkit.Bukkit} calls).
 */
final class TwinsBeamTest {

    private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final double EPS = 1e-9;

    private static TwinLocation at(int x, int y, int z) {
        return new TwinLocation(WORLD, x, y, z);
    }

    @Test
    void firstAndLastAreExactBlockCentres() {
        List<Vec3> points = TwinsBeam.samplePoints(at(0, 0, 0), at(4, 0, 0));

        Vec3 first = points.get(0);
        Vec3 last = points.get(points.size() - 1);
        // Endpoints are computed directly, so assert EXACT equality.
        assertEquals(new Vec3(0.5, 0.5, 0.5), first);
        assertEquals(new Vec3(4.5, 0.5, 0.5), last);
    }

    @Test
    void axisAlignedLengthFourGivesFivePointsAtUnitSpacing() {
        List<Vec3> points = TwinsBeam.samplePoints(at(0, 0, 0), at(4, 0, 0));

        assertEquals(5, points.size(), "d=4, steps=4 -> steps+1 points");
        for (int i = 0; i < points.size(); i++) {
            Vec3 p = points.get(i);
            assertEquals(0.5 + i, p.x(), EPS, "x at index " + i);
            assertEquals(0.5, p.y(), EPS, "y constant");
            assertEquals(0.5, p.z(), EPS, "z constant");
        }
        for (int i = 1; i < points.size(); i++) {
            assertEquals(1.0, points.get(i).x() - points.get(i - 1).x(), EPS,
                    "consecutive x-spacing at index " + i);
        }
    }

    @Test
    void adjacentBlocksGiveTwoEndpointsOnly() {
        List<Vec3> points = TwinsBeam.samplePoints(at(0, 0, 0), at(1, 0, 0));

        assertEquals(2, points.size(), "d=1, steps=1 -> 2 points");
        assertEquals(new Vec3(0.5, 0.5, 0.5), points.get(0));
        assertEquals(new Vec3(1.5, 0.5, 0.5), points.get(1));
    }

    @Test
    void sameBlockGivesExactlyOneCentrePoint() {
        List<Vec3> points = TwinsBeam.samplePoints(at(0, 0, 0), at(0, 0, 0));

        assertEquals(1, points.size(), "d=0 -> a single centre point");
        assertEquals(new Vec3(0.5, 0.5, 0.5), points.get(0));
    }

    @Test
    void longerDiagonalHasFloorDistPlusOnePointsWithBoundedSpacing() {
        TwinLocation a = at(0, 0, 0);
        TwinLocation b = at(10, 10, 0);
        double d = a.distanceTo(b);
        int steps = (int) Math.floor(d);

        List<Vec3> points = TwinsBeam.samplePoints(a, b);

        assertEquals(steps + 1, points.size(), "count == floor(dist)+1");
        // Endpoints exact.
        assertEquals(new Vec3(0.5, 0.5, 0.5), points.get(0));
        assertEquals(new Vec3(10.5, 10.5, 0.5), points.get(points.size() - 1));

        double expectedSpacing = d / steps;
        for (int i = 1; i < points.size(); i++) {
            Vec3 prev = points.get(i - 1);
            Vec3 cur = points.get(i);
            double dx = cur.x() - prev.x();
            double dy = cur.y() - prev.y();
            double dz = cur.z() - prev.z();
            double spacing = Math.sqrt(dx * dx + dy * dy + dz * dz);
            assertEquals(expectedSpacing, spacing, 1e-9, "spacing at index " + i);
            assertTrue(spacing <= 1.5, "spacing stays near 1 block at index " + i);
        }
    }

    @Test
    void interiorPointsLieOnTheSegmentInAToBOrder() {
        TwinLocation a = at(2, 3, 4);
        TwinLocation b = at(6, 3, 4);
        List<Vec3> points = TwinsBeam.samplePoints(a, b);

        // First is A's centre (not B's) — order is preserved.
        assertEquals(new Vec3(2.5, 3.5, 4.5), points.get(0));
        assertEquals(new Vec3(6.5, 3.5, 4.5), points.get(points.size() - 1));
        // Interior point at t = 2/4 = 0.5 -> x = 4.5 (small delta, not computed directly).
        assertEquals(4.5, points.get(2).x(), 1e-9);
    }
}
