/*
 * TNTLibrary - a custom-TNT framework and a set of creative, premium explosives.
 * Copyright (C) 2026 Carmelo Santana
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * See the LICENSE file at the project root for the full license text.
 */
package org.xpfarm.tntlibrary.twins;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure generation of explosion sample points along the beam ("trench") between the two halves of
 * "The Twins" bomb — the geometry the runtime turns into particle spawns and per-block effects, with
 * no running server involved.
 *
 * <h2>Sampling convention</h2>
 *
 * <p>{@link #samplePoints} walks the straight segment from Twin A's block <em>centre</em> to Twin B's
 * block centre, inclusive of both endpoints, at roughly one-block spacing. A block centre is each
 * integer coordinate plus {@code 0.5}.
 *
 * <p>Let {@code d} be the centre-to-centre Euclidean distance. The step count is
 * {@code steps = max(1, (int) Math.floor(d))} and points are produced at parameter
 * {@code t = i / steps} for {@code i} in {@code 0..steps} inclusive, yielding {@code steps + 1}
 * points. The {@code max(1, …)} floor guarantees at least the two endpoints for any distinct pair
 * (including adjacent blocks, where {@code d == 1}). Spacing is therefore {@code d / steps}, always in
 * {@code [1.0, 2.0)} and near one block.
 *
 * <p>The <strong>first</strong> point is exactly A's centre and the <strong>last</strong> is exactly
 * B's centre: both endpoints are computed directly (integer coordinate {@code + 0.5}) rather than via
 * {@code t} arithmetic, because a {@code t = 1.0} linear interpolation can drift by a floating-point
 * ulp. Interior points are the linear interpolation {@code centreA + t · (centreB − centreA)}.
 *
 * <p>If A and B are the same block ({@code d == 0}), the result is a single point at that shared
 * centre.
 *
 * <p>Points are returned in A&rarr;B order.
 */
public final class TwinsBeam {

    private TwinsBeam() {}

    /**
     * Explosion sample points from {@code a}'s block centre to {@code b}'s block centre, inclusive of
     * both endpoints, at ~1-block spacing, in A&rarr;B order. See the class javadoc for the exact
     * count rule. Returns a single centre point when {@code a} and {@code b} are the same block.
     *
     * @param a the first twin's block position
     * @param b the second twin's block position
     * @return an unmodifiable list of {@code steps + 1} sample points (one point when {@code a == b})
     */
    public static List<Vec3> samplePoints(TwinLocation a, TwinLocation b) {
        double ax = a.x() + 0.5;
        double ay = a.y() + 0.5;
        double az = a.z() + 0.5;
        double bx = b.x() + 0.5;
        double by = b.y() + 0.5;
        double bz = b.z() + 0.5;

        Vec3 centreA = new Vec3(ax, ay, az);
        Vec3 centreB = new Vec3(bx, by, bz);

        double d = a.distanceTo(b);
        if (d == 0.0) {
            return List.of(centreA);
        }

        int steps = Math.max(1, (int) Math.floor(d));
        List<Vec3> points = new ArrayList<>(steps + 1);
        points.add(centreA); // exact endpoint, not t-interpolated
        for (int i = 1; i < steps; i++) {
            double t = (double) i / steps;
            points.add(new Vec3(
                    ax + t * (bx - ax),
                    ay + t * (by - ay),
                    az + t * (bz - az)));
        }
        points.add(centreB); // exact endpoint, not t-interpolated
        return List.copyOf(points);
    }
}
