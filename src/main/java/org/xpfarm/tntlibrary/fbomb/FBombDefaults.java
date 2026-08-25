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

import java.util.Objects;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Validated, immutable view over the F-Bomb's extra config keys in {@code config.yml}.
 *
 * <p>Build one with {@link #from(ConfigurationSection, Logger)}, which reads under the fixed base
 * path {@code bombs.fbomb.} with the same tolerant discipline as {@code TntLibraryConfig} and
 * {@code SmartBombDefaults}: an absent key falls back to the shipped {@link #FALLBACK} silently
 * (an absent key is not a mistake), while a key that is <em>present but invalid</em> falls back
 * and logs a WARNING naming the key. The factory never throws for a bad operator value and never
 * blocks; one bad key never discards its neighbours. A null {@code root} or {@code logger} is a
 * wiring bug, not an operator typo, and fails fast with {@link NullPointerException}.
 *
 * @param menaceTicks the length, in ticks, of the menace phase before detonation, clamped to
 *     {@link FBombParams#MENACE_TICKS_MIN}..{@link FBombParams#MENACE_TICKS_MAX}
 * @param spawnDistance the distance, in blocks, at which skulls spawn from the F-Bomb, clamped to
 *     {@link FBombParams#SPAWN_DISTANCE_MIN}..{@link FBombParams#SPAWN_DISTANCE_MAX}
 * @param spawnHeight the vertical offset, in blocks, at which skulls spawn, clamped to
 *     {@link FBombParams#SPAWN_HEIGHT_MIN}..{@link FBombParams#SPAWN_HEIGHT_MAX}
 * @param bossbarRange the boss bar visibility range, in blocks, clamped to
 *     {@link FBombParams#BOSSBAR_RANGE_MIN}..{@link FBombParams#BOSSBAR_RANGE_MAX}
 * @param skullCount the number of skulls an F-Bomb spawns, clamped to
 *     {@link FBombParams#SKULL_COUNT_MIN}..{@link FBombParams#SKULL_COUNT_MAX}
 * @param skullCadenceTicks the interval, in ticks, between skull spawns, clamped to
 *     {@link FBombParams#SKULL_CADENCE_TICKS_MIN}..{@link FBombParams#SKULL_CADENCE_TICKS_MAX}
 */
public record FBombDefaults(
        int menaceTicks,
        int spawnDistance,
        int spawnHeight,
        int bossbarRange,
        int skullCount,
        int skullCadenceTicks) {

    /** Fixed base path, with trailing dot, under which every key below is read. */
    private static final String BASE = "bombs.fbomb.";

    /** The shipped defaults, restored whenever a key is absent or invalid. */
    public static final FBombDefaults FALLBACK = new FBombDefaults(60, 12, 6, 48, 6, 8);

    /** Clamps every field to the matching {@link FBombParams} bounds so an out-of-range instance
     *  is unrepresentable, reusing the Task-1 constants rather than hardcoding the bounds. */
    public FBombDefaults {
        menaceTicks = clamp(menaceTicks, FBombParams.MENACE_TICKS_MIN, FBombParams.MENACE_TICKS_MAX);
        spawnDistance =
                clamp(spawnDistance, FBombParams.SPAWN_DISTANCE_MIN, FBombParams.SPAWN_DISTANCE_MAX);
        spawnHeight = clamp(spawnHeight, FBombParams.SPAWN_HEIGHT_MIN, FBombParams.SPAWN_HEIGHT_MAX);
        bossbarRange =
                clamp(bossbarRange, FBombParams.BOSSBAR_RANGE_MIN, FBombParams.BOSSBAR_RANGE_MAX);
        skullCount = clamp(skullCount, FBombParams.SKULL_COUNT_MIN, FBombParams.SKULL_COUNT_MAX);
        skullCadenceTicks = clamp(skullCadenceTicks, FBombParams.SKULL_CADENCE_TICKS_MIN,
                FBombParams.SKULL_CADENCE_TICKS_MAX);
    }

    /**
     * Reads and validates the F-Bomb's extra config keys under {@code bombs.fbomb.}. See the
     * record javadoc for the tolerant read discipline this mirrors from {@code SmartBombDefaults}.
     *
     * @param root the root section -- typically a plugin's {@code FileConfiguration}, or a {@code
     *     YamlConfiguration} loaded directly from a string in tests; must not be null
     * @param logger where WARNINGs about present-but-invalid values are written; must not be null
     */
    public static FBombDefaults from(ConfigurationSection root, Logger logger) {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(logger, "logger");

        int menaceTicks =
                wholeNumberAtLeast(root, BASE + "menace-ticks", 20, FALLBACK.menaceTicks(), logger);
        int spawnDistance = wholeNumberAtLeast(
                root, BASE + "spawn-distance", 2, FALLBACK.spawnDistance(), logger);
        int spawnHeight =
                wholeNumberAtLeast(root, BASE + "spawn-height", 0, FALLBACK.spawnHeight(), logger);
        int bossbarRange = wholeNumberAtLeast(
                root, BASE + "bossbar-range", 8, FALLBACK.bossbarRange(), logger);
        int skullCount =
                wholeNumberAtLeast(root, BASE + "skull-count", 0, FALLBACK.skullCount(), logger);
        int skullCadenceTicks = wholeNumberAtLeast(
                root, BASE + "skull-cadence-ticks", 1, FALLBACK.skullCadenceTicks(), logger);

        return new FBombDefaults(
                menaceTicks, spawnDistance, spawnHeight, bossbarRange, skullCount, skullCadenceTicks);
    }

    /**
     * Builds the {@link FBombParams} a freshly placed F-Bomb starts with. {@code radius} and
     * {@code fuseTicks} come from the bomb's own {@code BombSettings} at the runtime seeding site
     * and are re-clamped by the {@link FBombParams} constructor, so passing the raw config values
     * is safe.
     */
    public FBombParams seed(int radius, int fuseTicks) {
        return new FBombParams(
                radius, fuseTicks, menaceTicks, spawnDistance, spawnHeight, bossbarRange, skullCount,
                skullCadenceTicks);
    }

    private static int clamp(int value, int lo, int hi) {
        return Math.max(lo, Math.min(hi, value));
    }

    /**
     * Reads an int key with an inclusive lower bound, warning on a present-but-invalid value and
     * quoting the value the operator actually wrote. Mirrors {@code SmartBombDefaults.wholeNumberAtLeast}:
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
