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

import org.bukkit.Location;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Damageable;

/**
 * The White Out's guaranteed server-side FREEZE kill — the static Bukkit edge for the pure
 * {@link FreezeFinisherSpec}.
 *
 * <p>At the collapse the runtime hands each caught entity here. This builds a FREEZE-typed
 * {@link DamageSource} anchored at the blast center and applies the clamped {@link FreezeFinisherSpec}
 * damage. That server-side {@code damage(...)} call — not the client-side pull or freeze vignette — is
 * what actually lands the kill: it does not depend on client physics, so a full-health Bedrock player
 * dies even when the velocity pull never took.
 *
 * <p>Server-dependent (constructs a live {@link DamageSource} and calls into CraftBukkit); verified at
 * the runtime gate (gate 12), not in JUnit — mirroring {@code gbomb/GBombFinisher}. All decision logic
 * (the clamped damage value, the FREEZE damage type) is unit-tested in {@link FreezeFinisherSpec}.
 */
public final class FreezeFinisher {

    private FreezeFinisher() {}

    /**
     * Applies the guaranteed FREEZE finisher to {@code e} anchored at {@code center}.
     *
     * @param e the target to damage; a {@link Damageable} (every {@code LivingEntity} is one). An
     *     invulnerable or creative-mode target naturally no-ops here
     * @param center where the killing freeze is anchored, used as the damage source location
     * @param killDamage the requested FREEZE finisher damage, clamped by {@link FreezeFinisherSpec}
     */
    public static void finish(Damageable e, Location center, double killDamage) {
        DamageSource src = DamageSource.builder(DamageType.FREEZE)
                .withDamageLocation(center)
                .build();
        e.damage(FreezeFinisherSpec.of(killDamage).damage(), src);
    }
}
