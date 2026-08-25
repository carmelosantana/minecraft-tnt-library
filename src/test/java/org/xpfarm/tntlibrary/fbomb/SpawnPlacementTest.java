/*
 * TNTLibrary - a custom-TNT framework and a set of creative, premium explosives.
 * Copyright (C) 2026 Carmelo Santana
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * See the LICENSE file at the project root for the full license text.
 */
package org.xpfarm.tntlibrary.fbomb;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.bukkit.Location;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link SpawnPlacement}. Headless: every {@link Location} is built with a null
 * world, since this class's math never touches the world.
 */
final class SpawnPlacementTest {

    private static final double EPS = 1e-9;

    @Test
    void offsetYawZeroFacesPositiveZ() {
        Location out = SpawnPlacement.offset(new Location(null, 0, 64, 0, 0f, 0f), 0, 10, 3);
        assertEquals(0.0, out.getX(), EPS);
        assertEquals(67.0, out.getY(), EPS);
        assertEquals(10.0, out.getZ(), EPS);
    }

    @Test
    void offsetYawNinetyFacesNegativeX() {
        Location out = SpawnPlacement.offset(new Location(null, 0, 64, 0, 0f, 0f), 90, 10, 0);
        assertEquals(-10.0, out.getX(), EPS);
        assertEquals(0.0, out.getZ(), EPS);
    }

    @Test
    void offsetDoesNotMutateOrigin() {
        Location origin = new Location(null, 5, 64, 5, 0f, 0f);
        SpawnPlacement.offset(origin, 45, 10, 3);
        assertEquals(5.0, origin.getX(), EPS);
        assertEquals(64.0, origin.getY(), EPS);
        assertEquals(5.0, origin.getZ(), EPS);
    }

    @Test
    void forSummonerUsesOriginYaw() {
        Location out = SpawnPlacement.forSummoner(new Location(null, 0, 64, 0, 90f, 0f), 10, 0);
        assertEquals(-10.0, out.getX(), EPS);
        assertEquals(0.0, out.getZ(), EPS);
    }

    @Test
    void chooseBestPicksHighestOpenness() {
        double[] open = {1, 1, 5, 1, 1, 1, 1, 1};
        assertEquals(2, SpawnPlacement.chooseBestDirection(open, 0));
    }

    @Test
    void chooseBestBreaksTiesTowardPreferredWhenItAchievesMax() {
        double[] open = {5, 1, 5, 1, 5, 1, 1, 1};
        assertEquals(4, SpawnPlacement.chooseBestDirection(open, 4), "preferred index achieves the max");
        assertEquals(0, SpawnPlacement.chooseBestDirection(open, 3), "preferred not max -> lowest max index");
    }

    @Test
    void chooseBestWithOutOfRangePreferredUsesLowestMaxIndex() {
        double[] open = {1, 9, 1, 9, 1, 1, 1, 1};
        assertEquals(1, SpawnPlacement.chooseBestDirection(open, -1));
    }
}
