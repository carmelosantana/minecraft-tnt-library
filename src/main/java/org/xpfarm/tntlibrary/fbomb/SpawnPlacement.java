/*
 * TNTLibrary - a custom-TNT framework and a set of creative, premium explosives.
 * Copyright (C) 2026 Carmelo Santana
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * See the LICENSE file at the project root for the full license text.
 */
package org.xpfarm.tntlibrary.fbomb;

import org.bukkit.Location;

/**
 * Decides where the F-Bomb's fake-Wither apparition spawns relative to the bomb: the eight
 * candidate compass headings the air-probe scores, picking the most open one, and the
 * rig-offset math that turns a chosen heading (or a plain facing) into an actual spawn point.
 *
 * <p>Pure, world-independent math; {@link Location} is a plain Paper API data class constructable
 * headless with a null world, so this class is unit-tested directly with no server. The Bukkit
 * air-probe that <em>builds</em> the {@code openness} array scored against {@link #DIRECTION_YAWS}
 * lives in the director, not here.
 */
public final class SpawnPlacement {

    /** The eight candidate horizontal headings the air-probe scores, one per compass direction. */
    public static final float[] DIRECTION_YAWS = {0f, 45f, 90f, 135f, 180f, 225f, 270f, 315f};

    private SpawnPlacement() {
    }

    /**
     * The index into {@code openness} (and {@link #DIRECTION_YAWS}) with the greatest openness
     * score. Ties resolve toward {@code preferredIndex} when it is in range (i.e. in
     * {@code [0, openness.length)}) <em>and</em> its score equals the max; otherwise the lowest
     * index achieving the max.
     *
     * @param openness one score per candidate direction; must be the same length as {@link
     *     #DIRECTION_YAWS}
     * @param preferredIndex the index to favor on a tie, or any out-of-range value to always fall
     *     back to the lowest-index max
     */
    public static int chooseBestDirection(double[] openness, int preferredIndex) {
        double max = Double.NEGATIVE_INFINITY;
        int bestIndex = -1;
        for (int i = 0; i < openness.length; i++) {
            if (openness[i] > max) {
                max = openness[i];
                bestIndex = i;
            }
        }
        if (preferredIndex >= 0 && preferredIndex < openness.length
                && openness[preferredIndex] == max) {
            return preferredIndex;
        }
        return bestIndex;
    }

    /**
     * The location {@code distance} blocks along the horizontal heading {@code yawDegrees} and
     * {@code height} blocks above {@code origin}. Bukkit yaw maps to a horizontal heading of
     * {@code (-sin(yaw), 0, cos(yaw))}. The origin is never mutated; a clone is returned, keeping
     * its world and facing.
     */
    public static Location offset(Location origin, double yawDegrees, double distance, double height) {
        double yawRadians = Math.toRadians(yawDegrees);
        double dirX = -Math.sin(yawRadians);
        double dirZ = Math.cos(yawRadians);
        Location out = origin.clone();
        out.add(dirX * distance, height, dirZ * distance);
        return out;
    }

    /**
     * Convenience for the plain facing case: {@code distance} blocks along {@code origin}'s own
     * yaw and {@code height} blocks up. Equivalent to {@code offset(origin, origin.getYaw(),
     * distance, height)}.
     */
    public static Location forSummoner(Location origin, double distance, double height) {
        return offset(origin, origin.getYaw(), distance, height);
    }
}
