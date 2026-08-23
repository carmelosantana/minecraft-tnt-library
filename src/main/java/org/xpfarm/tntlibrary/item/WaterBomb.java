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

import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.xpfarm.tntlibrary.core.CustomTnt;
import org.xpfarm.tntlibrary.core.Keys;
import org.xpfarm.tntlibrary.core.RecipeSpec;
import org.xpfarm.tntlibrary.detonation.DetonationContext;

/**
 * The Water Bomb: a vanilla {@link Material#TNT} wearing a custom water-themed model, crafted from
 * TNT surrounded by water buckets.
 *
 * <p>Like {@code redstone-stuff}'s {@code RedstoneSword}, a "custom item" here is just a vanilla
 * material selected by the {@code minecraft:item_model} data component via {@link
 * ItemMeta#setItemModel(NamespacedKey)}: Paper 26.1 exposes no custom-item registry. The model key
 * {@link #ITEM_MODEL_KEY} ({@code tnt_library:waterbomb}) resolves against a resource pack whose
 * texture lands in a later task; only the key needs to be correct now.
 *
 * <h2>Identity, no attribute modifiers</h2>
 *
 * <p>The built stack is identified <em>only</em> by the {@link Keys#TNT_ID} PDC marker (read back by
 * {@link BombItems#idOf(ItemStack)}) — never by display name or lore, both forgeable in an anvil.
 * No {@code ATTRIBUTE_MODIFIERS} are set: adding even one replaces the vanilla prototype set and
 * breaks item display on Bedrock/Geyser. Do not add one.
 *
 * <h2>Recipe shape</h2>
 *
 * <pre>
 *  W        W = Material.WATER_BUCKET
 * WTW       T = Material.TNT
 *  W
 * </pre>
 *
 * <p>TNT in the centre, four water buckets in a plus around it. This shape collides with no vanilla
 * recipe.
 */
public final class WaterBomb implements CustomTnt {

    /** This bomb's stable string id; matches {@code BombType.WATERBOMB.id()} and the {@code
     * bombs.waterbomb} config section. */
    public static final String ID = "waterbomb";

    /** Shipped default fuse length, in ticks — mirrors {@code bombs.waterbomb.fuse-ticks}. */
    public static final int DEFAULT_FUSE_TICKS = 80;

    /**
     * The {@code minecraft:item_model} value: {@code tnt_library:waterbomb}. Built with the explicit
     * two-arg {@link NamespacedKey} constructor on purpose — the plugin-arg form would lowercase
     * "TNTLibrary" and drop the underscore, yielding the wrong namespace.
     */
    public static final NamespacedKey ITEM_MODEL_KEY = new NamespacedKey("tnt_library", "waterbomb");

    private static final Component DISPLAY_NAME =
            Component.text("Water Bomb", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false);

    private static final Component LORE_LINE =
            Component.text("Douses fire and washes away the blast.", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false);

    private final int fuseTicks;

    /** Creates a Water Bomb with the shipped default fuse ({@value #DEFAULT_FUSE_TICKS} ticks). */
    public WaterBomb() {
        this(DEFAULT_FUSE_TICKS);
    }

    /**
     * Creates a Water Bomb with a config-injectable fuse length. Phase 1 wiring uses the no-arg
     * constructor; this overload exists so the fuse can later be fed from {@code
     * TntLibraryConfig}.
     */
    public WaterBomb(int fuseTicks) {
        this.fuseTicks = fuseTicks;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Component displayName() {
        return DISPLAY_NAME;
    }

    /**
     * Builds a fresh Water Bomb {@link ItemStack}. Requires a running Bukkit server ({@link
     * ItemStack} construction and {@link ItemStack#getItemMeta()} both reach into CraftBukkit), so
     * this is verified at the runtime gate rather than in JUnit — mirrors {@code
     * RedstoneSword#build()}.
     */
    @Override
    public ItemStack createItem() {
        ItemStack stack = new ItemStack(Material.TNT);
        ItemMeta meta = stack.getItemMeta();

        meta.setItemModel(ITEM_MODEL_KEY);
        meta.itemName(DISPLAY_NAME);
        meta.lore(List.of(LORE_LINE));

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(Keys.TNT_ID, PersistentDataType.STRING, ID);

        stack.setItemMeta(meta);
        return stack;
    }

    @Override
    public RecipeSpec recipeSpec() {
        return new RecipeSpec(
                List.of(" W ", "WTW", " W "),
                Map.of('W', Material.WATER_BUCKET, 'T', Material.TNT));
    }

    @Override
    public int fuseTicks() {
        return fuseTicks;
    }

    /** Lowest explosion power the Water Bomb ever uses, so a misconfigured {@code radius} still pops. */
    private static final float MIN_POWER = 1.0f;

    /** Upper clamp on explosion power — keeps the blast modest even if {@code radius} is large. */
    private static final float MAX_POWER = 8.0f;

    /**
     * Detonates the Water Bomb: spawn a real, tagged explosion whose crater is then flooded to the
     * rim by {@link org.xpfarm.tntlibrary.detonation.DetonationListener}.
     *
     * <p>This method performs only step one — the blast. It spawns a genuine {@link TNTPrimed} at the
     * detonation centre with a zero fuse (it explodes on the next tick) and tags it in its PDC with
     * {@link Keys#DETONATION_ID} = {@link #ID}. Spawning a real entity is deliberate: WorldGuard and
     * GriefPrevention filter its {@link org.bukkit.event.entity.EntityExplodeEvent} block list, so
     * protected terrain is removed from the crater before this plugin ever sees it, and the listener
     * that reads the tag both recognises the explosion as ours and knows to run the water fill. The
     * crater capture, rim math, and permanent water placement all happen in that listener one tick
     * later; nothing further is scheduled here.
     *
     * <p>The explosion is not incendiary (a Water Bomb starts no fires) and its power is derived from
     * the configured {@link org.xpfarm.tntlibrary.config.BombSettings#radius()}, clamped to
     * [{@value #MIN_POWER}, {@value #MAX_POWER}] to stay modest. A null world (a detached center) is a
     * no-op. This is server-dependent and verified at the runtime gate.
     *
     * @param ctx the detonation services and location; never {@code null}
     */
    @Override
    public void detonate(DetonationContext ctx) {
        World world = ctx.world();
        if (world == null) {
            return; // detached location; nothing to detonate against
        }
        Location center = ctx.center();
        float power = Math.max(MIN_POWER, Math.min(MAX_POWER, ctx.settings().radius()));

        world.spawn(center, TNTPrimed.class, tnt -> {
            tnt.setYield(power);
            tnt.setIsIncendiary(false);
            tnt.setFuseTicks(0); // explode on the next tick, driving the EntityExplodeEvent
            tnt.getPersistentDataContainer()
                    .set(Keys.DETONATION_ID, PersistentDataType.STRING, ID);
        });
    }
}
