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

/**
 * Pure, server-free codec for {@link SmartBombParams}.
 *
 * <p>Three responsibilities, deliberately kept headless so they unit-test without a running server:
 *
 * <ul>
 *   <li><b>Persistence</b> — {@link #serialize} / {@link #parse} round-trip params through a compact
 *       {@code key=value,key=value} string. Parsing is intentionally forgiving: anything it cannot make
 *       sense of falls back to {@link SmartBombParams#DEFAULT} for that field rather than throwing, so a
 *       hand-edited or version-skewed persisted value can never crash a load.
 *   <li><b>Command edits</b> — {@link #applyKeyValue} validates one {@code key value} pair and returns
 *       an {@link Edit} carrying either the new params or an operator-facing error message. Returning a
 *       result instead of throwing keeps ordinary bad input off the exception path.
 *   <li><b>Display</b> — {@link #describe} renders a human one-liner for {@code /tntlibrary smart get}.
 * </ul>
 */
public final class ParamCodec {

    private ParamCodec() {}

    /** Canonical key for explosion radius. */
    public static final String KEY_RADIUS = "radius";

    /** Canonical key for the base fuse delay. */
    public static final String KEY_DELAY = "delay";

    /** Canonical key for the world time-of-day trigger. */
    public static final String KEY_TIME = "time";

    /** Canonical key for the proximity-arming flag. */
    public static final String KEY_PROXIMITY = "proximity";

    /** Canonical key for the proximity scan radius. */
    public static final String KEY_PROXIMITY_RADIUS = "proximity-radius";

    private static final String VALID_KEYS =
            KEY_RADIUS + ", " + KEY_DELAY + ", " + KEY_TIME + ", " + KEY_PROXIMITY + ", "
                    + KEY_PROXIMITY_RADIUS;

    /**
     * Outcome of a single command edit: exactly one of {@code params} / {@code error} is non-null.
     * Built through {@link #ok} / {@link #err} so callers can branch on {@code error() == null} instead
     * of catching an exception.
     */
    public record Edit(SmartBombParams params, String error) {

        /** A successful edit carrying the new params. */
        public static Edit ok(SmartBombParams params) {
            return new Edit(params, null);
        }

        /** A rejected edit carrying an operator-facing message. */
        public static Edit err(String message) {
            return new Edit(null, message);
        }
    }

    /**
     * Renders params to the canonical persistence string, e.g.
     * {@code radius=4,delay=100,time=,proximity=false,proximity-radius=6}. {@code time} is empty when the
     * trigger is off. Field order is fixed so the output is stable.
     */
    public static String serialize(SmartBombParams p) {
        return KEY_RADIUS + "=" + p.radius()
                + "," + KEY_DELAY + "=" + p.delayTicks()
                + "," + KEY_TIME + "=" + (p.timeTrigger() == null ? "" : p.timeTrigger())
                + "," + KEY_PROXIMITY + "=" + p.proximity()
                + "," + KEY_PROXIMITY_RADIUS + "=" + p.proximityRadius();
    }

    /**
     * Parses a persistence string back into params, tolerating anything malformed. Recognised keys set
     * their field (clamped by the record); unknown keys are ignored; missing keys and garbage numeric
     * tokens keep {@link SmartBombParams#DEFAULT}'s value; an empty, absent, or non-numeric {@code time}
     * yields a {@code null} trigger. A {@code null} or blank input returns {@code DEFAULT}. Guarantees
     * {@code parse(serialize(p)).equals(p)} for any {@code p}.
     */
    public static SmartBombParams parse(String s) {
        SmartBombParams result = SmartBombParams.DEFAULT;
        if (s == null || s.isBlank()) {
            return result;
        }
        for (String token : s.split(",")) {
            int eq = token.indexOf('=');
            if (eq < 0) {
                continue;
            }
            String key = token.substring(0, eq).trim();
            String value = token.substring(eq + 1).trim();
            switch (key) {
                case KEY_RADIUS -> result = result.withRadius(parseIntOr(value, result.radius()));
                case KEY_DELAY -> result = result.withDelayTicks(parseIntOr(value, result.delayTicks()));
                case KEY_TIME -> result = result.withTimeTrigger(parseLongOrNull(value));
                case KEY_PROXIMITY -> result = result.withProximity(Boolean.parseBoolean(value));
                case KEY_PROXIMITY_RADIUS ->
                        result = result.withProximityRadius(parseIntOr(value, result.proximityRadius()));
                default -> {
                    // Unrecognised key: ignore so old/new schema versions can coexist.
                }
            }
        }
        return result;
    }

    /**
     * Applies one {@code key value} edit onto {@code base}, validating the value against the target
     * field's rules. Returns an {@link Edit} with the new params on success or an exact operator-facing
     * message on failure; never throws for ordinary bad input.
     */
    public static Edit applyKeyValue(SmartBombParams base, String key, String rawValue) {
        String value = rawValue == null ? "" : rawValue.trim();
        switch (key) {
            case KEY_RADIUS:
                return applyInt(value, KEY_RADIUS, SmartBombParams.RADIUS_MIN, SmartBombParams.RADIUS_MAX,
                        base::withRadius);
            case KEY_DELAY:
                return applyInt(value, KEY_DELAY, SmartBombParams.DELAY_MIN, SmartBombParams.DELAY_MAX,
                        base::withDelayTicks);
            case KEY_PROXIMITY_RADIUS:
                return applyInt(value, KEY_PROXIMITY_RADIUS, SmartBombParams.PROXIMITY_RADIUS_MIN,
                        SmartBombParams.PROXIMITY_RADIUS_MAX, base::withProximityRadius);
            case KEY_TIME:
                return applyTime(base, value);
            case KEY_PROXIMITY:
                return applyProximity(base, value);
            default:
                return Edit.err("Unknown key '" + key + "'. Valid keys: " + VALID_KEYS + ".");
        }
    }

    /** Human-readable one-liner for {@code /tntlibrary smart get}. */
    public static String describe(SmartBombParams p) {
        String time = p.timeTrigger() == null ? "off" : String.valueOf(p.timeTrigger());
        return "radius=" + p.radius()
                + ", delay=" + p.delayTicks() + " ticks"
                + ", time-trigger=" + time
                + ", proximity=" + (p.proximity() ? "on" : "off")
                + ", proximity-radius=" + p.proximityRadius();
    }

    private interface IntEdit {
        SmartBombParams apply(int value);
    }

    private static Edit applyInt(String value, String key, int min, int max, IntEdit edit) {
        int parsed;
        try {
            parsed = Integer.parseInt(value);
        } catch (NumberFormatException notANumber) {
            return Edit.err(key + " must be a whole number.");
        }
        if (parsed < min || parsed > max) {
            return Edit.err(key + " must be between " + min + " and " + max + ".");
        }
        return Edit.ok(edit.apply(parsed));
    }

    private static Edit applyTime(SmartBombParams base, String value) {
        String lower = value.toLowerCase();
        if (lower.isEmpty() || lower.equals("off") || lower.equals("none")) {
            return Edit.ok(base.withTimeTrigger(null));
        }
        long parsed;
        try {
            parsed = Long.parseLong(value);
        } catch (NumberFormatException notANumber) {
            return timeRangeError();
        }
        if (parsed < SmartBombParams.TIME_TRIGGER_MIN || parsed > SmartBombParams.TIME_TRIGGER_MAX) {
            return timeRangeError();
        }
        return Edit.ok(base.withTimeTrigger(parsed));
    }

    private static Edit timeRangeError() {
        return Edit.err("time must be between " + SmartBombParams.TIME_TRIGGER_MIN + " and "
                + SmartBombParams.TIME_TRIGGER_MAX + ", or 'off'.");
    }

    private static Edit applyProximity(SmartBombParams base, String value) {
        switch (value.toLowerCase()) {
            case "true", "on", "yes", "1" -> {
                return Edit.ok(base.withProximity(true));
            }
            case "false", "off", "no", "0" -> {
                return Edit.ok(base.withProximity(false));
            }
            default -> {
                return Edit.err("proximity must be true or false.");
            }
        }
    }

    private static int parseIntOr(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException notANumber) {
            return fallback;
        }
    }

    private static Long parseLongOrNull(String value) {
        if (value.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }
}
