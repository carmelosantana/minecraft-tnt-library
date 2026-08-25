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

import java.util.Objects;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.xpfarm.tntlibrary.config.BombSettings;

/**
 * Validated, immutable view over the G-Bomb's two bomb-specific config keys in {@code config.yml}.
 *
 * <p>Build one with {@link #from(ConfigurationSection, Logger)}, which reads under the fixed base
 * path {@code bombs.gbomb.} with the same tolerant discipline as {@code SmartBombDefaults}: an absent
 * key falls back to the shipped {@link #FALLBACK} silently (an absent key is not a mistake), while a
 * key that is <em>present but invalid</em> falls back and logs a WARNING naming the key. The factory
 * never throws for a bad operator value and never blocks; one bad key never discards its neighbour. A
 * null {@code root} or {@code logger} is a wiring bug, not an operator typo, and fails fast with
 * {@link NullPointerException}.
 *
 * <p>These are the two gbomb-only keys this factory owns. The G-Bomb's {@code radius} and
 * {@code hang-ticks} live in the shared {@link BombSettings} (which {@code BombType.GBOMB} maps from
 * the {@code radius}/{@code hang-ticks} config keys) and are folded in by {@link #params(BombSettings)},
 * mirroring how {@code SmartBombDefaults} owns only the Smart Bomb's trigger-default keys.
 *
 * @param launchPower the upward launch magnitude, clamped to
 *     {@link GBombParams#LAUNCH_POWER_MIN}..{@link GBombParams#LAUNCH_POWER_MAX}
 * @param killDamage the server-side FALL finisher damage, clamped to
 *     {@link GBombParams#KILL_DAMAGE_MIN}..{@link GBombParams#KILL_DAMAGE_MAX}
 */
public record GBombDefaults(double launchPower, double killDamage) {

    /** Fixed base path, with trailing dot, under which every key below is read. */
    private static final String BASE = "bombs.gbomb.";

    /** The shipped defaults, restored whenever a key is absent or invalid. */
    public static final GBombDefaults FALLBACK = new GBombDefaults(1.2, 1000.0);

    /** Clamps both fields to the {@link GBombParams} bounds so an out-of-range instance is
     *  unrepresentable, reusing the Task-1 constants rather than hardcoding the bounds. */
    public GBombDefaults {
        launchPower = Math.max(GBombParams.LAUNCH_POWER_MIN,
                Math.min(GBombParams.LAUNCH_POWER_MAX, launchPower));
        killDamage = Math.max(GBombParams.KILL_DAMAGE_MIN,
                Math.min(GBombParams.KILL_DAMAGE_MAX, killDamage));
    }

    /**
     * Reads and validates the G-Bomb's two bomb-specific keys under {@code bombs.gbomb.}. See the
     * record javadoc for the tolerant read discipline this mirrors from {@code SmartBombDefaults}.
     *
     * @param root the root section -- typically a plugin's {@code FileConfiguration}, or a {@code
     *     YamlConfiguration} loaded directly from a string in tests; must not be null
     * @param logger where WARNINGs about present-but-invalid values are written; must not be null
     */
    public static GBombDefaults from(ConfigurationSection root, Logger logger) {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(logger, "logger");

        double launchPower = finiteNumberAtLeast(
                root, BASE + "launch-power", 0, FALLBACK.launchPower(), logger);
        double killDamage = finiteNumberAtLeast(
                root, BASE + "kill-damage", 1, FALLBACK.killDamage(), logger);

        return new GBombDefaults(launchPower, killDamage);
    }

    /**
     * Folds this record's two owned values with the shared {@link BombSettings} into a
     * {@link GBombParams}. {@code radius} and {@code hangTicks} come from {@code BombSettings} (which
     * {@code BombType.GBOMB} maps from the {@code radius}/{@code hang-ticks} config keys);
     * {@code launchPower}/{@code killDamage} are the two gbomb-only keys this factory owns, matching
     * the Smart Bomb precedent. Every field is re-clamped by the {@link GBombParams} constructor, so
     * passing the raw {@code settings} values is safe.
     */
    public GBombParams params(BombSettings settings) {
        return new GBombParams(settings.radius(), settings.hangTicks(), launchPower, killDamage);
    }

    /**
     * Reads a fractional-allowed number key with an inclusive lower bound, warning on a
     * present-but-invalid value and quoting the value the operator actually wrote. Adapted from
     * {@code SmartBombDefaults.wholeNumberAtLeast} but permitting fractions: reads the raw object
     * rather than calling {@link ConfigurationSection#getDouble(String, double)}, which silently
     * coerces a non-number to the fallback and so would swallow a typo instead of reporting it. A
     * {@code null} or a {@link ConfigurationSection} written where a scalar was expected is a silent
     * fallback; a non-{@link Number} or a non-finite/out-of-range value warns and falls back.
     */
    private static double finiteNumberAtLeast(
            ConfigurationSection root, String key, double minimum, double fallback, Logger logger) {
        Object raw = root.get(key, null);
        if (raw == null || raw instanceof ConfigurationSection) {
            return fallback;
        }
        if (!(raw instanceof Number number)) {
            warn(key, raw, "a number of " + minimum + " or more", fallback, logger);
            return fallback;
        }
        double written = number.doubleValue();
        if (!Double.isFinite(written) || written < minimum) {
            warn(key, raw, "a number of " + minimum + " or more", fallback, logger);
            return fallback;
        }
        return written;
    }

    private static void warn(
            String key, Object value, String requirement, Object fallback, Logger logger) {
        logger.warning(key + " is " + value + " but must be " + requirement
                + "; falling back to " + fallback + ".");
    }
}
