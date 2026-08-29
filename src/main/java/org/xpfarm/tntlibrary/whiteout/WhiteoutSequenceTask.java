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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.xpfarm.tntlibrary.block.BombBlocks;
import org.xpfarm.tntlibrary.detonation.DetonationContext;

/**
 * One bounded per-detonation task that drives a single White Out through pull -> collapse -> sweep ->
 * done. It holds the mutable elapsed-tick counter, the sourced detonation context (for the protection
 * seam), the caught-entity ids, and the sweep cursor; every decision (which phase a tick is in, the
 * pull velocity, the in-radius test, the skinnable-surface predicate, the scar geometry, the clamped
 * finisher damage) comes from the pure, unit-tested cores.
 *
 * <h2>No-leaked-state gate</h2>
 *
 * <p>Each caught entity's id is recorded in the shared {@link EffectLedger} on first catch, and its
 * effects are cleared on any teardown. {@link #cancelAndClear()} clears every still-tracked caught id
 * before cancelling (plugin disable). If the source block is no longer a White Out before the sequence
 * commits (broken mid-fuse), the task aborts silently without applying anything.
 *
 * <h2>Threading</h2>
 *
 * <p>Main-thread-only; run by the Bukkit scheduler. Server-dependent; verified at the runtime gate.
 */
public final class WhiteoutSequenceTask extends BukkitRunnable {

    /**
     * Sets the sweep DURATION: {@code sweepTicks = ceil(ringCount / RINGS_PER_TICK)} SWEEP ticks (see
     * {@code WhiteoutRuntime#detonate}). {@link #doSweep} then spreads the columns evenly across those
     * ticks. Larger → fewer, heavier ticks; smaller → more, lighter ticks. 4 gives ~6 ticks at radius 24.
     */
    public static final int RINGS_PER_TICK = 4;

    /** Blindness amplifier (level I = amplifier 0). */
    private static final int BLINDNESS_AMPLIFIER = 0;
    /** Slowness amplifier (level V = amplifier 4) — near-immobile in the vortex. */
    private static final int SLOWNESS_AMPLIFIER = 4;
    /** Freeze ticks applied on first catch (140 = the full powder-snow shiver window). */
    private static final int FREEZE_TICKS_BUMP = 140;
    /** White particle count emitted per pull tick. */
    private static final int PARTICLE_COUNT = 20;
    /** The permanent scar block. */
    private static final Material SCAR_BLOCK = Material.WHITE_CONCRETE;

    private final WhiteoutRuntime runtime;
    private final DetonationContext ctx;
    private final World world;
    private final Location center;
    private final WhiteoutZone zone;
    private final WhiteoutParams params;
    private final int[][] columns;
    private final int sweepTicks;
    private final EffectLedger ledger;

    /** Exactly the entities THIS task caught, so it clears its own on teardown. */
    private final List<UUID> caught = new ArrayList<>();

    /** How many scar columns have been converted so far (the sweep cursor). */
    private int sweepCursor;

    /** Ticks since scheduling; drives {@link WhiteoutSequence#phaseAt}. */
    private long elapsed;

    /** Guards against a double terminal/cancel. */
    private boolean terminated;

    WhiteoutSequenceTask(WhiteoutRuntime runtime, DetonationContext ctx, World world, Location center,
            WhiteoutZone zone, WhiteoutParams params, int[][] columns, int sweepTicks,
            EffectLedger ledger) {
        this.runtime = runtime;
        this.ctx = ctx;
        this.world = world;
        this.center = center;
        this.zone = zone;
        this.params = params;
        this.columns = columns;
        this.sweepTicks = sweepTicks;
        this.ledger = ledger;
    }

    @Override
    public void run() {
        if (terminated) {
            return;
        }
        WhiteoutPhase phase = WhiteoutSequence.phaseAt(elapsed, params.pullTicks(), sweepTicks);
        switch (phase) {
            case PULL -> doPull();
            case COLLAPSE -> doCollapse();
            case SWEEP -> doSweep();
            case DONE -> {
                teardown();
                return;
            }
        }
        elapsed++;
    }

    /** PULL: draw every in-radius, non-exempt living entity inward and apply the storm debuffs. */
    private void doPull() {
        int r = params.radius();
        for (LivingEntity e : world.getNearbyLivingEntities(center, r, r, r)) {
            try {
                if (!e.isValid() || !zone.covers(e.getX(), e.getY(), e.getZ())) {
                    continue;
                }
                if (isExempt(e) || !ctx.protection().canBreak(e.getLocation())) {
                    continue;
                }
                Vec3 from = new Vec3(e.getX(), e.getY(), e.getZ());
                Vec3 to = new Vec3(center.getX(), center.getY(), center.getZ());
                Vec3 v = PullCurve.pullVelocity(from, to, r, params.pullPower());
                e.setVelocity(new Vector(v.x(), v.y(), v.z()));
                if (ledger.record(e.getUniqueId())) {
                    caught.add(e.getUniqueId());
                    applyStorm(e);
                }
            } catch (RuntimeException ex) {
                Bukkit.getLogger().warning("[WhiteOut] pull failed for " + e.getUniqueId() + ": " + ex);
            }
        }
        spawnParticles();
    }

    /** COLLAPSE: teleport stragglers outside the core to the center, then FREEZE-finish all caught. */
    private void doCollapse() {
        for (UUID id : caught) {
            try {
                Entity e = Bukkit.getEntity(id);
                if (!(e instanceof LivingEntity le) || !le.isValid()) {
                    continue;
                }
                if (le.getLocation().distance(center) > PullCurve.CORE_RADIUS) {
                    le.teleport(center);
                }
                if (isExempt(le)) {
                    continue;
                }
                FreezeFinisher.finish(le, center, params.killDamage());
            } catch (RuntimeException ex) {
                Bukkit.getLogger().warning("[WhiteOut] collapse failed for " + id + ": " + ex);
            }
        }
    }

    /**
     * SWEEP: convert the next EVEN slice of scar columns to white concrete, per the skip-list.
     *
     * <p>The columns are converted in center-out order across exactly {@link #sweepTicks} SWEEP ticks,
     * {@code ceil(columns.length / sweepTicks)} per tick. This is deliberate on two counts the owner
     * settled: (1) it caps each tick's {@code setBlockData} count (~{@code columns.length/sweepTicks},
     * e.g. ~300 at radius 24) so there is no single-tick hitch — the whole reason the transform is
     * spread rather than done at once; (2) an even center-out slice per tick makes the scar visibly
     * sweep outward. Do NOT front-load the batch — converting most of the disk in the first tick
     * reintroduces the hitch and defeats the sweep. {@code ceil} guarantees the cursor reaches
     * {@code columns.length} by the final SWEEP tick, so no outer ring is ever left unconverted.
     */
    private void doSweep() {
        int perTick = Math.max(1, (int) Math.ceil(columns.length / (double) Math.max(1, sweepTicks)));
        int end = Math.min(columns.length, sweepCursor + perTick);
        for (; sweepCursor < end; sweepCursor++) {
            try {
                convertColumn(columns[sweepCursor][0], columns[sweepCursor][1]);
            } catch (RuntimeException ex) {
                Bukkit.getLogger().warning("[WhiteOut] scar column failed: " + ex);
            }
        }
    }

    /**
     * Converts the topmost skinnable surface block of one column to white concrete, obeying the
     * skip-list: only if {@code ctx.protection().canPlace(loc)}, the block is not a TNT Library bomb
     * block, not a {@link TileState}, and not unbreakable/special.
     */
    private void convertColumn(int dx, int dz) {
        int wx = center.getBlockX() + dx;
        int wz = center.getBlockZ() + dz;
        int topY = world.getHighestBlockYAt(wx, wz);
        for (int y = topY; y >= world.getMinHeight(); y--) {
            Block block = world.getBlockAt(wx, y, wz);
            Material type = block.getType();
            if (!SurfacePredicate.isSkinTarget(type)) {
                continue; // scan down past air/leaves/snow/fluids/plants
            }
            // Found the topmost full solid cube; apply the skip-list before converting.
            if (!ctx.protection().canPlace(block.getLocation())) {
                return;
            }
            if (BombBlocks.bombIdOf(block).isPresent()) {
                return; // never overwrite a live bomb block
            }
            if (block.getState() instanceof TileState) {
                return; // never delete a container/tile entity + its contents
            }
            if (SurfacePredicate.isUnbreakableSpecial(type)) {
                return; // bedrock/barrier/command/end-portal/etc.
            }
            block.setType(SCAR_BLOCK, false);
            return;
        }
    }

    /** True for a creative/spectator player or an invulnerable entity — never pulled, frozen, killed. */
    private boolean isExempt(LivingEntity e) {
        if (e.isInvulnerable()) {
            return true;
        }
        if (e instanceof Player p) {
            return p.getGameMode() == org.bukkit.GameMode.CREATIVE
                    || p.getGameMode() == org.bukkit.GameMode.SPECTATOR;
        }
        return false;
    }

    /** Applies blindness + slowness + freeze on first catch. */
    private void applyStorm(LivingEntity e) {
        int t = params.effectTicks();
        e.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, t, BLINDNESS_AMPLIFIER));
        e.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, t, SLOWNESS_AMPLIFIER));
        e.setFreezeTicks(FREEZE_TICKS_BUMP);
    }

    /** Emits the decorative white particle cloud (best-effort on Bedrock). */
    private void spawnParticles() {
        world.spawnParticle(org.bukkit.Particle.SNOWFLAKE, center, PARTICLE_COUNT,
                params.radius() * 0.25, params.radius() * 0.25, params.radius() * 0.25, 0.01);
    }

    /** DONE / normal teardown: clear remaining effects, cancel, and deregister. Guards double-teardown. */
    private void teardown() {
        if (terminated) {
            return;
        }
        terminated = true;
        clearCaught();
        cancel();
        runtime.forget(this);
    }

    /**
     * The disable-path teardown: clears every still-tracked caught id, then cancels. Called by
     * {@link WhiteoutRuntime#cancelAll()} on plugin disable.
     */
    public void cancelAndClear() {
        if (terminated) {
            return;
        }
        terminated = true;
        clearCaught();
        cancel();
        runtime.forget(this);
    }

    /** Clears this task's effects off every caught id still tracked in the ledger. */
    private void clearCaught() {
        for (UUID id : caught) {
            if (ledger.contains(id)) {
                runtime.clearEffects(id);
            }
        }
    }
}
