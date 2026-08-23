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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link TntRegistry}'s id-to-definition lookup and duplicate handling.
 *
 * <p>The {@link FakeTnt} double implements {@link CustomTnt} with a pure, server-free
 * {@link CustomTnt#recipeSpec()} and {@link CustomTnt#displayName()}; its {@link
 * CustomTnt#createItem()} throws, which is never reached here because the registry only stores and
 * returns the definition — it never builds an item. That keeps register/get/ids/duplicate logic
 * genuinely testable with no running server, mirroring redstone-stuff's {@code ItemRegistryTest}.
 */
final class TntRegistryTest {

    /** A minimal, server-free {@link CustomTnt} for exercising the registry. */
    private static final class FakeTnt implements CustomTnt {
        private final String id;

        FakeTnt(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public Component displayName() {
            return Component.text(id);
        }

        @Override
        public ItemStack createItem() {
            throw new UnsupportedOperationException("server-dependent; not exercised in unit tests");
        }

        @Override
        public RecipeSpec recipeSpec() {
            return new RecipeSpec(List.of("T"), Map.of('T', Material.TNT));
        }

        @Override
        public int fuseTicks() {
            return 40;
        }

        @Override
        public void detonate(Location center, Entity primer) {
            // no-op
        }
    }

    @Test
    void registerThenGetReturnsSameInstance() {
        TntRegistry registry = new TntRegistry();
        FakeTnt bomb = new FakeTnt("waterbomb");

        registry.register(bomb);

        assertTrue(registry.get("waterbomb").isPresent());
        assertSame(bomb, registry.get("waterbomb").orElseThrow());
    }

    @Test
    void unknownIdReturnsEmpty() {
        TntRegistry registry = new TntRegistry();
        assertFalse(registry.get("does_not_exist").isPresent());
    }

    @Test
    void nullIdReturnsEmptyRatherThanThrowing() {
        TntRegistry registry = new TntRegistry();
        assertFalse(registry.get(null).isPresent());
    }

    @Test
    void duplicateIdIsRejected() {
        TntRegistry registry = new TntRegistry();
        registry.register(new FakeTnt("waterbomb"));

        assertThrows(IllegalStateException.class,
                () -> registry.register(new FakeTnt("waterbomb")));
    }

    @Test
    void registeringNullIsRejected() {
        TntRegistry registry = new TntRegistry();
        assertThrows(NullPointerException.class, () -> registry.register(null));
    }

    @Test
    void idsListsEveryRegisteredIdAndIsUnmodifiable() {
        TntRegistry registry = new TntRegistry();
        registry.register(new FakeTnt("waterbomb"));
        registry.register(new FakeTnt("smartbomb"));

        assertEquals(2, registry.ids().size());
        assertTrue(registry.ids().contains("waterbomb"));
        assertTrue(registry.ids().contains("smartbomb"));
        assertThrows(UnsupportedOperationException.class, () -> registry.ids().add("nope"));
    }

    @Test
    void allReturnsEveryRegisteredDefinition() {
        TntRegistry registry = new TntRegistry();
        FakeTnt bomb = new FakeTnt("waterbomb");
        registry.register(bomb);

        assertEquals(1, registry.all().size());
        assertTrue(registry.all().contains(bomb));
    }

    @Test
    void isRegisteredReflectsPresence() {
        TntRegistry registry = new TntRegistry();
        assertFalse(registry.isRegistered("waterbomb"));
        registry.register(new FakeTnt("waterbomb"));
        assertTrue(registry.isRegistered("waterbomb"));
        assertFalse(registry.isRegistered(null));
    }
}
