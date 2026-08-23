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

import java.util.Locale;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Region/claim protection back-end selected by {@code protection.provider}.
 *
 * <p>{@link #AUTO} is both the shipped default and the safe fallback: an absent, blank, or
 * unrecognised value all resolve to {@code AUTO} so a typo never disables protection or throws.
 * Only a value that is <em>present and non-blank yet unrecognised</em> is a misconfiguration and
 * earns a WARNING naming the key -- a blank value is treated as "left at the default", not as a
 * mistake, exactly like an absent key.
 */
public enum ProtectionProvider {
    AUTO,
    WORLDGUARD,
    GRIEFPREVENTION,
    NONE;

    /**
     * Reads a provider token from {@code root} at {@code key}, case-insensitively, never throwing.
     *
     * <p>Resolution: an absent or blank value yields {@link #AUTO} silently (it is the documented
     * default). A present, non-blank value that matches an enum constant ignoring case yields that
     * constant. Anything else -- {@code provider: banana}, a list, a mapping -- yields {@link #AUTO}
     * and logs a WARNING naming {@code key} and the offending value.
     *
     * @param root the root section to read from; must not be null
     * @param key the dotted path of the provider key, e.g. {@code protection.provider}
     * @param logger where the unknown-value WARNING is written; must not be null
     */
    public static ProtectionProvider from(ConfigurationSection root, String key, Logger logger) {
        // Read the raw object rather than getString: getString calls toString on any non-null value,
        // so a mapping written where a scalar was expected would push a Bukkit-internal
        // MemorySection toString into the WARNING below. An absent key and that structural mistake
        // both collapse to the blank-default path here, silently -- neither is a wrong token.
        Object rawObject = root.get(key, null);
        if (rawObject == null || rawObject instanceof ConfigurationSection) {
            return AUTO;
        }
        String raw = rawObject.toString();
        String normalised = raw.trim().toLowerCase(Locale.ROOT);
        if (normalised.isEmpty()) {
            return AUTO;
        }
        for (ProtectionProvider provider : values()) {
            if (provider.name().toLowerCase(Locale.ROOT).equals(normalised)) {
                return provider;
            }
        }
        logger.warning(key + " is '" + raw + "' but must be one of auto, worldguard, "
                + "griefprevention, none (case-insensitive); falling back to auto.");
        return AUTO;
    }
}
