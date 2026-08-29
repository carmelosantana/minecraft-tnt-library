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
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffectType;
import org.xpfarm.tntlibrary.detonation.DetonationContext;

/**
 * The shared coordinator for the whole White Out feature: it owns the one {@link EffectLedger}, the set
 * of in-flight {@link WhiteoutSequenceTask}s, and the injected {@link WhiteoutParams}.
 * {@code WhiteoutFeature} builds one and hands it to {@link WhiteOut} and the cleanup listener; every
 * detonation and every effect-clear path funnels through here.
 *
 * <h2>No-leaked-state gate (held to the G-Bomb #1-gate bar)</h2>
 *
 * <p>The only transient state is the blindness/slowness/freeze this bomb applies. It is tracked in the
 * shared {@link EffectLedger}; {@link #clearEffects(Entity)} (from the cleanup listener) and
 * {@link #cancelAll()}/{@link #restoreAll()} (plugin disable) clear it so nothing this bomb imposed
 * outlives an aborted detonation, a cancel, or a reload. The permanent scar needs no restore.
 *
 * <h2>Threading</h2>
 *
 * <p>Main-thread-only, mirroring {@code gbomb/GBombRuntime}: every entry point is a Bukkit event or
 * scheduled task on the server main thread. Server-dependent; verified at the runtime gate (gate 12).
 */
public final class WhiteoutRuntime {

    private final Plugin plugin;
    private final WhiteoutParams params;

    /** The single effect ledger shared by every task and the cleanup listener. */
    private final EffectLedger ledger = new EffectLedger();

    /** In-flight detonation tasks. Main-thread only. */
    private final Set<WhiteoutSequenceTask> active = new LinkedHashSet<>();

    public WhiteoutRuntime(Plugin plugin, WhiteoutParams params) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.params = Objects.requireNonNull(params, "params");
    }

    /** The shared effect ledger, exposed for the cleanup listener. */
    public EffectLedger ledger() {
        return ledger;
    }

    /** The injected, clamped White Out parameters. */
    public WhiteoutParams params() {
        return params;
    }

    /**
     * Starts a pull -> collapse -> sweep detonation for {@code ctx}. Builds the {@link WhiteoutZone}
     * from the block-centred detonation location and {@code params.radius()}, the center-out
     * {@link ScarGeometry} columns, sizes the sweep window, creates a {@link WhiteoutSequenceTask},
     * registers it active, and schedules it every tick. A null {@link DetonationContext#world()} is a
     * no-op. There is no {@code TNTPrimed} blast and no crater.
     */
    public void detonate(DetonationContext ctx) {
        World world = ctx.world();
        if (world == null) {
            return;
        }
        Location center = ctx.center().toCenterLocation();
        WhiteoutZone zone =
                new WhiteoutZone(center.getX(), center.getY(), center.getZ(), params.radius());
        int[][] columns = ScarGeometry.columnsCenterOut(params.radius());
        int rings = ScarGeometry.ringCount(params.radius());
        int sweepTicks = (int) Math.ceil(rings / (double) WhiteoutSequenceTask.RINGS_PER_TICK);

        WhiteoutSequenceTask task = new WhiteoutSequenceTask(
                this, ctx, world, center, zone, params, columns, sweepTicks, ledger);
        active.add(task);
        task.runTaskTimer(plugin, 0L, 1L);
    }

    /** Removes {@code task} from the active set; called by a task when it finishes or is cancelled. */
    void forget(WhiteoutSequenceTask task) {
        active.remove(task);
    }

    /**
     * Clears this bomb's storm effects off the entity with {@code id} (if resolvable) and forgets it
     * from the ledger. Idempotent — a second call for an already-cleared id is a no-op.
     */
    public void clearEffects(UUID id) {
        ledger.forget(id);
        Entity e = Bukkit.getEntity(id);
        if (e instanceof LivingEntity le) {
            stripEffects(le);
        }
    }

    /**
     * The unload-safe clear path: clears this bomb's effects on the still-valid {@code entity} handed
     * straight from a removal event, rather than re-resolving it by id. Used by
     * {@link WhiteoutCleanupListener}. Forgets the id and, only if it was tracked, strips the effects.
     */
    public void clearEffects(Entity entity) {
        boolean wasTracked = ledger.forget(entity.getUniqueId());
        if (wasTracked && entity instanceof LivingEntity le) {
            stripEffects(le);
        }
    }

    /**
     * Cancels every in-flight task (each clearing its own affected entities via
     * {@link WhiteoutSequenceTask#cancelAndClear()}), then drains the ledger with {@link #restoreAll()}
     * as a belt-and-suspenders sweep. The plugin-disable/teardown path: Bukkit cancels a plugin's tasks
     * on disable but does not clear the potion/freeze state, so we must.
     */
    public void cancelAll() {
        for (WhiteoutSequenceTask task : new ArrayList<>(active)) {
            task.cancelAndClear();
        }
        active.clear();
        restoreAll();
    }

    /**
     * Drains the ledger and clears effects for every still-tracked, resolvable entity. Idempotent — a
     * gone entity is skipped. The safe final sweep on disable and the fallback if any path missed a
     * per-entity clear.
     */
    public void restoreAll() {
        for (UUID id : ledger.drain()) {
            Entity e = Bukkit.getEntity(id);
            if (e instanceof LivingEntity le) {
                stripEffects(le);
            }
        }
    }

    /** Removes the blindness/slowness this bomb applied and zeroes the freeze ticks. */
    private void stripEffects(LivingEntity le) {
        le.removePotionEffect(PotionEffectType.BLINDNESS);
        le.removePotionEffect(PotionEffectType.SLOWNESS);
        le.setFreezeTicks(0);
    }

    /** A snapshot of the currently active task count, for diagnostics. */
    java.util.List<WhiteoutSequenceTask> activeTasks() {
        return new ArrayList<>(active);
    }
}
