/*
 * TNTLibrary - a custom-TNT framework and a set of creative, premium explosives.
 * Copyright (C) 2026 Carmelo Santana
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * See the LICENSE file at the project root for the full license text.
 */
package org.xpfarm.tntlibrary.twins;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
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
 * The Twins: a two-variant {@link CustomTnt} — one instance per colour ({@link TwinColor#WHITE} /
 * {@link TwinColor#BLACK}) — that pairs with its opposite. A single {@code TheTwins} carries its
 * colour plus the settings injected at construction (fuse, beam thickness, pairing range) and a
 * shared {@link PlacedTwinIndex}, mirroring {@code new WaterBomb(fuseTicks)}: {@link #detonate} never
 * reads {@code ctx.settings()} (the base {@code twins} section is the master-disabled variant
 * source), it reads these injected fields.
 *
 * <p>Like the Water Bomb, a "custom item" here is a vanilla {@link Material#TNT} wearing a custom
 * {@code minecraft:item_model} chosen by colour ({@link #ITEM_MODEL_WHITE} / {@link
 * #ITEM_MODEL_BLACK}), and identity is carried <em>only</em> by the {@link Keys#TNT_ID} PDC marker —
 * never the display name or lore (both forgeable in an anvil). No attribute modifiers are set:
 * adding even one replaces the vanilla prototype set and breaks item display on Bedrock/Geyser.
 *
 * <h2>Pure vs. server-dependent</h2>
 *
 * <p>{@link #id()}, {@link #fuseTicks()}, {@link #recipeSpec()} and the item-model key constants are
 * pure, server-free data and are unit-tested. {@link #createItem()} and {@link #detonate} reach into
 * CraftBukkit and are verified at the runtime gate. All pairing and beam geometry lives in the pure
 * {@link TwinsPlan}; {@code detonate} is only the thin runtime adapter that applies the {@link
 * TwinsOutcome} it returns.
 *
 * <h2>Detonation, in brief</h2>
 *
 * <p>{@code detonate} builds the igniting Twin's {@link TwinLocation} from the block centre, snapshots
 * the world's other placed Twins from the index, and asks {@link TwinsPlan#resolve} for the outcome:
 *
 * <ul>
 *   <li><b>Paired</b>: spawn one tagged, non-incendiary {@link TNTPrimed} per beam sample (yield =
 *       {@link #beamThickness} clamped to [{@value #MIN_POWER}, {@value #MAX_POWER}]), clear the
 *       partner's block to air (the origin block was already cleared by the fuse before detonate ran),
 *       and remove both spent Twins from the index.
 *   <li><b>Fizzle</b>: drop the Twin item back at the centre, puff a little smoke, action-bar the
 *       primer if a player, and remove the (now air) origin from the index.
 * </ul>
 *
 * <h2>Index ownership</h2>
 *
 * <p>{@code detonate} owns the index removal of the spent/fizzled Twins itself — it uniquely knows the
 * partner. The orchestrator wires index <em>add</em> on place and <em>remove</em> on manual break
 * only; it must NOT also remove at the detonation site or the two will double-remove (harmless, but
 * redundant) — removal here is the single source for spent Twins.
 */
public final class TheTwins implements CustomTnt {

    /**
     * The {@code minecraft:item_model} for the White Twin: {@code tnt_library:twins_white}. Built with
     * the explicit two-arg {@link NamespacedKey} constructor on purpose — the plugin-arg form would
     * lowercase "TNTLibrary" and drop the underscore, yielding the wrong namespace (see {@link Keys}).
     */
    public static final NamespacedKey ITEM_MODEL_WHITE = new NamespacedKey("tnt_library", "twins_white");

    /** The {@code minecraft:item_model} for the Black Twin: {@code tnt_library:twins_black}. */
    public static final NamespacedKey ITEM_MODEL_BLACK = new NamespacedKey("tnt_library", "twins_black");

    private static final Component WHITE_NAME =
            Component.text("White Twin", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false);

    private static final Component BLACK_NAME =
            Component.text("Black Twin", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false);

    private static final Component LORE_LINE =
            Component.text("Fires a beam to its nearest opposite Twin.", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false);

    private static final Component NO_PARTNER =
            Component.text("No matching Twin in range.", NamedTextColor.RED);

    /** Lowest per-sample explosion power, so a misconfigured beam thickness still pops. */
    private static final float MIN_POWER = 1.0f;

    /** Upper clamp on per-sample explosion power — keeps each beam node modest. */
    private static final float MAX_POWER = 8.0f;

    private final TwinColor color;
    private final int fuseTicks;
    private final int beamThickness; // from bombs.twins.radius, repurposed as per-sample blast power
    private final double maxPairDistance; // from bombs.twins.max-pair-distance
    private final PlacedTwinIndex index;

    /**
     * Creates one Twin variant.
     *
     * @param color which Twin this instance is; never {@code null}
     * @param fuseTicks fuse length in ticks (injected from {@code bombs.twins.fuse-ticks})
     * @param beamThickness the repurposed {@code bombs.twins.radius}, used as the per-sample explosion
     *     power (clamped to [{@value #MIN_POWER}, {@value #MAX_POWER}])
     * @param maxPairDistance the inclusive pairing range in blocks ({@code bombs.twins.max-pair-distance})
     * @param index the shared, per-world register of placed Twins; never {@code null}
     */
    public TheTwins(TwinColor color, int fuseTicks, int beamThickness,
            double maxPairDistance, PlacedTwinIndex index) {
        this.color = Objects.requireNonNull(color, "color");
        this.fuseTicks = fuseTicks;
        this.beamThickness = beamThickness;
        this.maxPairDistance = maxPairDistance;
        this.index = Objects.requireNonNull(index, "index");
    }

    @Override
    public String id() {
        return color.variantId();
    }

    @Override
    public Component displayName() {
        return color == TwinColor.WHITE ? WHITE_NAME : BLACK_NAME;
    }

    /** The {@code minecraft:item_model} key for this instance's colour. */
    private NamespacedKey itemModelKey() {
        return color == TwinColor.WHITE ? ITEM_MODEL_WHITE : ITEM_MODEL_BLACK;
    }

    /**
     * Builds a fresh Twin {@link ItemStack} for this colour. Requires a running Bukkit server ({@link
     * ItemStack} construction and {@link ItemStack#getItemMeta()} both reach into CraftBukkit), so this
     * is verified at the runtime gate rather than in JUnit — mirrors {@code WaterBomb#createItem()}.
     */
    @Override
    public ItemStack createItem() {
        ItemStack stack = new ItemStack(Material.TNT);
        ItemMeta meta = stack.getItemMeta();

        meta.setItemModel(itemModelKey());
        meta.itemName(displayName());
        meta.lore(List.of(LORE_LINE));

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(Keys.TNT_ID, PersistentDataType.STRING, id());

        stack.setItemMeta(meta);
        return stack;
    }

    @Override
    public RecipeSpec recipeSpec() {
        if (color == TwinColor.WHITE) {
            return new RecipeSpec(
                    List.of("W W", " T ", "W W"),
                    Map.of('W', Material.WHITE_WOOL, 'T', Material.TNT));
        }
        return new RecipeSpec(
                List.of("K K", " T ", "K K"),
                Map.of('K', Material.BLACK_WOOL, 'T', Material.TNT));
    }

    @Override
    public int fuseTicks() {
        return fuseTicks;
    }

    /**
     * Detonates this Twin: resolve the pairing/beam outcome over the index snapshot, then apply it.
     *
     * <p>A null world (a detached centre) is a no-op, like the Water Bomb. On a {@link
     * TwinsOutcome.Paired} outcome the beam is carved as one tagged, non-incendiary {@link TNTPrimed}
     * per {@link Vec3} sample (fuse 0, so it explodes next tick and drives the {@code
     * EntityExplodeEvent} the {@code DetonationListener} filters through WorldGuard/GriefPrevention),
     * the partner's block is cleared to air, and both spent Twins are removed from the index. On {@link
     * TwinsOutcome.Fizzle} the Twin item is dropped back at the centre (the fuse already cleared its
     * block to air), a smoke puff is emitted, the primer — if a player — is action-barred, and the
     * origin is removed from the index. Server-dependent; verified at the runtime gate.
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
        UUID worldId = world.getUID();
        TwinLocation origin =
                new TwinLocation(worldId, center.getBlockX(), center.getBlockY(), center.getBlockZ());

        Collection<PlacedTwin> candidates = index.inWorld(worldId);
        TwinsOutcome outcome = TwinsPlan.resolve(origin, color, candidates, maxPairDistance);

        if (outcome instanceof TwinsOutcome.Paired paired) {
            float power = Math.max(MIN_POWER, Math.min(MAX_POWER, (float) beamThickness));
            for (Vec3 sample : paired.beam()) {
                Location point = new Location(world, sample.x(), sample.y(), sample.z());
                world.spawn(point, TNTPrimed.class, tnt -> {
                    tnt.setYield(power);
                    tnt.setIsIncendiary(false);
                    tnt.setFuseTicks(0); // explode next tick, driving the tagged EntityExplodeEvent
                    tnt.getPersistentDataContainer()
                            .set(Keys.DETONATION_ID, PersistentDataType.STRING, id());
                });
            }
            // The origin block was already cleared to air by the fuse; only the partner remains.
            TwinLocation partnerLoc = paired.partner().location();
            world.getBlockAt(partnerLoc.x(), partnerLoc.y(), partnerLoc.z())
                    .setType(Material.AIR, false);
            index.remove(origin);
            index.remove(partnerLoc);
        } else {
            // Fizzle: no partner in range — restore the Twin item and give quiet feedback.
            world.dropItemNaturally(center, createItem());
            world.spawnParticle(Particle.SMOKE, center.clone().add(0, 0.55, 0),
                    8, 0.2, 0.2, 0.2, 0.0);
            if (ctx.primer() instanceof Player player) {
                player.sendActionBar(NO_PARTNER);
            }
            index.remove(origin); // its block is now air
        }
    }
}
