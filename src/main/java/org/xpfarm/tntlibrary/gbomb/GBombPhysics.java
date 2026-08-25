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

/**
 * Pure velocity + fall-distance shaping the G-Bomb runtime applies at the two motion beats — the
 * launch (up to apex) and the slam (down to ground). It only produces values: {@link Vec3} velocities
 * (from {@link Vec3}) and float fall distances. The Bukkit edge ({@code GBombRuntime}) turns these
 * into {@code entity.setVelocity(...)} and {@code entity.setFallDistance(...)} calls; nothing here
 * touches a server type.
 *
 * <p>Both velocities are pure vertical and deterministic — no RNG — so the shaping stays exactly
 * unit-testable and the motion reads clean rather than scattering targets sideways.
 *
 * <p><strong>Why fall distance is managed explicitly.</strong> Per the gate-1 gravity research, an
 * entity's fall distance does <em>not</em> accumulate while its gravity is disabled (the field the
 * client reads to decide a landing looks lethal simply stops advancing during the gravity-off hang),
 * and toggling gravity back on does <em>not</em> reset it. Neither side effect gives us what we want,
 * so the runtime sets the value itself: cleared to {@link #LAUNCH_FALL_DISTANCE} at launch, then
 * forced to {@link #SLAM_FALL_DISTANCE} at the slam so the Java-client landing reads lethal on its
 * own. That client read is cosmetic, though — the guaranteed kill is the server-side FALL
 * <em>finisher</em> ({@link FinisherSpec}), not the fall itself. On Bedrock the launch/fall visuals
 * are weak or absent (Geyser does not reliably translate {@code setGravity(false)} and player velocity
 * is client-authoritative), and the finisher is likewise what still lands the kill there.
 */
public final class GBombPhysics {

    /** Fixed modest downward slam velocity on the Y axis (blocks/tick). */
    public static final double SLAM_DOWN_VELOCITY = -1.5;

    /**
     * Fall distance forced at the slam so the Java-client landing reads lethal independent of the
     * finisher; the server-side FALL finisher is still the guaranteed kill.
     */
    public static final float SLAM_FALL_DISTANCE = 30.0f;

    /**
     * Fall distance cleared at launch. Set explicitly because gravity-off does not accumulate fall
     * distance and toggling gravity does not reset it (see the class javadoc).
     */
    public static final float LAUNCH_FALL_DISTANCE = 0.0f;

    private GBombPhysics() {}

    /**
     * The pure-vertical launch velocity carrying the requested (already-clamped) power upward.
     *
     * @param launchPower the upward launch magnitude, in blocks/tick
     * @return {@code (0, launchPower, 0)}
     */
    public static Vec3 launchVelocity(double launchPower) {
        return new Vec3(0, launchPower, 0);
    }

    /**
     * The fixed modest downward slam velocity.
     *
     * @return {@code (0, SLAM_DOWN_VELOCITY, 0)}
     */
    public static Vec3 slamVelocity() {
        return new Vec3(0, SLAM_DOWN_VELOCITY, 0);
    }

    /**
     * The fall distance to force at launch.
     *
     * @return {@link #LAUNCH_FALL_DISTANCE}
     */
    public static float launchFallDistance() {
        return LAUNCH_FALL_DISTANCE;
    }

    /**
     * The fall distance to force at the slam.
     *
     * @return {@link #SLAM_FALL_DISTANCE}
     */
    public static float slamFallDistance() {
        return SLAM_FALL_DISTANCE;
    }
}
