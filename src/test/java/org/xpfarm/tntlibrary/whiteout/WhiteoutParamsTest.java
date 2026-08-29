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

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Pins the value-type contract: every field is clamped to its documented bound at construction so an
 * out-of-range {@link WhiteoutParams} is unrepresentable. Built once from config, never edited
 * per-block, so there are no {@code with*} copies to test.
 */
final class WhiteoutParamsTest {

    @Test
    void defaultIsTheDocumentedBaseline() {
        assertEquals(new WhiteoutParams(24, 1.0, 60, 1000.0, 100), WhiteoutParams.DEFAULT);
    }

    @Test
    void inRangeValuesRoundTrip() {
        WhiteoutParams p = new WhiteoutParams(30, 2.5, 80, 500.0, 120);
        assertEquals(30, p.radius());
        assertEquals(2.5, p.pullPower());
        assertEquals(80, p.pullTicks());
        assertEquals(500.0, p.killDamage());
        assertEquals(120, p.effectTicks());
    }

    @Test
    void radiusClampsToBounds() {
        assertEquals(WhiteoutParams.RADIUS_MIN, new WhiteoutParams(0, 1.0, 60, 1000.0, 100).radius());
        assertEquals(WhiteoutParams.RADIUS_MAX, new WhiteoutParams(999, 1.0, 60, 1000.0, 100).radius());
    }

    @Test
    void pullPowerClampsToBounds() {
        assertEquals(WhiteoutParams.PULL_POWER_MIN,
                new WhiteoutParams(24, -5.0, 60, 1000.0, 100).pullPower());
        assertEquals(WhiteoutParams.PULL_POWER_MAX,
                new WhiteoutParams(24, 99.0, 60, 1000.0, 100).pullPower());
    }

    @Test
    void pullTicksClampsToBounds() {
        assertEquals(WhiteoutParams.PULL_TICKS_MIN,
                new WhiteoutParams(24, 1.0, 0, 1000.0, 100).pullTicks());
        assertEquals(WhiteoutParams.PULL_TICKS_MAX,
                new WhiteoutParams(24, 1.0, 1_000_000, 1000.0, 100).pullTicks());
    }

    @Test
    void killDamageClampsToBounds() {
        assertEquals(WhiteoutParams.KILL_DAMAGE_MIN,
                new WhiteoutParams(24, 1.0, 60, 0.0, 100).killDamage());
        assertEquals(WhiteoutParams.KILL_DAMAGE_MAX,
                new WhiteoutParams(24, 1.0, 60, 9_999_999.0, 100).killDamage());
    }

    @Test
    void effectTicksClampsToBounds() {
        assertEquals(WhiteoutParams.EFFECT_TICKS_MIN,
                new WhiteoutParams(24, 1.0, 60, 1000.0, 0).effectTicks());
        assertEquals(WhiteoutParams.EFFECT_TICKS_MAX,
                new WhiteoutParams(24, 1.0, 60, 1000.0, 1_000_000).effectTicks());
    }
}
