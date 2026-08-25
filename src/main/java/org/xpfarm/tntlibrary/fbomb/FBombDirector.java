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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.xpfarm.tntlibrary.config.TntLibraryConfig;

/**
 * The orchestrator that owns every live {@link FBombShow} and drives them all from a single
 * repeating task. Igniting a placed F-Bomb is diverted (by the shared ignition listener) to {@link
 * #summon(Block, Entity)}, which builds a fully wired show — air-probed apparition placement, rig,
 * boss bar — and registers it; a subsequent ignition of a block that already has a live show is a
 * no-op ({@code summon} returns {@code false}), à la {@code BombFuse.isBurning}.
 *
 * <p>Modelled on {@code tuesday-twister}'s {@code StormManager}: a single {@code runTaskTimer} over
 * all active shows, iteration over a copied snapshot so a show removing itself mid-tick is safe, a
 * per-show try/catch so one failing show is torn down and dropped (never recurring every tick), and
 * an onEnable orphan sweep that reaps any entity a crashed run left tagged with {@link
 * FBombRig#RIG_KEY}. All state is main-thread-only (like {@code SmartBombWatcher}), so no
 * synchronisation is needed.
 *
 * <p>Server-dependent (schedules tasks, spawns entities, manages boss bars); verified at the
 * runtime gate rather than in JUnit.
 */
public final class FBombDirector {

    private final JavaPlugin plugin;
    private final TntLibraryConfig config;
    private final FBombDefaults defaults;

    private final Map<UUID, FBombShow> active = new HashMap<>();
    private final Map<Location, UUID> locations = new HashMap<>();

    private BukkitTask task;
    private long tick;

    public FBombDirector(JavaPlugin plugin, TntLibraryConfig config, FBombDefaults defaults) {
        this.plugin = plugin;
        this.config = config;
        this.defaults = defaults;
    }

    /**
     * Diverted ignition entry point: builds and registers a new F-Bomb cinematic on {@code block},
     * or returns {@code false} when that block already has a live show. The apparition's spawn
     * heading is air-probed (the most open of the eight compass headings, favouring a player
     * igniter's facing on a tie) so the fake Wither materialises in open air rather than inside a
     * wall.
     *
     * @param block the placed F-Bomb block being ignited
     * @param igniter the entity that ignited it (a {@link Player}'s facing seeds the tie-break), or
     *     {@code null}
     * @return {@code true} if a new show was started; {@code false} if this block already has one
     */
    public boolean summon(Block block, Entity igniter) {
        Location key = block.getLocation();
        if (locations.containsKey(key)) {
            return false;
        }

        FBombParams params =
                defaults.seed(config.bomb(FBomb.ID).radius(), config.bomb(FBomb.ID).fuseTicks());

        Location base;
        int preferredIndex;
        if (igniter instanceof Player player) {
            base = player.getEyeLocation();
            preferredIndex = nearestYawIndex(base.getYaw());
        } else {
            base = block.getLocation().toCenterLocation();
            preferredIndex = -1;
        }

        double[] openness = probeOpenness(base, params);
        int best = SpawnPlacement.chooseBestDirection(openness, preferredIndex);
        Location rigCenter = SpawnPlacement.offset(
                base, SpawnPlacement.DIRECTION_YAWS[best], params.spawnDistance(), params.spawnHeight());

        UUID id = UUID.randomUUID();
        FBombRig rig = new FBombRig(plugin, id);
        BossBar bossBar = Bukkit.createBossBar("F-Bomb", BarColor.RED, BarStyle.SOLID);
        rig.spawn(rigCenter);

        FBombShow show = new FBombShow(plugin, id, block, rigCenter, params, rig, bossBar, igniter);
        active.put(id, show);
        locations.put(key, id);
        return true;
    }

    /**
     * The integer openness score of each of the eight {@link SpawnPlacement#DIRECTION_YAWS}: for
     * each heading, the candidate rig center is computed and the count of non-solid cells in a small
     * box around it (x,z in [-1..1], y in [0..3]) is tallied — a higher count means more open air.
     *
     * <p>Counts are stored as whole numbers (never floating-point accumulation) so {@link
     * SpawnPlacement#chooseBestDirection}'s exact {@code ==} tie-break stays well-defined.
     */
    private double[] probeOpenness(Location base, FBombParams params) {
        double[] openness = new double[SpawnPlacement.DIRECTION_YAWS.length];
        for (int d = 0; d < SpawnPlacement.DIRECTION_YAWS.length; d++) {
            Location candidate = SpawnPlacement.offset(
                    base, SpawnPlacement.DIRECTION_YAWS[d], params.spawnDistance(), params.spawnHeight());
            World world = candidate.getWorld();
            if (world == null) {
                openness[d] = 0.0;
                continue;
            }
            int cx = candidate.getBlockX();
            int cy = candidate.getBlockY();
            int cz = candidate.getBlockZ();
            int open = 0;
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    for (int y = 0; y <= 3; y++) {
                        if (!world.getBlockAt(cx + x, cy + y, cz + z).getType().isSolid()) {
                            open++;
                        }
                    }
                }
            }
            openness[d] = open;
        }
        return openness;
    }

    /** The {@link SpawnPlacement#DIRECTION_YAWS} index whose heading is nearest {@code yaw}. */
    private static int nearestYawIndex(float yaw) {
        int best = 0;
        double bestDelta = Double.MAX_VALUE;
        for (int i = 0; i < SpawnPlacement.DIRECTION_YAWS.length; i++) {
            double delta = angularDistance(yaw, SpawnPlacement.DIRECTION_YAWS[i]);
            if (delta < bestDelta) {
                bestDelta = delta;
                best = i;
            }
        }
        return best;
    }

    /** The smallest absolute angle, in degrees [0,180], between two headings. */
    private static double angularDistance(double a, double b) {
        double diff = Math.abs(a - b) % 360.0;
        return diff > 180.0 ? 360.0 - diff : diff;
    }

    /** Schedules the single global tick task (every tick) if it is not already running. */
    public void start() {
        if (task == null) {
            task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickAll, 1L, 1L);
        }
    }

    /**
     * Advances every active show by one tick over a copied snapshot, then reaps them. A show whose
     * tick throws is logged, torn down, and dropped so it never recurs; after ticking, every {@link
     * FBombShow#finished()} show is dropped from both the active and location maps.
     */
    public void tickAll() {
        tick++;
        for (FBombShow show : new ArrayList<>(active.values())) {
            try {
                show.tick(tick);
            } catch (Throwable t) {
                plugin.getLogger().log(Level.SEVERE,
                        "F-Bomb show " + show.id() + " threw during tick; tearing it down.", t);
                safeTeardown(show);
            }
        }
        for (FBombShow show : new ArrayList<>(active.values())) {
            if (show.finished()) {
                remove(show);
            }
        }
    }

    /** Tears down a show whose tick threw and drops it, swallowing any secondary failure. */
    private void safeTeardown(FBombShow show) {
        try {
            show.teardown();
        } catch (Throwable t) {
            plugin.getLogger().log(Level.SEVERE,
                    "Cleanup for F-Bomb show " + show.id() + " also failed; force-removing it.", t);
        }
        remove(show);
    }

    /** Drops a show from the active and location maps. */
    private void remove(FBombShow show) {
        active.remove(show.id());
        locations.remove(show.sourceBlock().getLocation());
    }

    /** Cancels the global tick task and tears down every remaining show, for a clean disable. */
    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (FBombShow show : new ArrayList<>(active.values())) {
            show.teardown();
        }
        active.clear();
        locations.clear();
    }

    /**
     * On enable, reaps debris a previous run may have left behind: removes any entity in a loaded
     * world still tagged with {@link FBombRig#RIG_KEY} (a stranded rig part or orphaned skull). No
     * snapshot files exist to restore — the F-Bomb keeps no world snapshot.
     */
    public void cleanupOrphans() {
        for (World world : plugin.getServer().getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity.getPersistentDataContainer().has(FBombRig.RIG_KEY, PersistentDataType.STRING)) {
                    entity.remove();
                }
            }
        }
    }
}
