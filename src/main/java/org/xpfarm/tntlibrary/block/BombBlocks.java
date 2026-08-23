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

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.NoteBlock;
import org.xpfarm.tntlibrary.config.BombType;
import org.xpfarm.tntlibrary.twins.TwinColor;

/**
 * The single source of truth linking each bomb id to the real vanilla {@code note_block} blockstate
 * it is placed as — the mechanism that makes a placed bomb a true 3D cube on <em>both</em> Java and
 * Bedrock (via a Geyser Custom Blocks override of that exact state).
 *
 * <h2>Why note_block</h2>
 *
 * <p>A placed bomb is not a display entity — display entities are invisible to Bedrock. It is a real
 * {@code minecraft:note_block} forced into a specific, otherwise-unused blockstate. A Java resource
 * pack reskins that state to the bomb's cube for Java clients; a Geyser {@code custom_mappings} entry
 * maps the same state to a Bedrock custom block for Bedrock clients. Note blocks are the
 * industry-standard donor: every vanilla note-block state shares one model, so overriding a handful
 * of states leaves natural note blocks untouched. Mushroom blocks were rejected — every mushroom face
 * is always textured, so a clean single-state override is impossible.
 *
 * <h2>The claimed states</h2>
 *
 * <p>Every placed bomb claims {@code instrument=pling, powered=false} and a distinct {@code note}
 * value ({@value #FIRST_NOTE}..) assigned in {@link BombType} declaration order. {@code pling} plus a
 * high, specific note keeps accidental collision with a hand-tuned vanilla note block vanishingly
 * unlikely; the placement layer additionally physics-locks the block so the instrument can never
 * re-derive from the block beneath it.
 *
 * <p><b>Variants.</b> Most bombs map one config id to one state. The Twins are the exception: they
 * ship as two placed variants — {@code twins_white} takes the base {@code twins} declaration slot and
 * {@code twins_black} takes {@link #TWINS_BLACK_NOTE} just below the sequential range — while the base
 * {@code twins} id itself is config/permission-only and claims no placed state. So the table is keyed
 * by <em>placed</em> id (variant where a bomb has variants), not by {@link BombType} id.
 *
 * <h2>Pure vs. runtime</h2>
 *
 * <p>The state <em>strings</em> ({@link #stateKeyFor}, {@link #bombIdForStateKey}, the table itself)
 * are pure data and unit-tested directly. Only {@link #blockDataFor} and the {@link Block}/{@link
 * BlockData} readers touch a running server (they call {@link Bukkit#createBlockData}); those are
 * verified at the runtime gate.
 */
public final class BombBlocks {

    /** The donor block every bomb is placed as. */
    public static final String DONOR = "minecraft:note_block";

    /** The instrument every claimed state uses; lowercased to match {@code note_block} state strings. */
    public static final String INSTRUMENT = "pling";

    /** The first {@code note} value handed out; subsequent bombs take {@value #FIRST_NOTE}+1, +2, … */
    public static final int FIRST_NOTE = 19;

    /**
     * The {@code note} the Black Twin claims — just below {@link #FIRST_NOTE}. The Twins are the one
     * bomb that ships as two placed <em>variants</em> rather than a single state: {@code twins_white}
     * takes the base {@code twins} declaration slot, and {@code twins_black} takes this dedicated slot
     * below the sequential range so it collides with no other bomb.
     */
    private static final int TWINS_BLACK_NOTE = FIRST_NOTE - 1;

    /** Bomb (or variant) id → the {@code note} value it claims. Insertion order follows {@link BombType}. */
    private static final Map<String, Integer> NOTE_BY_ID;

    /** Full state key (e.g. {@code instrument=pling,note=19,powered=false}) → bomb (or variant) id. */
    private static final Map<String, String> ID_BY_STATE_KEY;

    static {
        Map<String, Integer> noteById = new LinkedHashMap<>();
        Map<String, String> idByKey = new LinkedHashMap<>();
        int note = FIRST_NOTE;
        for (BombType type : BombType.values()) {
            int claimed = note++;
            if (type.id().equals(TwinColor.BASE_ID)) {
                // The Twins ship as two placed variants, not one base state: twins_white takes this
                // base declaration slot, twins_black a dedicated slot below FIRST_NOTE. The base
                // `twins` id itself is config/permission-only and claims no placed state.
                claim(noteById, idByKey, TwinColor.WHITE.variantId(), claimed);
                claim(noteById, idByKey, TwinColor.BLACK.variantId(), TWINS_BLACK_NOTE);
            } else {
                claim(noteById, idByKey, type.id(), claimed);
            }
        }
        NOTE_BY_ID = Map.copyOf(noteById);
        ID_BY_STATE_KEY = Map.copyOf(idByKey);
    }

    /** Records one id↔state claim in both directions. */
    private static void claim(
            Map<String, Integer> noteById, Map<String, String> idByKey, String id, int note) {
        noteById.put(id, note);
        idByKey.put(stateKey(note), id);
    }

    private BombBlocks() {}

    /** The {@code note} value the given bomb claims, or empty if {@code id} is not a known bomb. */
    public static Optional<Integer> noteFor(String id) {
        return Optional.ofNullable(NOTE_BY_ID.get(id));
    }

    /**
     * The blockstate key this bomb is placed as, e.g. {@code instrument=pling,note=19,powered=false}
     * — properties in the canonical alphabetical order the resource pack and Geyser mapping both use.
     *
     * @throws IllegalArgumentException if {@code id} is not a known bomb id
     */
    public static String stateKeyFor(String id) {
        Integer note = NOTE_BY_ID.get(id);
        if (note == null) {
            throw new IllegalArgumentException("Not a known bomb id: " + id);
        }
        return stateKey(note);
    }

    /** The bomb id claiming exactly {@code stateKey}, or empty if no bomb claims it. */
    public static Optional<String> bombIdForStateKey(String stateKey) {
        return Optional.ofNullable(ID_BY_STATE_KEY.get(stateKey));
    }

    /** The full block-data string this bomb is placed as, e.g. {@code minecraft:note_block[…]}. */
    public static String blockDataStringFor(String id) {
        return DONOR + "[" + stateKeyFor(id) + "]";
    }

    private static String stateKey(int note) {
        return "instrument=" + INSTRUMENT + ",note=" + note + ",powered=false";
    }

    // ---- runtime (server-dependent) ----------------------------------------------------------

    /**
     * The live {@link BlockData} for this bomb's claimed note-block state. Server-dependent (reaches
     * into CraftBukkit), so it is exercised at the runtime gate, not in JUnit.
     *
     * @throws IllegalArgumentException if {@code id} is not a known bomb id
     */
    public static BlockData blockDataFor(String id) {
        return Bukkit.createBlockData(blockDataStringFor(id));
    }

    /**
     * The bomb id this block is, or empty if it is not a claimed note-block state. Reads the block's
     * live {@link NoteBlock} data rather than any PDC marker — a placed bomb has no marker, only its
     * blockstate.
     */
    public static Optional<String> bombIdOf(Block block) {
        if (block == null || block.getType() != Material.NOTE_BLOCK) {
            return Optional.empty();
        }
        return bombIdOf(block.getBlockData());
    }

    /** The bomb id this block-data represents, or empty if it is not a claimed note-block state. */
    public static Optional<String> bombIdOf(BlockData data) {
        if (!(data instanceof NoteBlock note)) {
            return Optional.empty();
        }
        if (note.isPowered()) {
            return Optional.empty(); // every claimed state is powered=false
        }
        String key = "instrument=" + note.getInstrument().name().toLowerCase(Locale.ROOT)
                + ",note=" + note.getNote().getId()
                + ",powered=false";
        return bombIdForStateKey(key);
    }

    /** Whether {@code block} is any bomb's claimed note-block state. */
    public static boolean isBombBlock(Block block) {
        return bombIdOf(block).isPresent();
    }
}
