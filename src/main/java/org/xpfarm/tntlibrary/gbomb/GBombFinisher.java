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

import org.bukkit.Location;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Damageable;

/**
 * The G-Bomb's guaranteed server-side kill — the static Bukkit edge for the pure {@link FinisherSpec}.
 *
 * <p>On the slam tick the runtime hands each launched target here. This builds a FALL-typed
 * {@link DamageSource} anchored at the slam location and applies the clamped {@link FinisherSpec}
 * damage. That server-side {@code damage(...)} call — not the client-rendered fall — is what actually
 * lands the kill: it does not depend on client physics, so it lands on Java <em>and</em> Bedrock even
 * when the client-side float never rendered.
 *
 * <p><strong>Accepted Bedrock degradation.</strong> Geyser does not reliably translate
 * {@code setGravity(false)} and player velocity is client-authoritative, so the launch/hang/slam
 * <em>visuals</em> are weak or absent on Bedrock. This FALL {@code DamageSource} is deliberately the
 * one part that does not rely on those visuals, so the kill is identical across editions.
 *
 * <p>Server-dependent (constructs a live {@link DamageSource} and calls into CraftBukkit); verified at
 * the runtime gate (gate 12), not in JUnit — mirroring {@code smartbomb/SmartBombBlast}. All decision
 * logic (the clamped damage value, the FALL damage type) is unit-tested in {@link FinisherSpec}.
 */
public final class GBombFinisher {

    private GBombFinisher() {}

    /**
     * Applies the guaranteed FALL finisher to {@code e} at {@code slamLoc}.
     *
     * <p>The damage value is taken through {@link FinisherSpec#of(double)} so the tested clamp (never
     * below {@link GBombParams#KILL_DAMAGE_MIN}) is the value that lands — a degenerate config value can
     * never quietly disarm the finisher.
     *
     * @param e the target to damage; a {@link Damageable} (every {@code LivingEntity} is one). An
     *     invulnerable or creative-mode target naturally no-ops here, which is fine — its gravity was
     *     already restored before this call
     * @param slamLoc where the killing fall is anchored, used as the damage source location
     * @param killDamage the requested FALL finisher damage, clamped by {@link FinisherSpec}
     */
    public static void finish(Damageable e, Location slamLoc, double killDamage) {
        DamageSource src = DamageSource.builder(DamageType.FALL)
                .withDamageLocation(slamLoc)
                .build();
        e.damage(FinisherSpec.of(killDamage).damage(), src);
    }
}
