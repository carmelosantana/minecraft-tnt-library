/*
 * TNTLibrary - a custom-TNT framework and a set of creative, premium explosives.
 * Copyright (C) 2026 Carmelo Santana
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * See the LICENSE file at the project root for the full license text.
 */
package org.xpfarm.tntlibrary.smartbomb;

import java.util.Objects;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Validated, immutable view over the Smart Bomb's trigger-default keys in {@code config.yml}.
 *
 * <p>Build one with {@link #from(ConfigurationSection, Logger)}, which reads under the fixed base
 * path {@code bombs.smartbomb.} with the same tolerant discipline as {@code TntLibraryConfig}: an
 * absent key falls back to the shipped {@link #FALLBACK} silently (an absent key is not a mistake),
 * while a key that is <em>present but invalid</em> falls back and logs a WARNING naming the key. The
 * factory never throws for a bad operator value and never blocks; one bad key never discards its
 * neighbours. A null {@code root} or {@code logger} is a wiring bug, not an operator typo, and fails
 * fast with {@link NullPointerException}.
 *
 * @param proximityRadius the proximity scan radius a freshly placed bomb starts with, clamped to
 *     {@link SmartBombParams#PROXIMITY_RADIUS_MIN}..{@link SmartBombParams#PROXIMITY_RADIUS_MAX}
 * @param proximityDefault whether the proximity trigger is on at placement
 * @param timeTriggerDefault the initial on/off state of the programming UI's time-of-day toggle.
 *     This does <em>not</em> seed a time value: there is deliberately no {@code default-time} key, so
 *     a freshly placed bomb's {@link SmartBombParams#timeTrigger()} is always {@code null} (off)
 *     until the player programs a time (see {@link #seed(int, int)}). It is read and exposed only so
 *     (a) the shipped config key is honored and validated rather than silently ignored, and (b) the
 *     programming UI (Task 6) can use it as its time toggle's initial state.
 */
public record SmartBombDefaults(int proximityRadius, boolean proximityDefault, boolean timeTriggerDefault) {

    /** Fixed base path, with trailing dot, under which every key below is read. */
    private static final String BASE = "bombs.smartbomb.";

    /** The shipped defaults, restored whenever a key is absent or invalid. */
    public static final SmartBombDefaults FALLBACK = new SmartBombDefaults(6, false, false);

    /** Clamps {@code proximityRadius} to the {@link SmartBombParams} bounds so an out-of-range instance
     *  is unrepresentable, reusing the Task-1 constants rather than hardcoding the bounds. */
    public SmartBombDefaults {
        proximityRadius = Math.max(SmartBombParams.PROXIMITY_RADIUS_MIN,
                Math.min(SmartBombParams.PROXIMITY_RADIUS_MAX, proximityRadius));
    }

    /**
     * Reads and validates the Smart Bomb's trigger-default keys under {@code bombs.smartbomb.}. See
     * the record javadoc for the tolerant read discipline this mirrors from {@code TntLibraryConfig}.
     *
     * @param root the root section -- typically a plugin's {@code FileConfiguration}, or a {@code
     *     YamlConfiguration} loaded directly from a string in tests; must not be null
     * @param logger where WARNINGs about present-but-invalid values are written; must not be null
     */
    public static SmartBombDefaults from(ConfigurationSection root, Logger logger) {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(logger, "logger");

        int proximityRadius = wholeNumberAtLeast(
                root, BASE + "default-proximity-radius", 1, FALLBACK.proximityRadius(), logger);
        boolean proximityDefault = booleanOrWarn(
                root, BASE + "proximity-default", FALLBACK.proximityDefault(), logger);
        boolean timeTriggerDefault = booleanOrWarn(
                root, BASE + "time-trigger-default", FALLBACK.timeTriggerDefault(), logger);

        return new SmartBombDefaults(proximityRadius, proximityDefault, timeTriggerDefault);
    }

    /**
     * Builds the {@link SmartBombParams} a freshly placed Smart Bomb starts with. {@code radius} and
     * {@code delayTicks} come from the bomb's own {@code BombSettings} at the runtime seeding site
     * (Task 7) and are re-clamped by the {@link SmartBombParams} constructor, so passing the raw
     * config values is safe. {@code timeTrigger} is always {@code null} at placement -- there is no
     * {@code default-time} key, so a freshly placed bomb's time trigger is off until the player
     * programs a time (see {@code timeTriggerDefault} on the record).
     */
    public SmartBombParams seed(int radius, int delayTicks) {
        return new SmartBombParams(radius, delayTicks, null, proximityDefault, proximityRadius);
    }

    /**
     * Reads a boolean key, warning on a present-but-wrong-typed value and staying silent on an absent
     * one. Mirrors {@code TntLibraryConfig.booleanOrWarn}: reads the raw object so a quoted string
     * such as {@code "yes"} (not a YAML boolean) is reported rather than silently coerced, while a
     * {@link ConfigurationSection} written where a scalar was expected is a structural mistake whose
     * Bukkit-internal {@code toString} must never reach an operator's WARNING.
     */
    private static boolean booleanOrWarn(
            ConfigurationSection root, String key, boolean fallback, Logger logger) {
        Object raw = root.get(key, null);
        if (raw == null || raw instanceof ConfigurationSection) {
            return fallback;
        }
        if (raw instanceof Boolean bool) {
            return bool;
        }
        warn(key, raw, "true or false", fallback, logger);
        return fallback;
    }

    /**
     * Reads an int key with an inclusive lower bound, warning on a present-but-invalid value and
     * quoting the value the operator actually wrote. Mirrors {@code TntLibraryConfig.wholeNumberAtLeast}:
     * reads the raw object rather than calling {@link ConfigurationSection#getInt(String, int)}, which
     * funnels every {@code Number} through {@code Number#intValue()} -- truncating a fraction and
     * <em>wrapping</em> an out-of-range magnitude, so a range check would only ever see a number the
     * operator never typed. Reading {@code doubleValue()} is exact for every int-range integer and
     * keeps an out-of-range or unrepresentable magnitude out of range.
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
