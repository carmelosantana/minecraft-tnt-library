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

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Pins the value-type contract: every field is clamped to its documented bound at construction so an
 * out-of-range {@link GBombParams} is unrepresentable. Unlike Smart Bomb, G-Bomb params are built
 * once from config and never edited per-block, so there are no {@code with*} copies to test.
 */
final class GBombParamsTest {

    @Test
    void defaultIsTheDocumentedBaseline() {
        assertEquals(new GBombParams(20, 50, 1.2, 1000.0), GBombParams.DEFAULT);
    }

    @Test
    void radiusClampsToBounds() {
        assertEquals(GBombParams.RADIUS_MIN, new GBombParams(0, 50, 1.2, 1000.0).radius());
        assertEquals(GBombParams.RADIUS_MAX, new GBombParams(999, 50, 1.2, 1000.0).radius());
    }

    @Test
    void hangTicksClampsToBounds() {
        assertEquals(GBombParams.HANG_TICKS_MIN, new GBombParams(20, 0, 1.2, 1000.0).hangTicks());
        assertEquals(
                GBombParams.HANG_TICKS_MAX, new GBombParams(20, 1_000_000, 1.2, 1000.0).hangTicks());
    }

    @Test
    void launchPowerClampsToBounds() {
        assertEquals(GBombParams.LAUNCH_POWER_MIN, new GBombParams(20, 50, -5.0, 1000.0).launchPower());
        assertEquals(GBombParams.LAUNCH_POWER_MAX, new GBombParams(20, 50, 99.0, 1000.0).launchPower());
    }

    @Test
    void killDamageClampsToBounds() {
        assertEquals(GBombParams.KILL_DAMAGE_MIN, new GBombParams(20, 50, 1.2, 0.0).killDamage());
        assertEquals(
                GBombParams.KILL_DAMAGE_MAX, new GBombParams(20, 50, 1.2, 9_999_999.0).killDamage());
    }
}
