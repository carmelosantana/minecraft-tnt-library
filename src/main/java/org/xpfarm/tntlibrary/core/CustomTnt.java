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

import net.kyori.adventure.text.Component;
import org.bukkit.inventory.ItemStack;
import org.xpfarm.tntlibrary.detonation.DetonationContext;

/**
 * The contract every bomb in the library implements.
 *
 * <p>A {@code CustomTnt} is a lightweight definition, not an entity: its identity ({@link #id()}),
 * its recipe shape ({@link #recipeSpec()}) and its {@link #fuseTicks()} are pure, server-free data,
 * which is what lets {@link TntRegistry} be unit-tested. Only the two Bukkit-touching members —
 * {@link #createItem()} and {@link #detonate(DetonationContext)} — need a running server, and those
 * are verified at the runtime gate rather than in JUnit.
 */
public interface CustomTnt {

    /**
     * This bomb's stable, lowercase, underscore-free string id (e.g. {@code "waterbomb"}) — one of
     * the ids in the bomb catalogue ({@code org.xpfarm.tntlibrary.config.BombType}). It is written
     * into {@link Keys#TNT_ID} on the item and is the key it registers under in {@link TntRegistry}.
     * Never changes once shipped — it is persisted on real items.
     */
    String id();

    /**
     * The player-facing display name, as an Adventure {@link Component}. Cosmetic only: display
     * name is never used to identify a stack (a player can rename items in an anvil) — {@link
     * Keys#TNT_ID} is the only identity.
     */
    Component displayName();

    /**
     * Builds a fresh item stack for this bomb. Requires a running Bukkit server (stack construction
     * and {@code ItemMeta} both reach into CraftBukkit), so this is not exercised by unit tests —
     * mirror {@code redstone-stuff}'s {@code RedstoneSword#build()}.
     */
    ItemStack createItem();

    /**
     * This bomb's crafting recipe as {@linkplain RecipeSpec shape-as-data}. The {@code item} layer
     * turns it into a live {@code ShapedRecipe} once a server is running; expressed as data so the
     * recipe is validatable without one.
     */
    RecipeSpec recipeSpec();

    /**
     * How long the fuse burns, in server ticks (20 ticks = 1 second), between priming and {@link
     * #detonate(DetonationContext)}.
     */
    int fuseTicks();

    /**
     * The bomb's effect, fired when its fuse expires.
     *
     * <p>The {@link DetonationContext} carries every service the effect needs — where and who primed
     * it, the owning {@link org.bukkit.plugin.Plugin} for scheduling, this bomb's tuned {@link
     * org.xpfarm.tntlibrary.config.BombSettings}, and the {@link
     * org.xpfarm.tntlibrary.protect.ProtectionService} it must respect before changing the world.
     * Passing a context rather than a fixed {@code (Location, Entity)} pair lets new services be added
     * without changing this signature again. It is built and invoked by {@code
     * org.xpfarm.tntlibrary.detonation.Detonator} from the rig's fuse-elapsed callback.
     *
     * <p>The default is a deliberate no-op so a bomb can be registered and crafted before its effect
     * is written.
     *
     * @param ctx the detonation services and location for this blast; never {@code null}
     */
    default void detonate(DetonationContext ctx) {
        // Default no-op: effect is supplied per-bomb; the detonation layer builds and passes the ctx.
    }
}
