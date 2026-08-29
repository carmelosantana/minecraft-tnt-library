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
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Pins the two headless-checkable params of the guaranteed server-side FREEZE kill: the damage value
 * carries through (clamped to the minimum so a degenerate value can never disarm the finisher) and the
 * intended {@code DamageType} is {@code FREEZE}. The live {@code DamageSource} construction is in
 * {@link FreezeFinisher}, verified at the runtime gate.
 */
final class FreezeFinisherSpecTest {

    @Test
    void ofCarriesTheKillDamage() {
        assertEquals(1000.0, FreezeFinisherSpec.of(1000).damage());
    }

    @Test
    void subMinDamageClampsUp() {
        assertEquals(WhiteoutParams.KILL_DAMAGE_MIN, FreezeFinisherSpec.of(0.0).damage());
    }

    @Test
    void damageTypeIsFreeze() {
        assertTrue(FreezeFinisherSpec.DAMAGE_TYPE.equals("FREEZE"));
    }
}
