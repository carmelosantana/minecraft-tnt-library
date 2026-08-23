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

import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.bukkit.Material;

/**
 * A crafting recipe expressed as <em>data</em>, not as a live {@link
 * org.bukkit.inventory.ShapedRecipe}.
 *
 * <p>A {@code ShapedRecipe} cannot be constructed without a running server (its {@code
 * NamespacedKey}/{@code ItemStack} plumbing reaches into CraftBukkit), which would make every
 * recipe untestable. Modelling the shape as {@code rows} (up to a 3&times;3 grid of chars) plus an
 * {@code ingredients} map from char to {@link Material} keeps the recipe fully validatable in a
 * plain JUnit test; the {@code item} layer turns this into a real {@code ShapedRecipe} once a server
 * is running.
 *
 * <h2>Grid rules (enforced at construction)</h2>
 *
 * <ul>
 *   <li>1&ndash;3 rows, every row the same length, each row 1&ndash;3 chars wide.
 *   <li>{@code ' '} (space) is an intentionally empty slot and needs no ingredient.
 *   <li>Every other char that appears in the grid must have an entry in {@code ingredients}.
 * </ul>
 *
 * <p>Both collections are defensively copied and exposed unmodifiable, so a {@code RecipeSpec} is
 * effectively immutable.
 */
public final class RecipeSpec {

    private static final int MAX_DIMENSION = 3;

    private final List<String> rows;
    private final Map<Character, Material> ingredients;

    /**
     * @throws IllegalArgumentException if the grid is empty, larger than 3&times;3, ragged, or
     *     contains a non-space char with no matching ingredient
     * @throws NullPointerException if either argument (or any element) is {@code null}
     */
    public RecipeSpec(List<String> rows, Map<Character, Material> ingredients) {
        Objects.requireNonNull(rows, "rows");
        Objects.requireNonNull(ingredients, "ingredients");

        List<String> copiedRows = List.copyOf(rows); // rejects null elements, immutable
        Map<Character, Material> copiedIngredients = Map.copyOf(ingredients);

        validate(copiedRows, copiedIngredients);

        this.rows = copiedRows;
        this.ingredients = copiedIngredients;
    }

    private static void validate(List<String> rows, Map<Character, Material> ingredients) {
        if (rows.isEmpty() || rows.size() > MAX_DIMENSION) {
            throw new IllegalArgumentException(
                    "recipe must have 1 to " + MAX_DIMENSION + " rows, got " + rows.size());
        }
        int width = rows.get(0).length();
        if (width == 0 || width > MAX_DIMENSION) {
            throw new IllegalArgumentException(
                    "recipe rows must be 1 to " + MAX_DIMENSION + " chars wide, got " + width);
        }
        for (String row : rows) {
            if (row.length() != width) {
                throw new IllegalArgumentException(
                        "recipe rows must all be the same width; expected " + width + ", got \""
                                + row + "\"");
            }
            for (char slot : row.toCharArray()) {
                if (slot != ' ' && !ingredients.containsKey(slot)) {
                    throw new IllegalArgumentException(
                            "grid char '" + slot + "' has no ingredient mapping");
                }
            }
        }
    }

    /** The recipe grid, top row first, as an unmodifiable list. */
    public List<String> rows() {
        return rows;
    }

    /** The char&rarr;{@link Material} ingredient mapping, as an unmodifiable map. */
    public Map<Character, Material> ingredients() {
        return ingredients;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        return o instanceof RecipeSpec other
                && rows.equals(other.rows)
                && ingredients.equals(other.ingredients);
    }

    @Override
    public int hashCode() {
        return Objects.hash(rows, ingredients);
    }

    @Override
    public String toString() {
        return "RecipeSpec{rows=" + rows + ", ingredients=" + ingredients + '}';
    }
}
