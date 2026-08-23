/*
 * TNTLibrary - a custom-TNT framework and a set of creative, premium explosives.
 * Copyright (C) 2026 Carmelo Santana
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * See the LICENSE file at the project root for the full license text.
 */
package org.xpfarm.tntlibrary.twins;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.junit.jupiter.api.Test;
import org.xpfarm.tntlibrary.core.RecipeSpec;

/**
 * Pins the pure, server-free members of {@link TheTwins} — {@link TheTwins#id()},
 * {@link TheTwins#fuseTicks()}, {@link TheTwins#recipeSpec()}, and the {@code minecraft:item_model}
 * key constants. These assertions run headless: none of them constructs an {@link
 * org.bukkit.inventory.ItemStack} or reaches a running server. {@code createItem()} and
 * {@code detonate(...)} are Bukkit-touching and verified at the runtime gate (gate-12), not here.
 */
final class TheTwinsTest {

    /** A Twin wired with an easily-recognised fuse and pair-distance, over a throwaway index. */
    private static TheTwins twin(TwinColor color) {
        return new TheTwins(color, 90, 3, 32.0, new PlacedTwinIndex());
    }

    @Test
    void idIsTheStableVariantStringForItsColour() {
        assertEquals("twins_white", twin(TwinColor.WHITE).id());
        assertEquals("twins_black", twin(TwinColor.BLACK).id());
    }

    @Test
    void fuseTicksReturnsTheInjectedValue() {
        assertEquals(90, twin(TwinColor.WHITE).fuseTicks());
        assertEquals(7, new TheTwins(TwinColor.BLACK, 7, 3, 32.0, new PlacedTwinIndex()).fuseTicks());
    }

    @Test
    void whiteRecipeSpecIsWhiteWoolCornersAroundTnt() {
        RecipeSpec spec = twin(TwinColor.WHITE).recipeSpec();
        assertEquals(List.of("W W", " T ", "W W"), spec.rows());
        assertEquals(Map.of('W', Material.WHITE_WOOL, 'T', Material.TNT), spec.ingredients());
    }

    @Test
    void blackRecipeSpecIsBlackWoolCornersAroundTnt() {
        RecipeSpec spec = twin(TwinColor.BLACK).recipeSpec();
        assertEquals(List.of("K K", " T ", "K K"), spec.rows());
        assertEquals(Map.of('K', Material.BLACK_WOOL, 'T', Material.TNT), spec.ingredients());
    }

    @Test
    void constructorRejectsNullColour() {
        assertThrows(NullPointerException.class,
                () -> new TheTwins(null, 90, 3, 32.0, new PlacedTwinIndex()));
    }

    @Test
    void constructorRejectsNullIndex() {
        assertThrows(NullPointerException.class,
                () -> new TheTwins(TwinColor.WHITE, 90, 3, 32.0, null));
    }

    @Test
    void itemModelKeysUseTheExactTntLibraryNamespace() {
        assertEquals(new NamespacedKey("tnt_library", "twins_white"), TheTwins.ITEM_MODEL_WHITE);
        assertEquals(new NamespacedKey("tnt_library", "twins_black"), TheTwins.ITEM_MODEL_BLACK);
    }
}
