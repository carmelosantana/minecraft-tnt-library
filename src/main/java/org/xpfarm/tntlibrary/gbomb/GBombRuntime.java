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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.xpfarm.tntlibrary.detonation.DetonationContext;

/**
 * The shared coordinator for the whole G-Bomb feature: it owns the one {@link GravityLedger}, the set
 * of in-flight {@link GBombSequenceTask}s, and the injected {@link GBombParams}. {@code GBombFeature}
 * (Task 8) builds one of these and hands it to {@link GBomb} and the gravity-restore listener; every
 * detonation and every restore path funnels through here so gravity bookkeeping has exactly one home.
 *
 * <h2>#1 review gate — gravity restore on every path</h2>
 *
 * <p>Because the {@code NoGravity} NBT flag persists across chunk/entity unload, any entity whose
 * gravity we disabled MUST be restored on every path: normal slam ({@link #restore(UUID)} from the
 * task), task cancel and plugin disable ({@link #cancelAll()} →
 * {@link GBombSequenceTask#cancelAndRestore()} + {@link #restoreAll()}), and chunk/entity unload (the
 * Task-8 listener, via {@link #restore(UUID)} using {@link #ledger()}). Every restore resolves the id
 * to a live entity and calls {@code setGravity(prior)}; a gone entity still has its ledger entry
 * cleared, and an unknown prior defaults to {@code true} so gravity is never left off.
 *
 * <h2>Threading</h2>
 *
 * <p>Main-thread-only, mirroring {@code smartbomb/SmartBombWatcher}: every entry point is a Bukkit
 * event or scheduled task on the server main thread, so {@link #active} and the ledger need no
 * synchronisation. Server-dependent; verified at the runtime gate (gate 12), not in JUnit.
 */
public final class GBombRuntime {

    private final Plugin plugin;
    private final GBombParams params;

    /** The single gravity-restore ledger shared by every task and the unload listener. */
    private final GravityLedger ledger = new GravityLedger();

    /** In-flight detonation tasks. Main-thread only, mirroring {@code SmartBombWatcher.armed}. */
    private final Set<GBombSequenceTask> active = new LinkedHashSet<>();

    /**
     * @param plugin the owning plugin, used to schedule each detonation's {@link GBombSequenceTask};
     *     never {@code null}
     * @param params the G-Bomb's clamped, config-derived parameters, injected once; never {@code null}
     */
    public GBombRuntime(Plugin plugin, GBombParams params) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.params = Objects.requireNonNull(params, "params");
    }

    /** The shared gravity-restore ledger, exposed for the Task-8 chunk/entity-unload listener. */
    public GravityLedger ledger() {
        return ledger;
    }

    /** The injected, clamped G-Bomb parameters. */
    public GBombParams params() {
        return params;
    }

    /**
     * Starts a launch→hang→slam detonation for {@code ctx}. Builds the spherical {@link LaunchZone}
     * from the block-centred detonation location and {@code params.radius()}, creates a
     * {@link GBombSequenceTask}, registers it as active, and schedules it every tick.
     *
     * <p>A null {@link DetonationContext#world()} (a detached center) is a no-op, mirroring
     * {@code smartbomb/SmartBombBlast}. There is no {@code TNTPrimed} blast and no crater — the G-Bomb's
     * whole effect is the launch and the FALL finisher.
     */
    public void launch(DetonationContext ctx) {
        World world = ctx.world();
        if (world == null) {
            return; // detached location; nothing to launch against
        }
        Location center = ctx.center().toCenterLocation();
        LaunchZone zone = new LaunchZone(center.getX(), center.getY(), center.getZ(), params.radius());

        GBombSequenceTask task = new GBombSequenceTask(this, world, center, zone, params, ledger);
        active.add(task);
        task.runTaskTimer(plugin, 0L, 1L);
    }

    /** Removes {@code task} from the active set; called by a task when it finishes or is cancelled. */
    void forget(GBombSequenceTask task) {
        active.remove(task);
    }

    /**
     * Restores gravity for a single entity by id and clears its ledger entry. Resolves
     * {@link Bukkit#getEntity(UUID)}; if the entity is still present it is set back to its recorded
     * prior gravity flag (or {@code true} when the prior is unknown/absent — gravity is never left
     * off). Safe when the entity is gone: the ledger entry is cleared regardless. Idempotent — a second
     * call for an already-restored id is a no-op.
     *
     * @param id the entity whose gravity to restore
     */
    public void restore(UUID id) {
        boolean prior = ledger.forget(id).orElse(true);
        Entity e = Bukkit.getEntity(id);
        if (e != null) {
            e.setGravity(prior);
        }
    }

    /**
     * Cancels every in-flight task (each restoring its own launched entities via
     * {@link GBombSequenceTask#cancelAndRestore()}), then drains the ledger with {@link #restoreAll()}
     * as a belt-and-suspenders sweep. This is the plugin-disable/teardown path: Bukkit cancels a
     * plugin's tasks on disable but does <em>not</em> restore gravity, so we must.
     */
    public void cancelAll() {
        // Copy first: cancelAndRestore calls back into forget(), mutating active.
        for (GBombSequenceTask task : new ArrayList<>(active)) {
            task.cancelAndRestore();
        }
        active.clear();
        restoreAll();
    }

    /**
     * Drains the ledger and restores gravity for every tracked entity. Idempotent — {@link
     * GravityLedger#drain()} is idempotent, and a null/absent entity is skipped (never left gravity-off,
     * since a present entity is set to its prior flag, defaulting to {@code true}). This is the safe
     * final sweep on disable and the fallback if any path missed a per-entity restore.
     */
    public void restoreAll() {
        Map<UUID, Boolean> drained = ledger.drain();
        for (Map.Entry<UUID, Boolean> entry : drained.entrySet()) {
            Entity e = Bukkit.getEntity(entry.getKey());
            if (e != null) {
                e.setGravity(entry.getValue());
            }
        }
    }

    /** A snapshot of the currently active task count, for diagnostics/tests. */
    List<GBombSequenceTask> activeTasks() {
        return new ArrayList<>(active);
    }
}
