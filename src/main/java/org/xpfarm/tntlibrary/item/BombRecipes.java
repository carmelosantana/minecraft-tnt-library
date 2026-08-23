/*
 * TNTLibrary - a custom-TNT framework and a set of creative, premium explosives.
 * Copyright (C) 2026 Carmelo Santana
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * See the LICENSE file at the project root for the full license text.
 */
package org.xpfarm.tntlibrary.item;

import java.util.Map;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.plugin.Plugin;
import org.xpfarm.tntlibrary.core.CustomTnt;
import org.xpfarm.tntlibrary.core.RecipeSpec;

/**
 * Turns a {@link CustomTnt}'s shape-as-data {@link RecipeSpec} into a live, registered {@link
 * ShapedRecipe}.
 *
 * <p>{@code RecipeSpec} is deliberately server-free so it can be validated in JUnit; this helper is
 * the server-side bridge that the wiring task invokes at {@code onEnable} (never at class-load — a
 * {@code ShapedRecipe} cannot be constructed without a running server). Mirrors {@code
 * redstone-stuff}'s {@code RedstoneSwordRecipe} registration pattern.
 */
public final class BombRecipes {

    private BombRecipes() {}

    /**
     * The recipe key for {@code bomb}: {@code tnt_library:<id>}. Built with the explicit two-arg
     * {@link NamespacedKey} constructor on purpose — the plugin-arg form would lowercase
     * "TNTLibrary" and drop the underscore, giving the wrong namespace.
     */
    public static NamespacedKey key(CustomTnt bomb) {
        return new NamespacedKey("tnt_library", bomb.id());
    }

    /**
     * Builds the live {@link ShapedRecipe} for {@code bomb}: its {@link CustomTnt#createItem()}
     * stack shaped by its {@link RecipeSpec}. Requires a running server (both {@code createItem()}
     * and {@code ShapedRecipe} do), so it is not unit-tested — {@code RecipeSpec} carries the
     * assertable part of the definition.
     */
    public static ShapedRecipe build(CustomTnt bomb) {
        RecipeSpec spec = bomb.recipeSpec();
        ShapedRecipe recipe = new ShapedRecipe(key(bomb), bomb.createItem());
        recipe.shape(spec.rows().toArray(new String[0]));
        for (Map.Entry<Character, org.bukkit.Material> ingredient : spec.ingredients().entrySet()) {
            recipe.setIngredient(ingredient.getKey(), ingredient.getValue());
        }
        return recipe;
    }

    /**
     * Registers {@code bomb}'s recipe with {@code plugin}'s server, safe to call twice: the previous
     * registration under {@link #key(CustomTnt)} is removed first, so a config reload does not throw
     * a duplicate-key error. Uses the two-arg {@code removeRecipe}/{@code addRecipe} overloads with
     * {@code resendRecipes=true} so players already online see the change without relogging.
     */
    public static void register(Plugin plugin, CustomTnt bomb) {
        plugin.getServer().removeRecipe(key(bomb), true);
        plugin.getServer().addRecipe(build(bomb), true);
    }

    /**
     * Removes {@code bomb}'s recipe from {@code plugin}'s server, if present. Safe to call when
     * absent. Uses the two-arg {@code removeRecipe} overload with {@code resendRecipes=true} for the
     * same reason {@link #register(Plugin, CustomTnt)} does.
     */
    public static void unregister(Plugin plugin, CustomTnt bomb) {
        plugin.getServer().removeRecipe(key(bomb), true);
    }
}
