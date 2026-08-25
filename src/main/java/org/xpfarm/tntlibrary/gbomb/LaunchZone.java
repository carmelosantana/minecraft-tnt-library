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
 * Pure spherical in-radius predicate — one of the headless-unit-tested geometry cores, with no Bukkit
 * import.
 *
 * <p>At runtime the edge first gathers a bounded <em>box</em> of candidates via
 * {@code world.getNearbyLivingEntities(center, r, r, r)}, then narrows that box to a true sphere by
 * keeping only the entities for which {@link #covers} returns {@code true}. Membership is the
 * <em>closed</em> ball: a point exactly on the surface is covered.
 *
 * <p>{@code covers} compares squared distance to squared radius and never calls {@link Math#sqrt} —
 * this avoids a square root per candidate and sidesteps its rounding. The radius is taken as-given;
 * it is already clamped upstream by {@link GBombParams}.
 *
 * @param cx the sphere center x coordinate
 * @param cy the sphere center y coordinate
 * @param cz the sphere center z coordinate
 * @param radius the sphere radius, in blocks
 */
public record LaunchZone(double cx, double cy, double cz, int radius) {

    /**
     * Whether {@code (x, y, z)} lies within the closed ball around the center.
     *
     * @param x the candidate x coordinate
     * @param y the candidate y coordinate
     * @param z the candidate z coordinate
     * @return {@code true} when the squared distance to the center is at most the squared radius
     *     (boundary included)
     */
    public boolean covers(double x, double y, double z) {
        double dx = x - cx;
        double dy = y - cy;
        double dz = z - cz;
        double r = radius;
        return dx * dx + dy * dy + dz * dz <= r * r;
    }
}
