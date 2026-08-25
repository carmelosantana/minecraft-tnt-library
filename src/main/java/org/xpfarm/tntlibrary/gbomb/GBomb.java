/*
 * TNTLibrary - a custom-TNT framework and a set of creative, premium explosives.
 * Copyright (C) 2026 Carmelo Santana
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * See the LICENSE file at the project root for the full license text.
 */
package org.xpfarm.tntlibrary.gbomb;

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
 * The G-Bomb: a vanilla {@link Material#TNT} wearing a custom gravity-themed model that, instead of
 * cratering, launches every living entity in range skyward, holds them at apex with gravity disabled,
 * then slams them down with a guaranteed server-side FALL finisher.
 *
 * <p>Like the other bombs, a "custom item" here is just a vanilla material selected by the
 * {@code minecraft:item_model} data component via {@link ItemMeta#setItemModel(NamespacedKey)}: Paper
 * 26.1 exposes no custom-item registry. The model key {@link #ITEM_MODEL_KEY}
 * ({@code tnt_library:gbomb}) resolves against the bundled resource pack.
 *
 * <h2>Identity, no attribute modifiers</h2>
 *
 * <p>The built stack is identified <em>only</em> by the {@link Keys#TNT_ID} PDC marker — never by
 * display name or lore, both forgeable in an anvil. No {@code ATTRIBUTE_MODIFIERS} are set: adding even
 * one replaces the vanilla prototype set and breaks item display on Bedrock/Geyser (see the
 * {@code SmartBomb} note). Do not add one.
 *
 * <h2>Effect shape</h2>
 *
 * <p>{@link #detonate(DetonationContext)} delegates to {@link GBombRuntime#launch(DetonationContext)},
 * which runs launch → hang → slam + FALL finisher. There is deliberately no {@code TNTPrimed} blast and
 * no crater. A null {@link DetonationContext#world()} is a no-op.
 *
 * <h2>Recipe shape (proposed — the orchestrator finalizes it)</h2>
 *
 * <pre>
 * FTF       F = Material.FEATHER        (float)
 * TNT       T = Material.TNT
 * FTF       N = Material.NETHERITE_INGOT (slam, centre)
 * </pre>
 *
 * <p>Feathers for the float, a netherite ingot at the core for the slam. This shape collides with no
 * vanilla recipe. <strong>Note:</strong> recipe registration is owned by the orchestrator at wiring
 * time; it may deconflict or finalize this shape.
 *
 * <p>{@link #createItem()} and {@link #detonate(DetonationContext)} both reach into a running server and
 * are verified at the runtime gate (gate 12), not in JUnit.
 */
public final class GBomb implements CustomTnt {

    /** This bomb's stable string id; matches its {@code BombType} id and {@code bombs.gbomb} config
     * section. Never changes once shipped — it is persisted on real items via {@link Keys#TNT_ID}. */
    public static final String ID = "gbomb";

    /** Shipped default fuse length, in ticks — the delay between priming and {@link #detonate}. */
    public static final int DEFAULT_FUSE_TICKS = 60;

    /**
     * The {@code minecraft:item_model} value: {@code tnt_library:gbomb}. Built with the explicit
     * two-arg {@link NamespacedKey} constructor on purpose — the plugin-arg form would lowercase
     * "TNTLibrary" and drop the underscore, yielding the wrong namespace (see {@code Keys}).
     */
    public static final NamespacedKey ITEM_MODEL_KEY = new NamespacedKey("tnt_library", "gbomb");

    private static final Component DISPLAY_NAME =
            Component.text("G-Bomb", NamedTextColor.DARK_AQUA)
                    .decoration(TextDecoration.ITALIC, false);

    private static final Component LORE_LINE =
            Component.text("Anti-gravity TNT: launch, hang, then slam.", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false);

    private final GBombRuntime runtime;
    private final int fuseTicks;

    /**
     * Convenience constructor for the registry-shape checks the orchestrator may run — those touch only
     * {@link #id()}, {@link #recipeSpec()}, {@link #fuseTicks()}, none of which need a runtime. The
     * {@link #detonate(DetonationContext)} path is inert without an injected runtime.
     */
    public GBomb() {
        this(null, DEFAULT_FUSE_TICKS);
    }

    /** Creates a G-Bomb bound to {@code runtime} with the shipped default fuse. */
    public GBomb(GBombRuntime runtime) {
        this(runtime, DEFAULT_FUSE_TICKS);
    }

    /**
     * Creates a G-Bomb bound to the shared {@code runtime} with a config-injectable fuse length. The
     * runtime carries the gravity ledger, active tasks, and clamped params shared across the feature.
     */
    public GBomb(GBombRuntime runtime, int fuseTicks) {
        this.runtime = runtime;
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
     * Builds a fresh G-Bomb {@link ItemStack}. Requires a running Bukkit server ({@link ItemStack}
     * construction and {@link ItemStack#getItemMeta()} both reach into CraftBukkit), so this is verified
     * at the runtime gate rather than in JUnit — mirrors {@code SmartBomb#createItem()}.
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
                List.of("FTF", "TNT", "FTF"),
                Map.of('F', Material.FEATHER, 'T', Material.TNT, 'N', Material.NETHERITE_INGOT));
    }

    @Override
    public int fuseTicks() {
        return fuseTicks;
    }

    /**
     * Detonates the G-Bomb by handing off to {@link GBombRuntime#launch(DetonationContext)}: launch →
     * hang → slam + FALL finisher, with no blast and no crater. A null {@link DetonationContext#world()}
     * is a no-op (mirroring {@code smartbomb/SmartBombBlast}); a null runtime (the registry-shape
     * convenience constructor) is likewise inert. Server-dependent; verified at the runtime gate.
     *
     * @param ctx the detonation services and location; never {@code null}
     */
    @Override
    public void detonate(DetonationContext ctx) {
        if (ctx.world() == null || runtime == null) {
            return;
        }
        runtime.launch(ctx);
    }
}
