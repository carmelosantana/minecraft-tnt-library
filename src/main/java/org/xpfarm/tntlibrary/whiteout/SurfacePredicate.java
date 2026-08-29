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

import java.util.EnumSet;
import java.util.Set;
import org.bukkit.Material;

/**
 * Pure block-type classification for the White Out terrain scar — a headless-unit-tested fn over
 * {@link Material}, with no live-server touch.
 *
 * <p>{@link #isSkinTarget} answers "is this the topmost full solid cube worth skinning?" so the runtime
 * can scan a column downward past air, fluids, leaves, snow layers, carpets, slabs/stairs, panes, and
 * plants until it reaches a full cube. {@link #isUnbreakableSpecial} names blocks that must never be
 * converted even when they sit at the surface.
 *
 * <p><strong>Why name/enum classification, not {@code isSolid()}/{@code isOccluding()}.</strong> Those
 * registry-backed {@link Material} methods throw {@code IllegalStateException: No RegistryAccess} in a
 * headless JUnit test (verified against paper-api 26.1.2), which would make this predicate untestable.
 * Enum identity, {@link Material#name()}, and {@code EnumSet} are all headless-safe, so classification
 * is done with them. The predicate is a denylist: a {@link Material} is a skin target unless it is air,
 * a fluid, or a known non-full/decoration/plant category.
 */
public final class SurfacePredicate {

    /** Explicit non-full / non-cube materials that string patterns below do not already catch. */
    private static final Set<Material> NON_SKINNABLE = EnumSet.of(
            Material.AIR, Material.CAVE_AIR, Material.VOID_AIR,
            Material.WATER, Material.LAVA, Material.BUBBLE_COLUMN,
            Material.SNOW,            // the thin layer; SNOW_BLOCK is a full cube and IS a target
            Material.POWDER_SNOW,
            Material.FIRE, Material.SOUL_FIRE, Material.LIGHT, Material.NETHER_PORTAL,
            Material.VINE, Material.WEEPING_VINES, Material.TWISTING_VINES, Material.LILY_PAD,
            Material.DANDELION, Material.POPPY, Material.SHORT_GRASS, Material.TALL_GRASS,
            Material.FERN, Material.LARGE_FERN, Material.DEAD_BUSH, Material.SUGAR_CANE,
            Material.SCAFFOLDING, Material.LADDER, Material.END_ROD, Material.LIGHTNING_ROD);

    /** {@link Material#name()} suffixes/substrings that mark a non-full/decoration block. */
    private static final String[] NON_SKINNABLE_NAME_PARTS = {
            "LEAVES", "CARPET", "SLAB", "STAIRS", "FENCE", "FENCE_GATE", "WALL", "PANE",
            "DOOR", "TRAPDOOR", "SIGN", "BUTTON", "PRESSURE_PLATE", "SAPLING", "BANNER",
            "TORCH", "RAIL", "CANDLE", "FLOWER", "_BED", "PLANT"};

    /** Blocks that must never be converted, even at the surface. */
    private static final Set<Material> UNBREAKABLE_SPECIAL = EnumSet.of(
            Material.BEDROCK, Material.BARRIER, Material.STRUCTURE_VOID, Material.LIGHT,
            Material.COMMAND_BLOCK, Material.CHAIN_COMMAND_BLOCK, Material.REPEATING_COMMAND_BLOCK,
            Material.END_PORTAL, Material.END_PORTAL_FRAME, Material.END_GATEWAY,
            Material.JIGSAW, Material.STRUCTURE_BLOCK, Material.MOVING_PISTON);

    private SurfacePredicate() {}

    /**
     * Whether {@code m} is a full solid cube worth skinning to {@code white_concrete} — the runtime's
     * "topmost solid block" target as it scans a column downward. {@code false} for air, fluids, and
     * every enumerated or name-matched non-full/decoration/plant category.
     */
    public static boolean isSkinTarget(Material m) {
        if (m == null || NON_SKINNABLE.contains(m)) {
            return false;
        }
        String name = m.name();
        for (String part : NON_SKINNABLE_NAME_PARTS) {
            if (name.endsWith(part) || name.contains(part)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether {@code m} is an unbreakable/special block the scar must never convert (bedrock, barriers,
     * command blocks, end-portal furniture, structure/jigsaw blocks, and the like).
     */
    public static boolean isUnbreakableSpecial(Material m) {
        return m != null && UNBREAKABLE_SPECIAL.contains(m);
    }
}
