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
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.WitherSkull;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;
import org.xpfarm.tntlibrary.block.BombBlocks;

/**
 * A single live F-Bomb cinematic: the per-show state machine that ties together the visible {@link
 * FBombRig} apparition, the {@link BossBar}, the telegraphed WitherSkull volley, and the final
 * blast, advancing all of them once per {@link #tick(long)}.
 *
 * <p>This is the bounded, self-terminating sibling of {@code tuesday-twister}'s roaming {@code
 * WitherStorm}: it never moves, targets no one, and always ends — it fades in during SUMMON, menaces
 * (bobbing, updating its boss bar, firing survivable skulls at the nearest viewer) during MENACE,
 * and detonates once at BLAST. The pure tick-to-phase math lives in {@link CinematicStateMachine};
 * the pure fire schedule in {@link SkullVolleySchedule}; this class is the Bukkit glue and is
 * verified at the runtime gate rather than in JUnit.
 *
 * <p>The show never removes itself from the director; the director reads {@link #finished()} and
 * reaps it. {@link #teardown()} (boss bar + rig) is idempotent and runs on every exit path: the
 * source block being broken mid-show, the plugin disabling, the blast, or a throw during tick.
 */
public final class FBombShow {

    private final Plugin plugin;
    private final UUID id;
    private final Block sourceBlock;
    private final Location rigCenter;
    private final FBombParams params;
    private final FBombRig rig;
    private final BossBar bossBar;
    private final Entity igniter;

    private boolean finished;
    private boolean toreDown;

    public FBombShow(Plugin plugin, UUID id, Block sourceBlock, Location rigCenter,
            FBombParams params, FBombRig rig, BossBar bossBar, Entity igniter) {
        this.plugin = plugin;
        this.id = id;
        this.sourceBlock = sourceBlock;
        this.rigCenter = rigCenter.clone();
        this.params = params;
        this.rig = rig;
        this.bossBar = bossBar;
        this.igniter = igniter;
    }

    /** The block the F-Bomb was placed on; the director dedupes summons by its location. */
    public Block sourceBlock() {
        return sourceBlock;
    }

    /** The owning show id; also the PDC tag on the rig and every skull it fires. */
    public UUID id() {
        return id;
    }

    /** True once the show has run its BLAST (or aborted); the director reaps finished shows. */
    public boolean finished() {
        return finished;
    }

    /**
     * Advances the cinematic by one elapsed {@code tick}. Aborts silently (tearing down without
     * detonating) if the source block is no longer an F-Bomb — it was broken or replaced mid-show,
     * mirroring {@code SmartBombWatcher}'s abort. Otherwise drives the SUMMON/MENACE/BLAST phases.
     */
    public void tick(long tick) {
        if (finished) {
            return;
        }
        if (!BombBlocks.bombIdOf(sourceBlock).map(FBomb.ID::equals).orElse(false)) {
            // Broken/replaced mid-show: tear down the apparition but never detonate.
            teardown();
            finished = true;
            return;
        }

        CinematicPhase phase = CinematicStateMachine.phaseAt(tick, params.menaceTicks());
        switch (phase) {
            case SUMMON -> summon(tick);
            case MENACE -> menace(tick);
            case BLAST -> blast();
            case DONE -> {
                // Past the blast tick with nothing left to do; ensure teardown and reap.
                teardown();
                finished = true;
            }
        }
    }

    private void summon(long tick) {
        if (tick == 0L) {
            World world = rigCenter.getWorld();
            if (world != null) {
                world.playSound(rigCenter, Sound.ENTITY_WITHER_SPAWN, 4f, 0.6f);
            }
            addInRangePlayers();
        }
        rig.animate(rigCenter, tick);
    }

    private void menace(long tick) {
        rig.animate(rigCenter, tick);
        updateBossBar(tick);
        if (SkullVolleySchedule.firesAt(tick, params.skullCount(), params.skullCadenceTicks(),
                CinematicStateMachine.SUMMON_TICKS, params.menaceTicks())) {
            fireSkull();
        }
    }

    private void blast() {
        Location blastCenter = sourceBlock.getLocation().toCenterLocation();
        sourceBlock.setType(Material.AIR, false);
        FBombBlast.detonate(sourceBlock.getWorld(), blastCenter, params.radius());
        teardown();
        finished = true;
    }

    private void updateBossBar(long tick) {
        double progress = Math.max(0.0, Math.min(1.0, 1.0 - (tick / (double) params.menaceTicks())));
        bossBar.setProgress(progress);
        bossBar.setTitle("F-Bomb");
        manageViewers();
    }

    /** Adds every online player within {@link FBombParams#bossbarRange()} of the rig, same world. */
    private void addInRangePlayers() {
        World world = rigCenter.getWorld();
        if (world == null) {
            return;
        }
        double rangeSq = (double) params.bossbarRange() * params.bossbarRange();
        for (Player player : world.getPlayers()) {
            if (player.getLocation().distanceSquared(rigCenter) <= rangeSq
                    && !bossBar.getPlayers().contains(player)) {
                bossBar.addPlayer(player);
            }
        }
    }

    /**
     * Adds players who came into range and removes players who left it or changed world — the
     * {@code WitherStorm.manageViewers} idiom, measured against the rig center and the configured
     * boss bar range.
     */
    private void manageViewers() {
        World world = rigCenter.getWorld();
        if (world == null) {
            return;
        }
        double rangeSq = (double) params.bossbarRange() * params.bossbarRange();
        for (Player player : new ArrayList<>(bossBar.getPlayers())) {
            if (!player.getWorld().equals(world)
                    || player.getLocation().distanceSquared(rigCenter) > rangeSq) {
                bossBar.removePlayer(player);
            }
        }
        for (Player player : world.getPlayers()) {
            if (player.getLocation().distanceSquared(rigCenter) <= rangeSq
                    && !bossBar.getPlayers().contains(player)) {
                bossBar.addPlayer(player);
            }
        }
    }

    /**
     * Fires one survivable, non-incendiary {@link WitherSkull} from the rig center toward the
     * nearest boss-bar viewer's eyes. A skull is only fired when there is a viewer to aim at; the
     * skull is PDC-tagged with the show id so a crash-orphaned skull is swept by the director.
     */
    private void fireSkull() {
        World world = rigCenter.getWorld();
        if (world == null) {
            return;
        }
        Player target = nearestViewer();
        if (target == null) {
            return; // no one to menace this tick; skip firing
        }
        Vector direction = target.getEyeLocation().toVector().subtract(rigCenter.toVector());
        if (direction.lengthSquared() < 1.0e-6) {
            return;
        }
        direction.normalize();
        WitherSkull skull = world.spawn(rigCenter, WitherSkull.class);
        skull.setDirection(direction);
        skull.setVelocity(direction.clone().multiply(1.0));
        skull.setYield(1f);
        skull.setIsIncendiary(false);
        skull.getPersistentDataContainer()
                .set(FBombRig.RIG_KEY, PersistentDataType.STRING, id.toString());
    }

    /** The boss-bar viewer nearest the rig center in the same world, or null when none are viewing. */
    private Player nearestViewer() {
        World world = rigCenter.getWorld();
        Player best = null;
        double bestSq = Double.MAX_VALUE;
        for (Player player : bossBar.getPlayers()) {
            if (!player.getWorld().equals(world)) {
                continue;
            }
            double sq = player.getLocation().distanceSquared(rigCenter);
            if (sq < bestSq) {
                bestSq = sq;
                best = player;
            }
        }
        return best;
    }

    /** Tears down the boss bar and rig. Idempotent via the {@code toreDown} guard. */
    public void teardown() {
        if (toreDown) {
            return;
        }
        toreDown = true;
        bossBar.removeAll();
        rig.remove();
    }
}
