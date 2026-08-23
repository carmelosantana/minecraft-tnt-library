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

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.xpfarm.tntlibrary.block.BombBlocks;

/**
 * The "smart" part of the Smart Bomb: a bounded per-armed-block scheduled task that applies the
 * programmed trigger each throttled tick and detonates via {@link SmartBombBlast} with the block's
 * <em>programmed</em> radius.
 *
 * <h2>What a tick does</h2>
 *
 * <p>On each run the watcher re-checks the block is still this bomb (aborting silently, with no
 * explosion, if it was broken or replaced mid-arm — exactly like {@code BombFuse}); advances the
 * elapsed counter; optionally runs a <em>cheap, bounded</em> proximity scan; plays the escalating
 * proximity warning; then asks the pure {@link TriggerEvaluator} whether to fire. The three triggers
 * combine with OR-semantics, first-to-fire, and the delay is the guaranteed upper-bound cap; the
 * reported precedence is {@code proximity > time > delay} (see {@link TriggerEvaluator}).
 *
 * <h2>Snapshot-at-arm</h2>
 *
 * <p>The bomb's {@link SmartBombParams} are read <em>once</em> at {@link #arm} and held for the
 * armed lifetime — a mid-arm store edit does not re-target a live bomb. This is intentional: the
 * programmed behaviour is fixed at the moment of ignition, matching a physical timer.
 *
 * <h2>Cheap proximity</h2>
 *
 * <p>The bounded {@code getNearbyLivingEntities} box scan runs <em>only</em> when
 * {@code params.proximity()} is true and only at the throttled {@value #WATCHER_PERIOD_TICKS}-tick
 * cadence, so an armed bomb with proximity off costs almost nothing per tick.
 *
 * <h2>Threading</h2>
 *
 * <p>All access — {@link #arm}, {@link #disarm}, {@link #isArmed}, {@link #cancelAll}, and every
 * scheduled {@code run()} — is on the server main thread (driven by events and the Bukkit scheduler),
 * so the {@link #armed} map needs no synchronisation, mirroring {@code BombFuse}'s main-thread-only
 * contract. Server-dependent; verified at the runtime gate, not in JUnit.
 */
public final class SmartBombWatcher {

    /**
     * How often the watcher ticks, in server ticks (5×/s): cheap enough for a bounded proximity scan,
     * fine-grained enough for delay/time triggers. Elapsed advances by this each run, mirroring
     * {@code BombFuse}'s {@code FUSE_PERIOD_TICKS} cadence.
     */
    public static final long WATCHER_PERIOD_TICKS = 4L;

    /** The proximity alarm beep; a note-block pling translates through Geyser to Bedrock. */
    public static final Sound WARNING_SOUND = Sound.BLOCK_NOTE_BLOCK_PLING;

    /** Volume for the proximity warning beep and the initial armed cue. */
    public static final float WARNING_VOLUME = 1.0f;

    private final Plugin plugin;
    private final SmartBombStoreService store;

    /** Block cells with a live watcher, keyed by location. Main-thread only, like {@code BombFuse}. */
    private final Map<Location, Armed> armed = new HashMap<>();

    public SmartBombWatcher(Plugin plugin, SmartBombStoreService store) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.store = Objects.requireNonNull(store, "store");
    }

    /** Whether {@code block} currently has a live watcher. */
    public boolean isArmed(Block block) {
        return armed.containsKey(block.getLocation());
    }

    /**
     * Arms {@code block} as a Smart Bomb: snapshots its programmed params and starts the throttled
     * trigger loop. A second arming of an already-armed block is refused (dedupe like
     * {@code BombFuse.isBurning}).
     *
     * <p>The params come from the store; a properly seeded bomb always has an entry, so
     * {@link SmartBombParams#DEFAULT} is only the safety net for a placement that somehow left no entry.
     * The {@code igniter} is carried for attribution/future use and may be {@code null} (environmental
     * ignition passes no entity), so it is never required non-null.
     *
     * @return {@code true} if a new watcher started; {@code false} if this block was already armed
     */
    public boolean arm(Block block, Entity igniter) {
        Location key = block.getLocation();
        if (armed.containsKey(key)) {
            return false;
        }
        SmartBombParams params = store.get(BlockKey.from(block)).orElse(SmartBombParams.DEFAULT);
        World world = block.getWorld();
        Location center = key.toCenterLocation();

        // A single low-pitch note so a player knows arming succeeded.
        world.playSound(center, WARNING_SOUND, WARNING_VOLUME, 0.6f);

        Armed holder = new Armed(params);
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                tick(block, key, world, center, holder, this);
            }
        }.runTaskTimer(plugin, 0L, WATCHER_PERIOD_TICKS);
        holder.task = task;
        armed.put(key, holder);
        return true;
    }

    /** One throttled evaluation of an armed bomb. Main-thread only. */
    private void tick(Block block, Location key, World world, Location center, Armed holder,
            BukkitRunnable runnable) {
        // 1. Still this bomb? Broken/replaced mid-arm aborts silently, no detonation (like BombFuse).
        boolean stillThisBomb = BombBlocks.bombIdOf(block)
                .map(SmartBomb.ID::equals)
                .orElse(false);
        if (!stillThisBomb) {
            runnable.cancel();
            armed.remove(key);
            return; // the Task-7 listener owns the store entry on a break; do not touch it here
        }

        SmartBombParams params = holder.params;

        // 2. Advance elapsed by the tick cadence.
        holder.elapsed += WATCHER_PERIOD_TICKS;

        // 3. Proximity scan — only when programmed, otherwise skipped entirely (cheap).
        Double nearestDistance = null;
        if (params.proximity()) {
            int r = params.proximityRadius();
            double nearestSoFar = Double.POSITIVE_INFINITY;
            for (LivingEntity entity : world.getNearbyLivingEntities(center, r, r, r)) {
                double distance = entity.getLocation().distance(center);
                if (distance < nearestSoFar) {
                    nearestSoFar = distance;
                }
            }
            nearestDistance = (nearestSoFar == Double.POSITIVE_INFINITY) ? null : nearestSoFar;
        }

        // 4. Warning cue — beeps speed up and rise in pitch as the nearest entity closes.
        if (params.proximity() && nearestDistance != null) {
            ProximityWarning.Warning warning =
                    ProximityWarning.at(nearestDistance, params.proximityRadius());
            if (warning.play()) {
                holder.beepCounter -= WATCHER_PERIOD_TICKS;
                if (holder.beepCounter <= 0) {
                    world.playSound(center, WARNING_SOUND, WARNING_VOLUME, warning.pitch());
                    holder.beepCounter = warning.periodTicks();
                }
            }
        }

        // 5. Decide, with the world time normalized to 0..23999 for the pure evaluator.
        long worldTime = Math.floorMod(world.getTime(), 24000);
        TriggerEvaluator.Decision decision = TriggerEvaluator.evaluate(
                params, new TriggerEvaluator.State(holder.elapsed, worldTime, nearestDistance));

        // 6. Detonate: disarm, drop the store entry (detonate removes it), clear the cube, then blast
        //    with the PROGRAMMED radius.
        if (decision.detonate()) {
            runnable.cancel();
            armed.remove(key);
            store.remove(BlockKey.from(block));
            block.setType(Material.AIR, false);
            SmartBombBlast.detonate(world, center, params.radius());
            plugin.getLogger().fine(() ->
                    "Smart Bomb detonated at " + key + " fired by " + decision.firedBy());
        }
    }

    /**
     * Disarms {@code block}: cancels its watcher and forgets it. The Task-7 listener calls this on a
     * bomb break (and then removes the store entry itself).
     *
     * @return {@code true} if the block had a live watcher that was cancelled
     */
    public boolean disarm(Block block) {
        return disarm(block.getLocation());
    }

    /** Cancels and forgets the watcher keyed by {@code key}, if any. */
    private boolean disarm(Location key) {
        Armed holder = armed.remove(key);
        if (holder == null) {
            return false;
        }
        if (holder.task != null) {
            holder.task.cancel();
        }
        return true;
    }

    /**
     * Cancels every live watcher and clears the map. Called from {@code onDisable}: Bukkit also cancels
     * a plugin's tasks on disable, but clearing the map here means a reload starts from a clean slate.
     */
    public void cancelAll() {
        for (Armed holder : armed.values()) {
            if (holder.task != null) {
                holder.task.cancel();
            }
        }
        armed.clear();
    }

    /**
     * Per-armed-bomb bookkeeping: the running task, the params snapshotted at arm, and the two mutable
     * counters the trigger loop advances. A tiny mutable holder rather than a record, since the
     * counters and the (late-bound) task are written after construction.
     */
    private static final class Armed {

        /** The params fixed at arm; deliberately not re-read each tick. */
        private final SmartBombParams params;

        /** The running scheduled task; assigned once {@code runTaskTimer} returns. */
        private BukkitTask task;

        /** Ticks since arming, advanced by {@link #WATCHER_PERIOD_TICKS} each run. */
        private long elapsed;

        /** Countdown to the next warning beep, in ticks; reset to the warning's period after a beep. */
        private long beepCounter;

        private Armed(SmartBombParams params) {
            this.params = params;
        }
    }
}
