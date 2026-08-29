/*
 * TNTLibrary - a custom-TNT framework and a set of creative, premium explosives.
 * Copyright (C) 2026 Carmelo Santana
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * See the LICENSE file at the project root for the full license text.
 */
package org.xpfarm.tntlibrary.whiteout;

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
 * The White Out: a vanilla {@link Material#TNT} wearing a custom icy model that, instead of cratering,
 * sucks every living entity inward, freezes them solid with a guaranteed server-side FREEZE finisher at
 * the collapse, and whites the ground over in a permanent ring-swept white-concrete scar.
 *
 * <h2>Identity, no attribute modifiers</h2>
 *
 * <p>The built stack is identified <em>only</em> by the {@link Keys#TNT_ID} PDC marker — never by
 * display name or lore, both forgeable in an anvil. No {@code ATTRIBUTE_MODIFIERS} are set: adding even
 * one breaks item display on Bedrock/Geyser (see the {@code GBomb}/{@code SmartBomb} note). Do not add
 * one.
 *
 * <h2>Effect shape (G-Bomb divergence model)</h2>
 *
 * <p>{@link #detonate(DetonationContext)} delegates to {@link WhiteoutRuntime#detonate(DetonationContext)}.
 * The shared fuse -> {@code Detonator} -> {@code detonate(ctx)} path already reaches it, so there is no
 * {@code IgnitionListener} divert. There is deliberately no {@code TNTPrimed} blast and no crater. A
 * null {@link DetonationContext#world()} or a null runtime is a no-op.
 *
 * <h2>Recipe shape (proposed — the orchestrator finalizes it)</h2>
 *
 * <pre>
 * SPS   S = Material.SNOW_BLOCK
 * PTP   P = Material.PACKED_ICE
 * SPS   T = Material.TNT (centre)
 * </pre>
 *
 * <p>An icy motif that collides with no vanilla recipe and no other bomb. <strong>Note:</strong> recipe
 * registration is owned by the orchestrator at wiring time; it may deconflict or finalize this shape.
 *
 * <p>{@link #createItem()} and {@link #detonate(DetonationContext)} both reach into a running server and
 * are verified at the runtime gate (gate 12), not in JUnit.
 */
public final class WhiteOut implements CustomTnt {

    /** This bomb's stable string id; matches its {@code BombType} id and {@code bombs.whiteout} config
     * section (note=24). Never changes once shipped — it is persisted on real items via
     * {@link Keys#TNT_ID}. */
    public static final String ID = "whiteout";

    /** Shipped default fuse length, in ticks — the delay between priming and {@link #detonate}. */
    public static final int DEFAULT_FUSE_TICKS = 100;

    /**
     * The {@code minecraft:item_model} value: {@code tnt_library:whiteout}. Built with the explicit
     * two-arg {@link NamespacedKey} constructor on purpose — the plugin-arg form would lowercase
     * "TNTLibrary" and drop the underscore (see {@code Keys}).
     */
    public static final NamespacedKey ITEM_MODEL_KEY = new NamespacedKey("tnt_library", "whiteout");

    private static final Component DISPLAY_NAME =
            Component.text("White Out", NamedTextColor.WHITE)
                    .decoration(TextDecoration.ITALIC, false);

    private static final Component LORE_LINE =
            Component.text("Whiteout vortex: implode, freeze, white over.", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false);

    private final WhiteoutRuntime runtime;
    private final int fuseTicks;

    /**
     * Convenience constructor for the registry-shape checks the orchestrator may run — those touch only
     * {@link #id()}, {@link #recipeSpec()}, {@link #fuseTicks()}. The {@link #detonate(DetonationContext)}
     * path is inert without an injected runtime.
     */
    public WhiteOut() {
        this(null, DEFAULT_FUSE_TICKS);
    }

    /** Creates a White Out bound to {@code runtime} with the shipped default fuse. */
    public WhiteOut(WhiteoutRuntime runtime) {
        this(runtime, DEFAULT_FUSE_TICKS);
    }

    /** Creates a White Out bound to the shared {@code runtime} with a config-injectable fuse length. */
    public WhiteOut(WhiteoutRuntime runtime, int fuseTicks) {
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
                List.of("SPS", "PTP", "SPS"),
                Map.of('S', Material.SNOW_BLOCK, 'P', Material.PACKED_ICE, 'T', Material.TNT));
    }

    @Override
    public int fuseTicks() {
        return fuseTicks;
    }

    /**
     * Detonates the White Out by handing off to {@link WhiteoutRuntime#detonate(DetonationContext)}:
     * pull -> collapse + FREEZE finisher -> scar sweep, with no blast and no crater. A null
     * {@link DetonationContext#world()} is a no-op; a null runtime (the registry-shape convenience
     * constructor) is likewise inert. Server-dependent; verified at the runtime gate.
     */
    @Override
    public void detonate(DetonationContext ctx) {
        if (ctx.world() == null || runtime == null) {
            return;
        }
        runtime.detonate(ctx);
    }
}
