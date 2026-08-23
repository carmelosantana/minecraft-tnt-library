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

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.xpfarm.tntlibrary.config.BombType;

/**
 * Pins the pure {@link BombBlocks} state-claim table — the contract the Java resource pack and the
 * Geyser {@code custom_mappings} both mirror. These assertions run without a server (no {@link
 * org.bukkit.Bukkit} calls); the {@link org.bukkit.block.data.BlockData} resolution is verified at
 * the runtime gate.
 */
final class BombBlocksTest {

    @Test
    void everyBombTypeHasAClaimedNote() {
        for (BombType type : BombType.values()) {
            assertTrue(BombBlocks.noteFor(type.id()).isPresent(),
                    "no claimed note for bomb id " + type.id());
        }
    }

    @Test
    void notesAreDistinctAndStartAtNineteenInDeclarationOrder() {
        int expected = BombBlocks.FIRST_NOTE;
        Set<Integer> seen = new HashSet<>();
        for (BombType type : BombType.values()) {
            int note = BombBlocks.noteFor(type.id()).orElseThrow();
            assertEquals(expected, note, "note for " + type.id() + " out of expected order");
            assertTrue(seen.add(note), "duplicate note " + note);
            expected++;
        }
    }

    @Test
    void claimedNotesStayWithinTheValidNoteBlockRange() {
        for (BombType type : BombType.values()) {
            int note = BombBlocks.noteFor(type.id()).orElseThrow();
            assertTrue(note >= 0 && note <= 24, "note " + note + " outside 0..24 for " + type.id());
        }
    }

    @Test
    void stateKeyIsCanonicalForWaterBomb() {
        assertEquals("instrument=pling,note=19,powered=false", BombBlocks.stateKeyFor("waterbomb"));
        assertEquals("minecraft:note_block[instrument=pling,note=19,powered=false]",
                BombBlocks.blockDataStringFor("waterbomb"));
    }

    @Test
    void stateKeyAndBombIdRoundTripForEveryBomb() {
        for (BombType type : BombType.values()) {
            String key = BombBlocks.stateKeyFor(type.id());
            assertEquals(type.id(), BombBlocks.bombIdForStateKey(key).orElseThrow(),
                    "round-trip failed for " + type.id());
        }
    }

    @Test
    void unknownIdsAreRejectedOrEmpty() {
        assertThrows(IllegalArgumentException.class, () -> BombBlocks.stateKeyFor("nope"));
        assertTrue(BombBlocks.noteFor("nope").isEmpty());
        assertTrue(BombBlocks.bombIdForStateKey("instrument=harp,note=0,powered=false").isEmpty());
        assertFalse(BombBlocks.bombIdForStateKey("instrument=pling,note=19,powered=true").isPresent());
    }

    @Test
    void donorIsNoteBlock() {
        assertEquals("minecraft:note_block", BombBlocks.DONOR);
    }
}
