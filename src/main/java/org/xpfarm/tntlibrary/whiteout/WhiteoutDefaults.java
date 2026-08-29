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

import java.util.Objects;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.xpfarm.tntlibrary.config.BombSettings;

/**
 * Validated, immutable view over the White Out's four bomb-specific config keys in {@code config.yml}.
 *
 * <p>Build one with {@link #from(ConfigurationSection, Logger)}, which reads under the fixed base path
 * {@code bombs.whiteout.} with the same tolerant discipline as {@code GBombDefaults}: an absent key
 * falls back to {@link #FALLBACK} silently, while a present-but-invalid key falls back and logs a
 * WARNING naming the key. Never throws for a bad operator value; a null {@code root}/{@code logger} is
 * a wiring bug and fails fast with {@link NullPointerException}.
 *
 * <p>The White Out's {@code radius} ({@code pull-radius}) and {@code fuse-ticks} live in the shared
 * {@link BombSettings} (which {@code BombType.WHITEOUT} maps) and are folded in by
 * {@link #params(BombSettings)}.
 *
 * @param pullPower the escalating inward per-tick velocity ceiling, clamped to
 *     {@link WhiteoutParams#PULL_POWER_MIN}..{@link WhiteoutParams#PULL_POWER_MAX}
 * @param pullTicks the active pull window, clamped to
 *     {@link WhiteoutParams#PULL_TICKS_MIN}..{@link WhiteoutParams#PULL_TICKS_MAX}
 * @param killDamage the collapse FREEZE finisher damage, clamped to
 *     {@link WhiteoutParams#KILL_DAMAGE_MIN}..{@link WhiteoutParams#KILL_DAMAGE_MAX}
 * @param effectTicks the debuff linger, clamped to
 *     {@link WhiteoutParams#EFFECT_TICKS_MIN}..{@link WhiteoutParams#EFFECT_TICKS_MAX}
 */
public record WhiteoutDefaults(double pullPower, int pullTicks, double killDamage, int effectTicks) {

    /** Fixed base path, with trailing dot, under which every key below is read. */
    private static final String BASE = "bombs.whiteout.";

    /** The shipped defaults, restored whenever a key is absent or invalid. */
    public static final WhiteoutDefaults FALLBACK = new WhiteoutDefaults(1.0, 60, 1000.0, 100);

    /** Clamps every field to the {@link WhiteoutParams} bounds so an out-of-range instance is
     *  unrepresentable, reusing the Task-1 constants rather than hardcoding the bounds. */
    public WhiteoutDefaults {
        pullPower = Math.max(WhiteoutParams.PULL_POWER_MIN,
                Math.min(WhiteoutParams.PULL_POWER_MAX, pullPower));
        pullTicks = Math.max(WhiteoutParams.PULL_TICKS_MIN,
                Math.min(WhiteoutParams.PULL_TICKS_MAX, pullTicks));
        killDamage = Math.max(WhiteoutParams.KILL_DAMAGE_MIN,
                Math.min(WhiteoutParams.KILL_DAMAGE_MAX, killDamage));
        effectTicks = Math.max(WhiteoutParams.EFFECT_TICKS_MIN,
                Math.min(WhiteoutParams.EFFECT_TICKS_MAX, effectTicks));
    }

    /**
     * Reads and validates the White Out's four bomb-specific keys under {@code bombs.whiteout.}. See
     * the record javadoc for the tolerant read discipline this mirrors from {@code GBombDefaults}.
     */
    public static WhiteoutDefaults from(ConfigurationSection root, Logger logger) {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(logger, "logger");

        double pullPower = finiteNumberAtLeast(
                root, BASE + "pull-power", 0.0, FALLBACK.pullPower(), logger);
        int pullTicks = wholeNumberAtLeast(
                root, BASE + "pull-ticks", 1, FALLBACK.pullTicks(), logger);
        double killDamage = finiteNumberAtLeast(
                root, BASE + "kill-damage", 1.0, FALLBACK.killDamage(), logger);
        int effectTicks = wholeNumberAtLeast(
                root, BASE + "effect-ticks", 1, FALLBACK.effectTicks(), logger);

        return new WhiteoutDefaults(pullPower, pullTicks, killDamage, effectTicks);
    }

    /**
     * Folds this record's four owned values with the shared {@link BombSettings} into a
     * {@link WhiteoutParams}. {@code radius} comes from {@code BombSettings.radius()} (the bomb's
     * {@code pull-radius}); the other four are the whiteout-only keys this factory owns. Every field is
     * re-clamped by the {@link WhiteoutParams} constructor, so passing the raw settings value is safe.
     */
    public WhiteoutParams params(BombSettings settings) {
        return new WhiteoutParams(settings.radius(), pullPower, pullTicks, killDamage, effectTicks);
    }

    /**
     * Reads a fractional-allowed number key with an inclusive lower bound, warning on a
     * present-but-invalid value and quoting the value the operator wrote. Copied from
     * {@code GBombDefaults.finiteNumberAtLeast}: reads the raw object rather than
     * {@link ConfigurationSection#getDouble(String, double)}, which would silently coerce a non-number.
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

    /**
     * Reads a whole-number key with an inclusive lower bound, warning on a present-but-invalid value.
     * Copied from {@code SmartBombDefaults.wholeNumberAtLeast}: reads {@code doubleValue()} (exact for
     * every int-range integer) and rejects a fraction, non-finite, or out-of-range magnitude rather
     * than truncating/wrapping through {@link ConfigurationSection#getInt(String, int)}.
     */
    private static int wholeNumberAtLeast(
            ConfigurationSection root, String key, int minimum, int fallback, Logger logger) {
        Object raw = root.get(key, null);
        if (raw == null || raw instanceof ConfigurationSection) {
            return fallback;
        }
        if (!(raw instanceof Number number)) {
            warn(key, raw, "a whole number of " + minimum + " or more", fallback, logger);
            return fallback;
        }
        double written = number.doubleValue();
        if (!Double.isFinite(written)
                || written != Math.floor(written)
                || written < minimum
                || written > Integer.MAX_VALUE) {
            warn(key, raw, "a whole number of " + minimum + " or more", fallback, logger);
            return fallback;
        }
        return (int) written;
    }

    private static void warn(
            String key, Object value, String requirement, Object fallback, Logger logger) {
        logger.warning(key + " is " + value + " but must be " + requirement
                + "; falling back to " + fallback + ".");
    }
}
