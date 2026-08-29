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
 * Pins the pure escalating-capped inward pull: {@link PullCurve#magnitude} rises monotonically toward
 * the center, is clamped to {@code [0, pullPower]}, and hits zero at/beyond the rim; {@link
 * PullCurve#pullVelocity} points inward and is zero at the center. With {@code radius 24} the sampled
 * fractions are exact, so exact assertions are used where possible and an epsilon elsewhere.
 */
final class PullCurveTest {

    private static final double EPS = 1e-9;

    @Test
    void coreRadiusConstantIsOnePointFive() {
        assertEquals(1.5, PullCurve.CORE_RADIUS, EPS);
    }

    @Test
    void magnitudeAtCenterEqualsPullPower() {
        assertEquals(1.0, PullCurve.magnitude(0, 24, 1.0), EPS);
        assertEquals(3.0, PullCurve.magnitude(0, 24, 3.0), EPS);
    }

    @Test
    void magnitudeAtRimIsZero() {
        assertEquals(0.0, PullCurve.magnitude(24, 24, 1.0), EPS);
    }

    @Test
    void magnitudeBeyondRimClampsToZero() {
        assertEquals(0.0, PullCurve.magnitude(30, 24, 1.0), EPS);
    }

    @Test
    void magnitudeRisesMonotonicallyTowardCenter() {
        double near = PullCurve.magnitude(6, 24, 1.0);   // (24-6)/24 = 0.75
        double mid = PullCurve.magnitude(12, 24, 1.0);   // (24-12)/24 = 0.50
        double far = PullCurve.magnitude(18, 24, 1.0);   // (24-18)/24 = 0.25
        assertEquals(0.75, near, EPS);
        assertEquals(0.50, mid, EPS);
        assertEquals(0.25, far, EPS);
        assertTrue(near > mid && mid > far, "nearer the center must pull harder");
    }

    @Test
    void magnitudeNeverExceedsPullPower() {
        for (double d = 0; d <= 24; d += 0.5) {
            double m = PullCurve.magnitude(d, 24, 2.0);
            assertTrue(m >= 0.0 && m <= 2.0, "magnitude " + m + " out of [0, pullPower] at d=" + d);
        }
    }

    @Test
    void pullVelocityPointsInward() {
        Vec3 v = PullCurve.pullVelocity(new Vec3(10, 0, 0), new Vec3(0, 0, 0), 24, 1.0);
        // direction is toward center (-x); magnitude = (24-10)/24 = 14/24
        assertTrue(v.x() < 0, "must pull toward the center along -x");
        assertEquals(0.0, v.y(), EPS);
        assertEquals(0.0, v.z(), EPS);
        assertEquals(-(14.0 / 24.0), v.x(), EPS);
    }

    @Test
    void pullVelocityIsZeroAtTheCenter() {
        Vec3 v = PullCurve.pullVelocity(new Vec3(5, 5, 5), new Vec3(5, 5, 5), 24, 1.0);
        assertEquals(new Vec3(0, 0, 0), v);
    }
}
