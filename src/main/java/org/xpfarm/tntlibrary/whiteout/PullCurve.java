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

/**
 * Pure velocity shaping the White Out runtime applies each tick of the pull window: an inward,
 * escalating-capped velocity toward the blast center. It only produces {@link Vec3} values; the Bukkit
 * edge ({@code WhiteoutSequenceTask}) turns them into {@code entity.setVelocity(...)}. Deterministic —
 * no RNG — so the shaping stays exactly unit-testable.
 *
 * <p><strong>The curve.</strong> {@code fraction = clamp((radius - distance) / radius, 0, 1)} and
 * {@code magnitude = pullPower * fraction}. It is {@code pullPower} at the center, {@code 0} at and
 * beyond the rim, strictly increasing as the entity nears the center (collapsing-singularity feel), and
 * always within {@code [0, pullPower]} (the cap kept modest for the Paper #13270 setVelocity
 * regression, since {@code pullPower} is clamped to {@code <= 3.0} by {@link WhiteoutParams}).
 *
 * <p><strong>Accepted Bedrock degradation.</strong> Player velocity is client-authoritative through
 * Geyser, so the pull is strong on Java and weak/ignored for Bedrock players. The guaranteed kill is
 * the server-side FREEZE finisher, not this pull.
 */
public final class PullCurve {

    /** Entities inside this radius of the center are treated as already at the singularity core. */
    public static final double CORE_RADIUS = 1.5;

    private PullCurve() {}

    /**
     * The inward pull magnitude at {@code distance} from the center. Rises linearly from {@code 0} at
     * the rim to {@code pullPower} at the center; clamped to {@code [0, pullPower]} and to {@code 0}
     * beyond the rim.
     *
     * @param distance the entity's distance from the center, in blocks (>= 0)
     * @param radius the pull radius, in blocks (already clamped >= 1 upstream)
     * @param pullPower the magnitude ceiling (already clamped to {@code [0, 3]} upstream)
     */
    public static double magnitude(double distance, int radius, double pullPower) {
        if (radius <= 0) {
            return 0.0;
        }
        double fraction = (radius - distance) / radius;
        if (fraction < 0.0) {
            fraction = 0.0;
        } else if (fraction > 1.0) {
            fraction = 1.0;
        }
        return pullPower * fraction;
    }

    /**
     * The inward pull velocity for an entity at {@code from} being drawn toward {@code center}: the unit
     * direction toward the center scaled by {@link #magnitude}. Returns {@code (0, 0, 0)} when {@code
     * from} equals {@code center} (zero distance — no direction to pull along).
     */
    public static Vec3 pullVelocity(Vec3 from, Vec3 center, int radius, double pullPower) {
        double dx = center.x() - from.x();
        double dy = center.y() - from.y();
        double dz = center.z() - from.z();
        double distanceSq = dx * dx + dy * dy + dz * dz;
        if (distanceSq == 0.0) {
            return new Vec3(0, 0, 0);
        }
        double distance = Math.sqrt(distanceSq);
        double scale = magnitude(distance, radius, pullPower) / distance;
        return new Vec3(dx * scale, dy * scale, dz * scale);
    }
}
