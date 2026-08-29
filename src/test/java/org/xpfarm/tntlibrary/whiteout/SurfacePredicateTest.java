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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

/**
 * Pins the two pure block-classification predicates over {@link Material}. Both use only headless-safe
 * enum identity and {@link Material#name()} patterns (the registry-backed {@code isSolid()}/{@code
 * isOccluding()} throw {@code No RegistryAccess} in a plain JUnit test), so this test needs no server.
 */
final class SurfacePredicateTest {

    @Test
    void fullSolidCubesAreSkinTargets() {
        assertTrue(SurfacePredicate.isSkinTarget(Material.STONE));
        assertTrue(SurfacePredicate.isSkinTarget(Material.DIRT));
        assertTrue(SurfacePredicate.isSkinTarget(Material.GRASS_BLOCK));
        assertTrue(SurfacePredicate.isSkinTarget(Material.SAND));
        assertTrue(SurfacePredicate.isSkinTarget(Material.DEEPSLATE));
        assertTrue(SurfacePredicate.isSkinTarget(Material.WHITE_CONCRETE));
    }

    @Test
    void airAndFluidsAreNotSkinTargets() {
        assertFalse(SurfacePredicate.isSkinTarget(Material.AIR));
        assertFalse(SurfacePredicate.isSkinTarget(Material.CAVE_AIR));
        assertFalse(SurfacePredicate.isSkinTarget(Material.VOID_AIR));
        assertFalse(SurfacePredicate.isSkinTarget(Material.WATER));
        assertFalse(SurfacePredicate.isSkinTarget(Material.LAVA));
    }

    @Test
    void leavesSnowLayerCarpetAndPlantsAreNotSkinTargets() {
        assertFalse(SurfacePredicate.isSkinTarget(Material.OAK_LEAVES));
        assertFalse(SurfacePredicate.isSkinTarget(Material.SNOW));         // the layer, not SNOW_BLOCK
        assertFalse(SurfacePredicate.isSkinTarget(Material.WHITE_CARPET));
        assertFalse(SurfacePredicate.isSkinTarget(Material.POPPY));
        assertFalse(SurfacePredicate.isSkinTarget(Material.DANDELION));
        assertFalse(SurfacePredicate.isSkinTarget(Material.OAK_SLAB));
        assertFalse(SurfacePredicate.isSkinTarget(Material.OAK_STAIRS));
        assertFalse(SurfacePredicate.isSkinTarget(Material.OAK_FENCE));
        assertFalse(SurfacePredicate.isSkinTarget(Material.GLASS_PANE));
    }

    @Test
    void snowBlockIsAFullCubeSkinTarget() {
        // The full block is a legitimate surface to skin; only the thin SNOW layer is skipped.
        assertTrue(SurfacePredicate.isSkinTarget(Material.SNOW_BLOCK));
    }

    @Test
    void unbreakableSpecialsAreFlagged() {
        assertTrue(SurfacePredicate.isUnbreakableSpecial(Material.BEDROCK));
        assertTrue(SurfacePredicate.isUnbreakableSpecial(Material.BARRIER));
        assertTrue(SurfacePredicate.isUnbreakableSpecial(Material.COMMAND_BLOCK));
        assertTrue(SurfacePredicate.isUnbreakableSpecial(Material.CHAIN_COMMAND_BLOCK));
        assertTrue(SurfacePredicate.isUnbreakableSpecial(Material.REPEATING_COMMAND_BLOCK));
        assertTrue(SurfacePredicate.isUnbreakableSpecial(Material.END_PORTAL));
        assertTrue(SurfacePredicate.isUnbreakableSpecial(Material.END_PORTAL_FRAME));
        assertTrue(SurfacePredicate.isUnbreakableSpecial(Material.END_GATEWAY));
        assertTrue(SurfacePredicate.isUnbreakableSpecial(Material.JIGSAW));
        assertTrue(SurfacePredicate.isUnbreakableSpecial(Material.STRUCTURE_BLOCK));
        assertTrue(SurfacePredicate.isUnbreakableSpecial(Material.LIGHT));
    }

    @Test
    void ordinaryBlocksAreNotUnbreakableSpecials() {
        assertFalse(SurfacePredicate.isUnbreakableSpecial(Material.STONE));
        assertFalse(SurfacePredicate.isUnbreakableSpecial(Material.WHITE_CONCRETE));
        assertFalse(SurfacePredicate.isUnbreakableSpecial(Material.OBSIDIAN));
    }
}
