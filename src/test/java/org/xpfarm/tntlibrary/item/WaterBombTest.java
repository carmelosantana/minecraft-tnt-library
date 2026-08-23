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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import org.xpfarm.tntlibrary.config.BombType;
import org.xpfarm.tntlibrary.core.RecipeSpec;

/**
 * Server-free unit tests for {@link WaterBomb}: its id, fuse, display name, and recipe shape are all
 * pure data. The server-dependent members ({@code createItem()}, PDC round-trips via {@link
 * BombItems}, and live {@code ShapedRecipe} construction via {@link BombRecipes}) are deliberately
 * left to the runtime gate — constructing an {@code ItemStack} or {@code ShapedRecipe} needs a
 * running CraftBukkit server.
 */
final class WaterBombTest {

    @Test
    void idIsWaterbombAndMatchesTheCatalogue() {
        assertEquals("waterbomb", new WaterBomb().id());
        assertEquals(BombType.WATERBOMB.id(), new WaterBomb().id());
    }

    @Test
    void fuseTicksDefaultsToEighty() {
        assertEquals(80, new WaterBomb().fuseTicks());
    }

    @Test
    void fuseTicksIsConstructorInjectable() {
        assertEquals(120, new WaterBomb(120).fuseTicks());
    }

    @Test
    void displayNameContentIsWaterBomb() {
        Component name = new WaterBomb().displayName();
        TextComponent text = assertInstanceOf(TextComponent.class, name);
        assertEquals("Water Bomb", text.content());
    }

    @Test
    void displayNameIsNotItalic() {
        assertEquals(
                TextDecoration.State.FALSE,
                new WaterBomb().displayName().decoration(TextDecoration.ITALIC));
    }

    @Test
    void recipeSpecIsTntSurroundedByWaterBuckets() {
        RecipeSpec spec = new WaterBomb().recipeSpec();

        assertEquals(List.of(" W ", "WTW", " W "), spec.rows());
        assertEquals(
                Map.of('W', Material.WATER_BUCKET, 'T', Material.TNT),
                spec.ingredients());
    }
}
