/*
 * TNTLibrary - a custom-TNT framework and a set of creative, premium explosives.
 * Copyright (C) 2026 Carmelo Santana
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * See the LICENSE file at the project root for the full license text.
 */
package org.xpfarm.tntlibrary.fbomb;

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
 * The F-Bomb: a vanilla {@link Material#TNT} wearing a custom wither-themed model, crafted from
 * a wither skeleton skull, TNT, and gunpowder. Its signature is a menacing apparition — a boss-bar
 * announced, skull-volleying cinematic — that plays out before the blast itself lands; this class
 * covers the item and the plain fallback blast, and the cinematic is built by later F-Bomb tasks.
 *
 * <p>Like {@code SmartBomb} and {@code WaterBomb}, a "custom item" here is just a vanilla material
 * selected by the {@code minecraft:item_model} data component via {@link
 * ItemMeta#setItemModel(NamespacedKey)}: Paper 26.1 exposes no custom-item registry. The model key
 * {@link #ITEM_MODEL_KEY} ({@code tnt_library:fbomb}) resolves against the bundled resource pack,
 * so the item shows as the 3D F-Bomb cube.
 *
 * <h2>Identity, no attribute modifiers</h2>
 *
 * <p>The built stack is identified <em>only</em> by the {@link Keys#TNT_ID} PDC marker — never by
 * display name or lore, both forgeable in an anvil. No {@code ATTRIBUTE_MODIFIERS} are set: adding
 * even one replaces the vanilla prototype set and breaks item display on Bedrock/Geyser. Do not
 * add one.
 *
 * <h2>Recipe shape</h2>
 *
 * <pre>
 * SGS       S = Material.WITHER_SKELETON_SKULL
 * GTG       G = Material.GUNPOWDER
 * SGS       T = Material.TNT
 * </pre>
 *
 * <p>The wither motif: TNT ringed by gunpowder with a skull at each corner. This shape collides
 * with no vanilla recipe.
 */
public final class FBomb implements CustomTnt {

    /** This bomb's stable string id; matches its {@code BombType} id and {@code bombs.fbomb}
     * config section. Also the value tagged onto the primed entity via {@link Keys#DETONATION_ID}. */
    public static final String ID = "fbomb";

    /** Shipped default fuse length, in ticks — the fallback fuse for the generic detonation path
     * when the director-driven cinematic is not the one driving the timing. */
    public static final int DEFAULT_FUSE_TICKS = 60;

    /**
     * The {@code minecraft:item_model} value: {@code tnt_library:fbomb}. Built with the explicit
     * two-arg {@link NamespacedKey} constructor on purpose — the plugin-arg form would lowercase
     * "TNTLibrary" and drop the underscore, yielding the wrong namespace.
     */
    public static final NamespacedKey ITEM_MODEL_KEY = new NamespacedKey("tnt_library", "fbomb");

    private static final Component DISPLAY_NAME =
            Component.text("F-Bomb", NamedTextColor.DARK_RED).decoration(TextDecoration.ITALIC, false);

    private static final Component LORE_LINE =
            Component.text("Summons a menacing apparition, then detonates.", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false);

    private final int fuseTicks;

    /** Creates an F-Bomb with the shipped default fuse ({@value #DEFAULT_FUSE_TICKS} ticks). */
    public FBomb() {
        this(DEFAULT_FUSE_TICKS);
    }

    /**
     * Creates an F-Bomb with a config-injectable fuse length. The wiring layer feeds this the
     * configured fuse; the director-driven cinematic owns the real menace timing, so this fuse is
     * only the fallback for the generic fuse path.
     */
    public FBomb(int fuseTicks) {
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
     * Builds a fresh F-Bomb {@link ItemStack}. Requires a running Bukkit server ({@link ItemStack}
     * construction and {@link ItemStack#getItemMeta()} both reach into CraftBukkit), so this is
     * verified at the runtime gate rather than in JUnit — mirrors {@code SmartBomb#createItem()}.
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
                List.of("SGS", "GTG", "SGS"),
                Map.of(
                        'S', Material.WITHER_SKELETON_SKULL,
                        'G', Material.GUNPOWDER,
                        'T', Material.TNT));
    }

    @Override
    public int fuseTicks() {
        return fuseTicks;
    }

    /**
     * Detonates the F-Bomb via the shared {@link FBombBlast} using the bomb's configured {@link
     * org.xpfarm.tntlibrary.config.BombSettings#radius()}.
     *
     * <p>This is the <em>fallback</em> path — the generic fuse-elapsed detonation that runs when
     * the director-driven cinematic is not the one in play. The normal way an F-Bomb fires is that
     * cinematic (menace phase, skull volley, boss bar), which calls {@link FBombBlast#detonate}
     * directly once it finishes. Both routes funnel through {@link FBombBlast} so the blast itself
     * is identical either way. A null world (a detached center) is handled by {@link FBombBlast} as
     * a no-op. Server-dependent; verified at the runtime gate.
     *
     * @param ctx the detonation services and location; never {@code null}
     */
    @Override
    public void detonate(DetonationContext ctx) {
        FBombBlast.detonate(ctx.world(), ctx.center(), ctx.settings().radius());
    }
}
