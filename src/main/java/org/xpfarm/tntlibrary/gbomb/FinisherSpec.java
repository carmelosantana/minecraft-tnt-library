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

/**
 * Pure descriptor of the G-Bomb's guaranteed server-side kill, capturing the two params that can be
 * asserted headlessly: the damage value and the fact the {@code DamageType} is {@code FALL}.
 *
 * <p>The runtime edge ({@code GBombFinisher}) turns this into
 * {@code DamageSource.builder(DamageType.FALL).withDamageLocation(loc).build()} followed by
 * {@code ((Damageable) entity).damage(damage, damageSource)}. That call — not the fall itself — is the
 * kill guarantee: the client-side fall visuals ({@link GBombPhysics}) are cosmetic and degrade on
 * Bedrock, but this FALL {@code DamageSource} lands the kill server-side on both editions.
 *
 * <p>The damage is clamped to {@link GBombParams#KILL_DAMAGE_MIN} in the compact constructor so a
 * degenerate (zero or negative) value can never quietly disarm the finisher.
 *
 * @param damage the FALL-typed damage applied on the slam, never below {@link GBombParams#KILL_DAMAGE_MIN}
 */
public record FinisherSpec(double damage) {

    /** The intended {@code DamageType} name; the runtime resolves this to {@code DamageType.FALL}. */
    public static final String DAMAGE_TYPE = "FALL";

    /** Normalizes the damage so the finisher can never be built with a below-minimum value. */
    public FinisherSpec {
        damage = Math.max(GBombParams.KILL_DAMAGE_MIN, damage);
    }

    /**
     * Builds a spec for the given kill damage, clamping to {@link GBombParams#KILL_DAMAGE_MIN}.
     *
     * @param killDamage the requested FALL finisher damage
     * @return a clamped {@code FinisherSpec}
     */
    public static FinisherSpec of(double killDamage) {
        return new FinisherSpec(killDamage);
    }
}
