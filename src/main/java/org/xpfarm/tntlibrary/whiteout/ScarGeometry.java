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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

/**
 * Pure ring geometry for the White Out terrain scar: the set of horizontal column offsets inside the
 * radius-disk, ordered center-out so the runtime can sweep {@code white_concrete} outward one ring at a
 * time (no single-tick hitch; the scar visibly spreads). No Bukkit import — the runtime maps each
 * {@code {dx, dz}} onto a live world column at the edge.
 *
 * <p>"Center-out" orders by squared distance from the center ascending (ties broken by {@code dx} then
 * {@code dz} so the order is fully deterministic). A "ring" is one distinct squared-distance value.
 */
public final class ScarGeometry {

    private ScarGeometry() {}

    /**
     * Every integer column offset {@code {dx, dz}} with {@code dx*dx + dz*dz <= radius*radius}, ordered
     * center-out. The first element is always {@code {0, 0}}.
     *
     * @param radius the scar radius, in blocks; {@code <= 0} yields just the center column
     * @return a fresh {@code int[][]} of {@code {dx, dz}} pairs, center-out
     */
    public static int[][] columnsCenterOut(int radius) {
        int r = Math.max(0, radius);
        int rSq = r * r;
        List<int[]> cols = new ArrayList<>();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz <= rSq) {
                    cols.add(new int[] {dx, dz});
                }
            }
        }
        cols.sort(Comparator
                .comparingInt((int[] c) -> c[0] * c[0] + c[1] * c[1])
                .thenComparingInt(c -> c[0])
                .thenComparingInt(c -> c[1]));
        return cols.toArray(new int[0][]);
    }

    /**
     * The number of distinct rings (distinct squared-distance values) inside the radius-disk. Used by
     * the runtime to size the sweep window.
     */
    public static int ringCount(int radius) {
        int r = Math.max(0, radius);
        int rSq = r * r;
        TreeSet<Integer> rings = new TreeSet<>();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                int d = dx * dx + dz * dz;
                if (d <= rSq) {
                    rings.add(d);
                }
            }
        }
        return rings.size();
    }
}
