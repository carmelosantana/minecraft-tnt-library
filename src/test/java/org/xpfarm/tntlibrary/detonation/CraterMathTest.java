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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.xpfarm.tntlibrary.detonation.CraterMath.Cell;
import org.xpfarm.tntlibrary.detonation.CraterMath.Column;
import org.xpfarm.tntlibrary.detonation.CraterMath.SurfaceHeightFn;

/**
 * Unit tests for the server-free crater geometry in {@link CraterMath}: footprint projection, the
 * rim/pool level, and which cells fall at or below it. This is the half of the Water Bomb fill that
 * needs no world; the block reads and writes it feeds live in {@code DetonationListener} and are
 * verified at the runtime gate.
 */
final class CraterMathTest {

    /** A {@link SurfaceHeightFn} backed by a fixed column-to-height map; absent columns are void. */
    private static SurfaceHeightFn heights(Map<Column, Integer> map) {
        return (x, z) -> {
            Integer h = map.get(new Column(x, z));
            return h == null ? OptionalInt.empty() : OptionalInt.of(h);
        };
    }

    private static Map<Column, Integer> ring(int height, Column... columns) {
        Map<Column, Integer> map = new HashMap<>();
        for (Column c : columns) {
            map.put(c, height);
        }
        return map;
    }

    @Test
    void columnsOfProjectsCellsDroppingY() {
        List<Cell> crater = List.of(
                new Cell(0, 60, 0), new Cell(0, 61, 0), new Cell(0, 62, 0), new Cell(1, 60, 0));

        Set<Column> columns = CraterMath.columnsOf(crater);

        assertEquals(Set.of(new Column(0, 0), new Column(1, 0)), columns);
    }

    @Test
    void rimLevelOfUniformSurroundingsIsThatHeight() {
        List<Cell> crater = List.of(new Cell(0, 60, 0), new Cell(0, 61, 0), new Cell(0, 62, 0));
        Map<Column, Integer> surrounding = ring(
                62, new Column(1, 0), new Column(-1, 0), new Column(0, 1), new Column(0, -1));

        assertEquals(OptionalInt.of(62), CraterMath.rimLevel(crater, heights(surrounding)));
    }

    @Test
    void rimLevelIsTheLowestLipOnSlopedGround() {
        List<Cell> crater = List.of(new Cell(0, 58, 0), new Cell(0, 59, 0), new Cell(0, 60, 0));
        Map<Column, Integer> surrounding = new HashMap<>();
        surrounding.put(new Column(1, 0), 63);
        surrounding.put(new Column(-1, 0), 60); // the low lip: water spills here first
        surrounding.put(new Column(0, 1), 62);
        surrounding.put(new Column(0, -1), 61);

        assertEquals(OptionalInt.of(60), CraterMath.rimLevel(crater, heights(surrounding)));
    }

    @Test
    void rimLevelIgnoresInteriorNeighboursOfAMultiColumnCrater() {
        // Two-column crater; (0,0)<->(1,0) are each other's interior neighbours and must not count.
        List<Cell> crater = List.of(new Cell(0, 60, 0), new Cell(1, 60, 0));
        Map<Column, Integer> surrounding = new HashMap<>();
        // Exterior rim of the pair:
        surrounding.put(new Column(-1, 0), 64);
        surrounding.put(new Column(2, 0), 64);
        surrounding.put(new Column(0, 1), 64);
        surrounding.put(new Column(0, -1), 64);
        surrounding.put(new Column(1, 1), 64);
        surrounding.put(new Column(1, -1), 64);
        // A trap: if the interior link were mis-sampled it would read one of these as ground.
        surrounding.put(new Column(0, 0), 1);
        surrounding.put(new Column(1, 0), 1);

        assertEquals(OptionalInt.of(64), CraterMath.rimLevel(crater, heights(surrounding)));
    }

    @Test
    void rimLevelIsEmptyForAnEmptyCrater() {
        assertTrue(CraterMath.rimLevel(List.of(), heights(Map.of())).isEmpty());
    }

    @Test
    void rimLevelIsEmptyWhenEverySurroundingColumnIsVoid() {
        List<Cell> crater = List.of(new Cell(0, 60, 0));
        assertTrue(CraterMath.rimLevel(crater, heights(Map.of())).isEmpty());
    }

    @Test
    void cellsAtOrBelowIsInclusiveOfTheRim() {
        List<Cell> crater = List.of(
                new Cell(0, 60, 0), new Cell(0, 61, 0), new Cell(0, 62, 0), new Cell(0, 63, 0));

        Set<Integer> ys = CraterMath.cellsAtOrBelow(crater, 62).stream()
                .map(Cell::y).collect(Collectors.toSet());

        assertEquals(Set.of(60, 61, 62), ys); // 63 is above the rim, excluded
    }

    @Test
    void floodCellsFillsTheBowlUpToButNotOverTheLowestRim() {
        // A bowl: a deep centre column and a shallower side column, rim measured from the outside.
        List<Cell> crater = List.of(
                new Cell(0, 58, 0), new Cell(0, 59, 0), new Cell(0, 60, 0), // centre stack
                new Cell(0, 61, 0));                                        // one above the lip
        Map<Column, Integer> surrounding = ring(
                60, new Column(1, 0), new Column(-1, 0), new Column(0, 1), new Column(0, -1));

        List<Cell> flooded = CraterMath.floodCells(crater, heights(surrounding));
        Set<Integer> ys = flooded.stream().map(Cell::y).collect(Collectors.toSet());

        assertEquals(Set.of(58, 59, 60), ys); // filled to the rim (60); the y=61 cell stays air
    }

    @Test
    void floodCellsIsEmptyWhenThereIsNoMeasurableRim() {
        List<Cell> crater = List.of(new Cell(0, 60, 0));
        assertTrue(CraterMath.floodCells(crater, heights(Map.of())).isEmpty());
    }

    @Test
    void floodCellsExcludesNothingBelowRimAndEverythingAbove() {
        List<Cell> crater = List.of(new Cell(5, 70, 5), new Cell(5, 71, 5), new Cell(5, 72, 5));
        Map<Column, Integer> surrounding = ring(
                71, new Column(6, 5), new Column(4, 5), new Column(5, 6), new Column(5, 4));

        List<Cell> flooded = CraterMath.floodCells(crater, heights(surrounding));

        assertEquals(2, flooded.size());
        assertTrue(flooded.contains(new Cell(5, 70, 5)));
        assertTrue(flooded.contains(new Cell(5, 71, 5)));
        assertFalse(flooded.contains(new Cell(5, 72, 5)));
    }
}
