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

/**
 * Pure descriptor of the White Out's guaranteed server-side kill, capturing the two params that can be
 * asserted headlessly: the damage value and the fact the {@code DamageType} is {@code FREEZE}.
 *
 * <p>The runtime edge ({@code FreezeFinisher}) turns this into
 * {@code DamageSource.builder(DamageType.FREEZE).withDamageLocation(loc).build()} followed by
 * {@code ((Damageable) entity).damage(damage, damageSource)}. That call — not the pull or the freeze
 * visuals — is the kill guarantee: it lands server-side on Java and Bedrock alike.
 *
 * <p>The damage is clamped to {@link WhiteoutParams#KILL_DAMAGE_MIN} in the compact constructor so a
 * degenerate value can never quietly disarm the finisher.
 *
 * @param damage the FREEZE-typed damage applied at the collapse, never below
 *     {@link WhiteoutParams#KILL_DAMAGE_MIN}
 */
public record FreezeFinisherSpec(double damage) {

    /** The intended {@code DamageType} name; the runtime resolves this to {@code DamageType.FREEZE}. */
    public static final String DAMAGE_TYPE = "FREEZE";

    /** Normalizes the damage so the finisher can never be built with a below-minimum value. */
    public FreezeFinisherSpec {
        damage = Math.max(WhiteoutParams.KILL_DAMAGE_MIN, damage);
    }

    /**
     * Builds a spec for the given kill damage, clamping to {@link WhiteoutParams#KILL_DAMAGE_MIN}.
     */
    public static FreezeFinisherSpec of(double killDamage) {
        return new FreezeFinisherSpec(killDamage);
    }
}
