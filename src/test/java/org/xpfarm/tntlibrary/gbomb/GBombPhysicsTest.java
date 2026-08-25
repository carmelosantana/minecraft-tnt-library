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
 * Pins the pure velocity + fall-distance shaping the runtime applies at launch and slam: launch is a
 * modest pure-vertical velocity carrying the requested power, the slam is a fixed modest downward
 * velocity, and the fall distances are the explicit values we set because gravity-off does not
 * accumulate fall distance and toggling gravity does not reset it. Every value is exactly
 * representable, so exact assertions are used with no epsilon.
 */
final class GBombPhysicsTest {

    @Test
    void launchVelocityIsPureVerticalCarryingPower() {
        assertEquals(new Vec3(0, 1.2, 0), GBombPhysics.launchVelocity(1.2));
        assertEquals(new Vec3(0, 3.0, 0), GBombPhysics.launchVelocity(3.0));
    }

    @Test
    void slamVelocityIsFixedModestDownward() {
        assertEquals(new Vec3(0, -1.5, 0), GBombPhysics.slamVelocity());
        assertEquals(GBombPhysics.SLAM_DOWN_VELOCITY, GBombPhysics.slamVelocity().y());
    }

    @Test
    void launchFallDistanceIsClearedToZero() {
        assertEquals(0.0f, GBombPhysics.launchFallDistance());
        assertEquals(GBombPhysics.LAUNCH_FALL_DISTANCE, GBombPhysics.launchFallDistance());
    }

    @Test
    void slamFallDistanceReadsLethal() {
        assertEquals(30.0f, GBombPhysics.slamFallDistance());
        assertEquals(GBombPhysics.SLAM_FALL_DISTANCE, GBombPhysics.slamFallDistance());
    }
}
