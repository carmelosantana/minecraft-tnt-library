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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link SkullVolleySchedule}. Pure headless math: no Bukkit involved.
 */
final class SkullVolleyScheduleTest {

    @Test
    void firesEveryCadenceAfterTheStartOffset() {
        // count 3, cadence 10, start 10, window 60 -> 20, 30, 40
        assertEquals(List.of(20L, 30L, 40L), SkullVolleySchedule.fireTicks(3, 10, 10, 60));
    }

    @Test
    void volleyIsBoundedByCount() {
        // count 2, cadence 5, start 10, window 100 -> 15, 20
        assertEquals(List.of(15L, 20L), SkullVolleySchedule.fireTicks(2, 5, 10, 100));
    }

    @Test
    void ticksAtOrAfterBlastAreDropped() {
        // count 10, cadence 20, start 10, window 60 -> 30, 50 (70+ dropped, 60 is BLAST)
        assertEquals(List.of(30L, 50L), SkullVolleySchedule.fireTicks(10, 20, 10, 60));
    }

    @Test
    void zeroCountOrCadenceIsEmpty() {
        assertTrue(SkullVolleySchedule.fireTicks(0, 10, 10, 60).isEmpty());
        assertTrue(SkullVolleySchedule.fireTicks(5, 0, 10, 60).isEmpty());
    }

    @Test
    void firesAtMatchesTheSchedule() {
        assertTrue(SkullVolleySchedule.firesAt(30L, 3, 10, 10, 60));
        assertFalse(SkullVolleySchedule.firesAt(25L, 3, 10, 10, 60));
        assertFalse(SkullVolleySchedule.firesAt(60L, 10, 20, 10, 60)); // BLAST tick, never a skull
    }
}
