/*
 * TNTLibrary - a custom-TNT framework and a set of creative, premium explosives.
 * Copyright (C) 2026 Carmelo Santana
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * See the LICENSE file at the project root for the full license text.
 */
package org.xpfarm.tntlibrary.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Pins the pure {@link PlacedBombIndex} — the location register the guard heals drifted bomb blocks
 * from. No {@link org.bukkit.Bukkit} calls, so it runs headless; the drift-and-heal behavior itself is
 * verified at the runtime gate.
 */
final class PlacedBombIndexTest {

    private static final UUID WORLD_A = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final UUID WORLD_B = UUID.fromString("00000000-0000-0000-0000-0000000000bb");

    @Test
    void getReturnsWhatWasPut() {
        PlacedBombIndex index = new PlacedBombIndex();
        index.put(WORLD_A, 1, 2, 3, "waterbomb");
        assertEquals("waterbomb", index.get(WORLD_A, 1, 2, 3).orElseThrow());
        assertTrue(index.contains(WORLD_A, 1, 2, 3));
        assertEquals(1, index.size());
    }

    @Test
    void missingLocationsAreEmpty() {
        PlacedBombIndex index = new PlacedBombIndex();
        index.put(WORLD_A, 1, 2, 3, "waterbomb");
        assertTrue(index.get(WORLD_A, 9, 9, 9).isEmpty(), "unregistered coordinate");
        assertTrue(index.get(WORLD_B, 1, 2, 3).isEmpty(), "same coordinate, other world");
        assertFalse(index.contains(WORLD_A, 9, 9, 9));
    }

    @Test
    void reRegisteringOverwritesInPlace() {
        PlacedBombIndex index = new PlacedBombIndex();
        index.put(WORLD_A, 1, 2, 3, "waterbomb");
        index.put(WORLD_A, 1, 2, 3, "smartbomb");
        assertEquals("smartbomb", index.get(WORLD_A, 1, 2, 3).orElseThrow());
        assertEquals(1, index.size(), "a location holds at most one id");
    }

    @Test
    void sameCoordinatesInDifferentWorldsAreDistinct() {
        PlacedBombIndex index = new PlacedBombIndex();
        index.put(WORLD_A, 1, 2, 3, "waterbomb");
        index.put(WORLD_B, 1, 2, 3, "smartbomb");
        assertEquals("waterbomb", index.get(WORLD_A, 1, 2, 3).orElseThrow());
        assertEquals("smartbomb", index.get(WORLD_B, 1, 2, 3).orElseThrow());
        assertEquals(2, index.size());
    }

    @Test
    void removeIsScopedAndIdempotent() {
        PlacedBombIndex index = new PlacedBombIndex();
        index.put(WORLD_A, 1, 2, 3, "waterbomb");
        index.put(WORLD_A, 4, 5, 6, "gbomb");
        index.remove(WORLD_A, 1, 2, 3);
        assertFalse(index.contains(WORLD_A, 1, 2, 3));
        assertTrue(index.contains(WORLD_A, 4, 5, 6), "other entries untouched");
        index.remove(WORLD_A, 1, 2, 3); // no-op, already gone
        index.remove(WORLD_B, 7, 8, 9); // no-op, world never seen
        assertEquals(1, index.size());
    }

    @Test
    void nullArgumentsAreRejectedOrTreatedAsAbsent() {
        PlacedBombIndex index = new PlacedBombIndex();
        assertThrows(NullPointerException.class, () -> index.put(null, 1, 2, 3, "waterbomb"));
        assertThrows(NullPointerException.class, () -> index.put(WORLD_A, 1, 2, 3, null));
        assertTrue(index.get(null, 1, 2, 3).isEmpty());
        index.remove(null, 1, 2, 3); // tolerated no-op
        assertEquals(0, index.size());
    }
}
