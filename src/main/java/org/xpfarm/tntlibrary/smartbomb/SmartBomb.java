/*
 * TNTLibrary - a custom-TNT framework and a set of creative, premium explosives.
 * Copyright (C) 2026 Carmelo Santana
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * See the LICENSE file at the project root for the full license text.
 */
package org.xpfarm.tntlibrary.smartbomb;

import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.xpfarm.tntlibrary.core.CustomTnt;
import org.xpfarm.tntlibrary.core.Keys;
import org.xpfarm.tntlibrary.core.RecipeSpec;
import org.xpfarm.tntlibrary.detonation.DetonationContext;

/**
 * The Smart Bomb: a vanilla {@link Material#TNT} wearing a custom command-block-themed model, crafted
 * from TNT wrapped in redstone. Unlike the other bombs, its interest is in <em>when</em> it fires — a
 * player programs a delay, a time-of-day, or a proximity trigger per placed block (persisted by the
 * Smart Bomb store), and a watcher (Task 5) detonates it at the programmed moment.
 *
 * <p>Like {@code WaterBomb}, a "custom item" here is just a vanilla material selected by the {@code
 * minecraft:item_model} data component via {@link ItemMeta#setItemModel(NamespacedKey)}: Paper 26.1
 * exposes no custom-item registry. The model key {@link #ITEM_MODEL_KEY} ({@code tnt_library:smartbomb})
 * resolves against the bundled resource pack, so the item shows as the 3D Smart Bomb cube.
 *
 * <h2>Identity, no attribute modifiers</h2>
 *
 * <p>The built stack is identified <em>only</em> by the {@link Keys#TNT_ID} PDC marker — never by
 * display name or lore, both forgeable in an anvil. No {@code ATTRIBUTE_MODIFIERS} are set: adding even
 * one replaces the vanilla prototype set and breaks item display on Bedrock/Geyser. Do not add one.
 *
 * <h2>Recipe shape</h2>
 *
 * <pre>
 * RRR       R = Material.REDSTONE (dust)
 * RTR       T = Material.TNT
 * RBR       B = Material.REDSTONE_BLOCK
 * </pre>
 *
 * <p>The command-block motif: TNT ringed by redstone with a redstone block below. This shape collides
 * with no vanilla recipe.
 */
public final class SmartBomb implements CustomTnt {

    /** This bomb's stable string id; matches its {@code BombType} id and {@code bombs.smartbomb}
     * config section. Also the value tagged onto the primed entity via {@link Keys#DETONATION_ID}. */
    public static final String ID = "smartbomb";

    /** Shipped default fuse length, in ticks — the fallback fuse for the generic detonation path when
     * no per-block trigger drives the timing. Mirrors {@code bombs.smartbomb.default-delay-ticks}. */
    public static final int DEFAULT_FUSE_TICKS = 100;

    /**
     * The {@code minecraft:item_model} value: {@code tnt_library:smartbomb}. Built with the explicit
     * two-arg {@link NamespacedKey} constructor on purpose — the plugin-arg form would lowercase
     * "TNTLibrary" and drop the underscore, yielding the wrong namespace.
     */
    public static final NamespacedKey ITEM_MODEL_KEY = new NamespacedKey("tnt_library", "smartbomb");

    private static final Component DISPLAY_NAME =
            Component.text("Smart Bomb", NamedTextColor.LIGHT_PURPLE)
                    .decoration(TextDecoration.ITALIC, false);

    private static final Component LORE_LINE =
            Component.text("Programmable TNT: delay, time-of-day, or proximity.", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false);

    private final int fuseTicks;

    /** Creates a Smart Bomb with the shipped default fuse ({@value #DEFAULT_FUSE_TICKS} ticks). */
    public SmartBomb() {
        this(DEFAULT_FUSE_TICKS);
    }

    /**
     * Creates a Smart Bomb with a config-injectable fuse length. The wiring layer feeds this the
     * {@code default-delay-ticks} config value; the watcher owns real trigger timing, so this fuse is
     * only the fallback for the generic fuse path.
     */
    public SmartBomb(int fuseTicks) {
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
     * Builds a fresh Smart Bomb {@link ItemStack}. Requires a running Bukkit server ({@link ItemStack}
     * construction and {@link ItemStack#getItemMeta()} both reach into CraftBukkit), so this is
     * verified at the runtime gate rather than in JUnit — mirrors {@code WaterBomb#createItem()}.
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
                List.of("RRR", "RTR", "RBR"),
                Map.of('R', Material.REDSTONE, 'T', Material.TNT, 'B', Material.REDSTONE_BLOCK));
    }

    @Override
    public int fuseTicks() {
        return fuseTicks;
    }

    /**
     * Detonates the Smart Bomb via the shared {@link SmartBombBlast} using the bomb's configured {@link
     * org.xpfarm.tntlibrary.config.BombSettings#radius()}.
     *
     * <p>This is the <em>fallback</em> path — the generic fuse-elapsed detonation that runs when no
     * per-block trigger is in play. The Task-5 watcher, which is the normal way a Smart Bomb fires,
     * calls {@link SmartBombBlast#detonate} directly with the per-block <em>programmed</em> radius. Both
     * routes funnel through {@link SmartBombBlast} so the blast is identical either way. A null world (a
     * detached center) is handled by {@link SmartBombBlast} as a no-op. Server-dependent; verified at
     * the runtime gate.
     *
     * @param ctx the detonation services and location; never {@code null}
     */
    @Override
    public void detonate(DetonationContext ctx) {
        SmartBombBlast.detonate(ctx.world(), ctx.center(), ctx.settings().radius());
    }
}
