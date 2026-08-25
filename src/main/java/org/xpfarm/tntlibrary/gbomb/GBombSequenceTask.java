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
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

/**
 * One bounded per-detonation task that drives a single G-Bomb through launch → hang → slam → done.
 *
 * <p>The task holds only the mutable elapsed-tick counter and the list of entities <em>this</em>
 * detonation disabled gravity on; every decision (which phase a tick is in, the launch/slam velocities
 * and fall distances, the clamped finisher damage, the in-radius test) comes from the pure,
 * unit-tested cores — {@link GBombSequence}, {@link GBombPhysics}, {@link LaunchZone},
 * {@link FinisherSpec}. Here we only translate those into live Bukkit calls.
 *
 * <h2>#1 review gate — gravity restore</h2>
 *
 * <p>On {@link LaunchPhase#LAUNCH} each selected entity's prior gravity flag is recorded in the shared
 * {@link GravityLedger} and the id added to {@link #launched} <em>before</em> gravity is disabled. On
 * {@link LaunchPhase#SLAM} gravity is restored <em>first</em> (via {@link GBombRuntime#restore(UUID)},
 * which clears the ledger entry) and only then is the finisher applied, so a finisher exception can
 * never strand an entity gravity-off. {@link #cancelAndRestore()} restores every still-tracked launched
 * id before cancelling, covering plugin disable mid-hang (Bukkit's own task cancel does not restore
 * gravity). The terminal {@link LaunchPhase#DONE} tick does a belt-and-suspenders restore of anything
 * still tracked.
 *
 * <h2>Accepted Bedrock degradation</h2>
 *
 * <p>Geyser does not reliably translate {@code setGravity(false)} and player velocity is
 * client-authoritative, so the launch/hang/slam <em>visuals</em> are weak or absent on Bedrock. The
 * kill still lands via the server-side FALL {@link GBombFinisher}, which does not depend on client
 * physics.
 *
 * <h2>Threading</h2>
 *
 * <p>Main-thread-only: constructed from {@link GBombRuntime#launch} and run by the Bukkit scheduler on
 * the server main thread, so {@link #launched} and the shared ledger need no synchronisation.
 * Server-dependent; verified at the runtime gate (gate 12), not in JUnit.
 */
public final class GBombSequenceTask extends BukkitRunnable {

    private final GBombRuntime runtime;
    private final World world;
    private final Location center;
    private final LaunchZone zone;
    private final GBombParams params;
    private final GravityLedger ledger;

    /** Exactly the entities THIS task disabled gravity on, so it restores its own on cancel. */
    private final List<UUID> launched = new ArrayList<>();

    /** Ticks since scheduling; drives {@link GBombSequence#phaseAt(long, long)}. */
    private long elapsed;

    /** Guards against a double terminal/cancel so {@link #cancel()} is never called twice. */
    private boolean terminated;

    GBombSequenceTask(GBombRuntime runtime, World world, Location center, LaunchZone zone,
            GBombParams params, GravityLedger ledger) {
        this.runtime = runtime;
        this.world = world;
        this.center = center;
        this.zone = zone;
        this.params = params;
        this.ledger = ledger;
    }

    @Override
    public void run() {
        if (terminated) {
            return;
        }
        LaunchPhase phase = GBombSequence.phaseAt(elapsed, params.hangTicks());
        switch (phase) {
            case LAUNCH -> doLaunch();
            case HANG -> {
                // Nothing to do: gravity is already disabled, so the targets hang at apex on their own.
            }
            case SLAM -> doSlam();
            case DONE -> {
                finishTerminal();
                return;
            }
        }
        elapsed++;
    }

    /** LAUNCH: select every living entity in the true sphere, disable gravity, throw them upward. */
    private void doLaunch() {
        int r = params.radius();
        Vector launchVelocity = toBukkit(GBombPhysics.launchVelocity(params.launchPower()));
        for (LivingEntity e : world.getNearbyLivingEntities(center, r, r, r)) {
            if (!e.isValid() || !zone.covers(e.getX(), e.getY(), e.getZ())) {
                continue;
            }
            UUID id = e.getUniqueId();
            boolean prior = e.hasGravity();
            ledger.record(id, prior);
            launched.add(id);
            e.setGravity(false);
            e.setFallDistance(GBombPhysics.launchFallDistance());
            e.setVelocity(launchVelocity);
        }
    }

    /**
     * SLAM: for each launched entity, restore gravity FIRST (clearing the ledger entry) so a finisher
     * exception can never strand it gravity-off, then throw it down and apply the FALL finisher.
     */
    private void doSlam() {
        Vector slamVelocity = toBukkit(GBombPhysics.slamVelocity());
        for (UUID id : launched) {
            // Restore gravity before any finisher work, even if the entity is gone (clears the ledger).
            runtime.restore(id);
            Entity e = Bukkit.getEntity(id);
            if (!(e instanceof LivingEntity le) || !le.isValid()) {
                continue;
            }
            le.setVelocity(slamVelocity);
            le.setFallDistance(GBombPhysics.slamFallDistance());
            GBombFinisher.finish(le, le.getLocation(), params.killDamage());
        }
    }

    /** DONE: cancel, deregister, and belt-and-suspenders restore anything still tracked. */
    private void finishTerminal() {
        if (terminated) {
            return;
        }
        terminated = true;
        restoreStillTracked();
        cancel();
        runtime.forget(this);
    }

    /**
     * Restores gravity for every id this task launched that is still tracked, then cancels the task.
     * Called by {@link GBombRuntime#cancelAll()} on plugin disable so a detonation cancelled mid-hang
     * still restores gravity — Bukkit's own task cancel on disable does not.
     */
    public void cancelAndRestore() {
        if (terminated) {
            return;
        }
        terminated = true;
        restoreStillTracked();
        cancel();
        runtime.forget(this);
    }

    /** Restores every launched id still present in the ledger (already-restored ids are skipped). */
    private void restoreStillTracked() {
        for (UUID id : launched) {
            if (ledger.contains(id)) {
                runtime.restore(id);
            }
        }
    }

    private static Vector toBukkit(Vec3 v) {
        return new Vector(v.x(), v.y(), v.z());
    }
}
