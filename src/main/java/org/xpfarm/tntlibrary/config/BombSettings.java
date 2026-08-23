/*
 * TNTLibrary - a custom-TNT framework and a set of creative, premium explosives.
 * Copyright (C) 2026 Carmelo Santana
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * See the LICENSE file at the project root for the full license text.
 */
package org.xpfarm.tntlibrary.config;

/**
 * Immutable, validated settings for a single bomb.
 *
 * <p>The six shipped bombs do not share one key vocabulary -- one bomb calls its size {@code
 * radius}, another {@code pull-radius}, another {@code default-radius}; one calls its timing {@code
 * fuse-ticks}, another {@code default-delay-ticks} -- so this record stores each concept once under
 * a neutral name and {@link BombType} owns the per-bomb mapping from those config keys onto these
 * fields. The wiring layer therefore reads {@code bomb.radius()} without caring which key the
 * operator's file spelled it under.
 *
 * @param enabled whether this bomb is active. Note this already folds in the master switch: {@link
 *     TntLibraryConfig} forces it to {@code false} for every bomb when {@code enabled: false} at the
 *     file root, so a caller never has to re-check the master flag.
 * @param radius the bomb's size in blocks -- the value of whichever of {@code radius},
 *     {@code pull-radius}, or {@code default-radius} the bomb defines; {@code 0} for a bomb that has
 *     no radius concept at all (the F-Bomb)
 * @param fuseTicks the bomb's primary delay in ticks -- the value of whichever of {@code fuse-ticks}
 *     or {@code default-delay-ticks} the bomb defines
 * @param hangTicks the G-Bomb's {@code hang-ticks} (how long it floats before detonating);
 *     {@code 0} for every other bomb, which has no hang phase
 */
public record BombSettings(boolean enabled, int radius, int fuseTicks, int hangTicks) {

    /**
     * The result of looking up an unknown or unconfigured bomb id: disabled, all magnitudes zero.
     * Returning this rather than {@code null} keeps {@link TntLibraryConfig#bomb(String)} total, so
     * the wiring layer never has to null-check a lookup.
     */
    public static final BombSettings DISABLED = new BombSettings(false, 0, 0, 0);

    /**
     * This bomb with {@link #enabled()} forced to {@code false}, preserving its magnitudes. Used by
     * {@link TntLibraryConfig} to apply the master switch; returns {@code this} unchanged when it is
     * already disabled, so the common case allocates nothing.
     */
    public BombSettings asDisabled() {
        return enabled ? new BombSettings(false, radius, fuseTicks, hangTicks) : this;
    }
}
