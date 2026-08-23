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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.xpfarm.tntlibrary.config.BombType;
import org.xpfarm.tntlibrary.twins.TwinColor;

/**
 * Pins the pure {@link BombBlocks} state-claim table — the contract the Java resource pack and the
 * Geyser {@code custom_mappings} both mirror. These assertions run without a server (no {@link
 * org.bukkit.Bukkit} calls); the {@link org.bukkit.block.data.BlockData} resolution is verified at
 * the runtime gate.
 *
 * <p>The table is keyed by <em>placed</em> id, not by {@link BombType} id: most bombs map one config
 * id to one state, but the Twins ship as two placed variants ({@code twins_white} at the base {@code
 * twins} slot, {@code twins_black} just below the sequential range) while the base {@code twins} id
 * itself has no placed state. {@link #PLACED} is the authoritative expected allocation.
 */
final class BombBlocksTest {

    /** The full expected placed-id → note allocation (variant ids where a bomb has variants). */
    private static final Map<String, Integer> PLACED;

    static {
        Map<String, Integer> placed = new LinkedHashMap<>();
        placed.put("waterbomb", 19);
        placed.put("twins_white", 20); // takes the base `twins` declaration slot
        placed.put("twins_black", 18); // dedicated slot just below FIRST_NOTE
        placed.put("smartbomb", 21);
        placed.put("fbomb", 22);
        placed.put("gbomb", 23);
        placed.put("whiteout", 24);
        PLACED = Map.copyOf(placed);
    }

    @Test
    void everyPlacedIdHasItsClaimedNoteAndTheBaseTwinsHasNone() {
        PLACED.forEach((id, note) ->
                assertEquals(note, BombBlocks.noteFor(id).orElseThrow(), "wrong note for " + id));
        // The base `twins` id is config/permission-only — it claims no placed state.
        assertTrue(BombBlocks.noteFor(TwinColor.BASE_ID).isEmpty(),
                "base twins id must claim no placed state (it splits into variants)");
    }

    @Test
    void everyNonVariantBombTypeIsPlacedAndTwinsSplitsIntoVariants() {
        for (BombType type : BombType.values()) {
            if (type.id().equals(TwinColor.BASE_ID)) {
                assertTrue(BombBlocks.noteFor(type.id()).isEmpty(),
                        "base twins must have no placed state");
                assertTrue(BombBlocks.noteFor(TwinColor.WHITE.variantId()).isPresent());
                assertTrue(BombBlocks.noteFor(TwinColor.BLACK.variantId()).isPresent());
            } else {
                assertTrue(BombBlocks.noteFor(type.id()).isPresent(),
                        "no claimed note for bomb id " + type.id());
            }
        }
    }

    @Test
    void claimedNotesAreDistinct() {
        Set<Integer> seen = new HashSet<>();
        PLACED.values().forEach(note ->
                assertTrue(seen.add(note), "duplicate note " + note));
    }

    @Test
    void claimedNotesStayWithinTheValidNoteBlockRange() {
        PLACED.forEach((id, note) ->
                assertTrue(note >= 0 && note <= 24, "note " + note + " outside 0..24 for " + id));
    }

    @Test
    void stateKeyIsCanonicalForWaterBomb() {
        assertEquals("instrument=pling,note=19,powered=false", BombBlocks.stateKeyFor("waterbomb"));
        assertEquals("minecraft:note_block[instrument=pling,note=19,powered=false]",
                BombBlocks.blockDataStringFor("waterbomb"));
    }

    @Test
    void twinVariantsClaimTheirOwnDistinctStates() {
        assertEquals("instrument=pling,note=20,powered=false",
                BombBlocks.stateKeyFor("twins_white"));
        assertEquals("instrument=pling,note=18,powered=false",
                BombBlocks.stateKeyFor("twins_black"));
    }

    @Test
    void stateKeyAndBombIdRoundTripForEveryPlacedId() {
        for (String id : PLACED.keySet()) {
            String key = BombBlocks.stateKeyFor(id);
            assertEquals(id, BombBlocks.bombIdForStateKey(key).orElseThrow(),
                    "round-trip failed for " + id);
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
    void theBaseTwinsIdHasNoState() {
        // The base `twins` id resolves to no placed state at all: no note, and stateKeyFor rejects it.
        assertThrows(IllegalArgumentException.class, () -> BombBlocks.stateKeyFor(TwinColor.BASE_ID));
        assertTrue(BombBlocks.noteFor(TwinColor.BASE_ID).isEmpty());
    }

    @Test
    void donorIsNoteBlock() {
        assertEquals("minecraft:note_block", BombBlocks.DONOR);
    }
}
