/*
 * TNTLibrary - a custom-TNT framework and a set of creative, premium explosives.
 * Copyright (C) 2026 Carmelo Santana
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * See the LICENSE file at the project root for the full license text.
 */
package org.xpfarm.tntlibrary.smartbomb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Pins the proximity warning mapping: closeness drives pitch up and beep period down, monotonically. */
final class ProximityWarningTest {

    @Test
    void beyondRadiusDoesNotPlay() {
        ProximityWarning.Warning w = ProximityWarning.at(7.0, 6);
        assertFalse(w.play());
    }

    @Test
    void atEdgeUsesFarPitchAndFarPeriod() {
        ProximityWarning.Warning w = ProximityWarning.at(6.0, 6);
        assertTrue(w.play());
        assertEquals(ProximityWarning.PITCH_FAR, w.pitch(), 1e-6);
        assertEquals(ProximityWarning.PERIOD_FAR, w.periodTicks());
    }

    @Test
    void atContactUsesNearPitchAndNearPeriod() {
        ProximityWarning.Warning w = ProximityWarning.at(0.0, 6);
        assertTrue(w.play());
        assertEquals(ProximityWarning.PITCH_NEAR, w.pitch(), 1e-6);
        assertEquals(ProximityWarning.PERIOD_NEAR, w.periodTicks());
    }

    @Test
    void pitchIncreasesAndPeriodNonIncreasesAsDistanceFalls() {
        int radius = 8;
        float prevPitch = Float.NEGATIVE_INFINITY;
        int prevPeriod = Integer.MAX_VALUE;
        for (int dist = radius; dist >= 0; dist--) {
            ProximityWarning.Warning w = ProximityWarning.at(dist, radius);
            assertTrue(w.play());
            assertTrue(w.pitch() > prevPitch, "pitch must strictly increase as distance falls");
            assertTrue(w.periodTicks() <= prevPeriod, "periodTicks must be non-increasing as distance falls");
            prevPitch = w.pitch();
            prevPeriod = w.periodTicks();
        }
    }

    @Test
    void zeroRadiusGuardedNoPlayNoDivideByZero() {
        ProximityWarning.Warning w = ProximityWarning.at(0.0, 0);
        assertFalse(w.play());
    }
}
