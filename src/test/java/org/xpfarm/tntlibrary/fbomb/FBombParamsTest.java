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

import org.junit.jupiter.api.Test;

/**
 * Pins the value-type contract: every field is clamped to its documented bound at construction so
 * an out-of-range {@link FBombParams} is unrepresentable.
 */
class FBombParamsTest {

    @Test
    void inRangeValuesRoundTrip() {
        FBombParams p = new FBombParams(6, 60, 80, 12, 6, 48, 6, 8);
        assertEquals(6, p.radius());
        assertEquals(60, p.fuseTicks());
        assertEquals(80, p.menaceTicks());
        assertEquals(12, p.spawnDistance());
        assertEquals(6, p.spawnHeight());
        assertEquals(48, p.bossbarRange());
        assertEquals(6, p.skullCount());
        assertEquals(8, p.skullCadenceTicks());
    }

    @Test
    void everyFieldClampsBelowMinAndAboveMax() {
        FBombParams lo = new FBombParams(-99, -99, -99, -99, -99, -99, -99, -99);
        assertEquals(FBombParams.RADIUS_MIN, lo.radius());
        assertEquals(FBombParams.FUSE_TICKS_MIN, lo.fuseTicks());
        assertEquals(FBombParams.MENACE_TICKS_MIN, lo.menaceTicks());
        assertEquals(FBombParams.SPAWN_DISTANCE_MIN, lo.spawnDistance());
        assertEquals(FBombParams.SPAWN_HEIGHT_MIN, lo.spawnHeight());
        assertEquals(FBombParams.BOSSBAR_RANGE_MIN, lo.bossbarRange());
        assertEquals(FBombParams.SKULL_COUNT_MIN, lo.skullCount());
        assertEquals(FBombParams.SKULL_CADENCE_TICKS_MIN, lo.skullCadenceTicks());

        FBombParams hi = new FBombParams(9999, 9999, 9999, 9999, 9999, 9999, 9999, 9999);
        assertEquals(FBombParams.RADIUS_MAX, hi.radius());
        assertEquals(FBombParams.FUSE_TICKS_MAX, hi.fuseTicks());
        assertEquals(FBombParams.MENACE_TICKS_MAX, hi.menaceTicks());
        assertEquals(FBombParams.SPAWN_DISTANCE_MAX, hi.spawnDistance());
        assertEquals(FBombParams.SPAWN_HEIGHT_MAX, hi.spawnHeight());
        assertEquals(FBombParams.BOSSBAR_RANGE_MAX, hi.bossbarRange());
        assertEquals(FBombParams.SKULL_COUNT_MAX, hi.skullCount());
        assertEquals(FBombParams.SKULL_CADENCE_TICKS_MAX, hi.skullCadenceTicks());
    }

    @Test
    void defaultIsWithinBounds() {
        assertEquals(new FBombParams(6, 60, 60, 12, 6, 48, 6, 8), FBombParams.DEFAULT);
    }
}
