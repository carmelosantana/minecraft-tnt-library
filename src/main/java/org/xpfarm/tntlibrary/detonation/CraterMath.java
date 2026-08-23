/*
 * TNTLibrary - a custom-TNT framework and a set of creative, premium explosives.
 * Copyright (C) 2026 Carmelo Santana
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * See the LICENSE file at the project root for the full license text.
 */
package org.xpfarm.tntlibrary.detonation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;

/**
 * The pure, server-free geometry of turning a crater into a still pool.
 *
 * <p>All the reasoning that does <em>not</em> need a live world lives here so it can be unit-tested
 * directly: given the set of blocks an explosion cleared (the crater cavity) and a function that
 * reports the surrounding ground height, decide the water level and which cells sit at or below it.
 * The actual block reads and writes — spawning the explosion, reading heights out of the world,
 * setting water — stay in {@code DetonationListener}, which is verified at the runtime gate.
 *
 * <h2>The rim / pool-level model</h2>
 *
 * <p>A crater is a set of {@link Cell}s. Project them to their {@code (x, z)} {@link Column}s: those
 * are the crater's footprint. A column that borders the outside world has orthogonal neighbours that
 * are <em>not</em> part of the crater; each such neighbour has a surrounding ground height. The pool
 * can only rise as high as the <b>lowest</b> point on that surrounding rim before it would spill over
 * — so the water level is the global minimum of every non-crater-neighbour height around the whole
 * footprint. Filling every crater cell whose {@code y} is at or below that level yields a flat, still
 * pool that never climbs above the terrain on its uphill side.
 */
public final class CraterMath {

    private CraterMath() {}

    /**
     * A single block cell in the crater, in absolute world block coordinates. Kept as a plain record
     * of primitives (not a Bukkit type) so this whole class stays server-free and testable.
     */
    public record Cell(int x, int y, int z) {
        /** This cell's horizontal {@link Column} — its {@code (x, z)}, dropping {@code y}. */
        public Column column() {
            return new Column(x, z);
        }
    }

    /** A vertical column of the world, identified by its horizontal {@code (x, z)}. */
    public record Column(int x, int z) {}

    /**
     * Reports the surface (top solid ground) height of a column, or {@link OptionalInt#empty()} for a
     * column with no ground at all (e.g. open void). At runtime this is backed by {@code
     * World#getHighestBlockYAt}; in tests it is a lookup over a fixed map.
     */
    @FunctionalInterface
    public interface SurfaceHeightFn {
        /**
         * @param x column x
         * @param z column z
         * @return the y of the topmost solid block, or empty if the column has no ground
         */
        OptionalInt heightAt(int x, int z);
    }

    /** The distinct horizontal footprint of a crater: every {@link Column} it touches. */
    public static Set<Column> columnsOf(Collection<Cell> craterCells) {
        Objects.requireNonNull(craterCells, "craterCells");
        Set<Column> columns = new HashSet<>();
        for (Cell cell : craterCells) {
            columns.add(cell.column());
        }
        return columns;
    }

    /**
     * The water level for this crater: the lowest surrounding-ground height across the whole rim.
     *
     * <p>For every crater column, its four orthogonal neighbours that are <em>not</em> themselves
     * crater columns are the rim samples; each contributes its {@link SurfaceHeightFn} height, and the
     * pool level is the minimum of all of them (so the pool cannot overflow the lowest lip). Returns
     * {@link OptionalInt#empty()} when the crater has no bounded rim to measure — an empty crater, or
     * one whose every neighbour column reports no ground — in which case the caller must place no
     * water rather than guess a level.
     */
    public static OptionalInt rimLevel(Collection<Cell> craterCells, SurfaceHeightFn surrounding) {
        Objects.requireNonNull(craterCells, "craterCells");
        Objects.requireNonNull(surrounding, "surrounding");
        Set<Column> footprint = columnsOf(craterCells);
        int rim = Integer.MAX_VALUE;
        boolean found = false;
        for (Column column : footprint) {
            for (Column neighbour : orthogonalNeighbours(column)) {
                if (footprint.contains(neighbour)) {
                    continue; // interior edge, not part of the rim
                }
                OptionalInt height = surrounding.heightAt(neighbour.x(), neighbour.z());
                if (height.isPresent()) {
                    rim = Math.min(rim, height.getAsInt());
                    found = true;
                }
            }
        }
        return found ? OptionalInt.of(rim) : OptionalInt.empty();
    }

    /**
     * The crater cells that should become water: every cell whose {@code y} is at or below {@code
     * rimLevel} (inclusive — a cell level with the surrounding ground top is still submerged). Order
     * is unspecified.
     */
    public static List<Cell> cellsAtOrBelow(Collection<Cell> craterCells, int rimLevel) {
        Objects.requireNonNull(craterCells, "craterCells");
        List<Cell> flooded = new ArrayList<>();
        for (Cell cell : craterCells) {
            if (cell.y() <= rimLevel) {
                flooded.add(cell);
            }
        }
        return flooded;
    }

    /**
     * Convenience: compute the rim ({@link #rimLevel}) and, if one exists, the cells to flood ({@link
     * #cellsAtOrBelow}) in one call. Returns an empty list when there is no measurable rim, so the
     * caller floods nothing rather than everything.
     */
    public static List<Cell> floodCells(Collection<Cell> craterCells, SurfaceHeightFn surrounding) {
        OptionalInt rim = rimLevel(craterCells, surrounding);
        return rim.isPresent() ? cellsAtOrBelow(craterCells, rim.getAsInt()) : List.of();
    }

    /** The four orthogonal (N/S/E/W) column neighbours of {@code column}. */
    private static List<Column> orthogonalNeighbours(Column column) {
        return List.of(
                new Column(column.x() + 1, column.z()),
                new Column(column.x() - 1, column.z()),
                new Column(column.x(), column.z() + 1),
                new Column(column.x(), column.z() - 1));
    }
}
