/*
 * TNTLibrary - a custom-TNT framework and a set of creative, premium explosives.
 * Copyright (C) 2026 Carmelo Santana
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * See the LICENSE file at the project root for the full license text.
 */
package org.xpfarm.tntlibrary.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the shape-as-data {@link RecipeSpec}.
 *
 * <p>{@code RecipeSpec} is deliberately server-free: it holds the recipe grid as {@code
 * List<String>} rows plus a {@code Map<Character, Material>} of ingredients, so the whole recipe
 * shape can be validated in a plain JUnit test long before a running server turns it into a live
 * {@code ShapedRecipe}. These tests exercise round-trip fidelity, immutability, and the validation
 * that rejects malformed grids.
 */
final class RecipeSpecTest {

    @Test
    void rowsAndIngredientsRoundTrip() {
        RecipeSpec spec = new RecipeSpec(
                List.of("WWW", "WTW", "WWW"),
                Map.of('W', Material.WATER_BUCKET, 'T', Material.TNT));

        assertEquals(List.of("WWW", "WTW", "WWW"), spec.rows());
        assertEquals(Material.WATER_BUCKET, spec.ingredients().get('W'));
        assertEquals(Material.TNT, spec.ingredients().get('T'));
    }

    @Test
    void everyNonSpaceCharInTheGridMustHaveAnIngredient() {
        // 'X' appears in the grid but is not mapped -> invalid.
        assertThrows(IllegalArgumentException.class,
                () -> new RecipeSpec(List.of("XTX"), Map.of('T', Material.TNT)));
    }

    @Test
    void spacesAreAllowedAsEmptySlotsAndNeedNoIngredient() {
        RecipeSpec spec = new RecipeSpec(List.of(" T ", "TTT"), Map.of('T', Material.TNT));
        assertTrue(spec.ingredients().containsKey('T'));
        assertEquals(2, spec.rows().size());
    }

    @Test
    void rejectsEmptyOrOversizedGrids() {
        assertThrows(IllegalArgumentException.class, () -> new RecipeSpec(List.of(), Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new RecipeSpec(List.of("TTTT"), Map.of('T', Material.TNT)));
        assertThrows(IllegalArgumentException.class,
                () -> new RecipeSpec(List.of("T", "T", "T", "T"), Map.of('T', Material.TNT)));
    }

    @Test
    void rejectsRaggedRows() {
        assertThrows(IllegalArgumentException.class,
                () -> new RecipeSpec(List.of("TT", "T"), Map.of('T', Material.TNT)));
    }

    @Test
    void storedCollectionsAreDefensiveCopiesAndUnmodifiable() {
        RecipeSpec spec = new RecipeSpec(List.of("T"), Map.of('T', Material.TNT));
        assertThrows(UnsupportedOperationException.class, () -> spec.rows().add("X"));
        assertThrows(UnsupportedOperationException.class,
                () -> spec.ingredients().put('Z', Material.STONE));
    }

    @Test
    void mutatingTheSourceCollectionsDoesNotLeakIntoTheSpec() {
        var rows = new java.util.ArrayList<>(List.of("T"));
        var ingredients = new java.util.HashMap<Character, Material>(Map.of('T', Material.TNT));
        RecipeSpec spec = new RecipeSpec(rows, ingredients);

        rows.add("XX");
        ingredients.put('Q', Material.STONE);

        assertEquals(List.of("T"), spec.rows());
        assertNotSame(ingredients, spec.ingredients());
        assertEquals(1, spec.ingredients().size());
    }
}
