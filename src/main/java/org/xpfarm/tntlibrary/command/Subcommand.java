/*
 * TNTLibrary - a custom-TNT framework and a set of creative, premium explosives.
 * Copyright (C) 2026 Carmelo Santana
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * See the LICENSE file at the project root for the full license text.
 */
package org.xpfarm.tntlibrary.command;

import java.util.Locale;
import java.util.Optional;

/**
 * The {@code /tntlibrary} subcommands the executor routes on.
 *
 * <p>Kept as a server-free enum so the routing decision — "is this arg a known subcommand?" — is
 * unit-tested without a {@code CommandSender}. Each constant's lowercase {@link #label()} is both the
 * word a player types and the token offered in tab completion.
 */
public enum Subcommand {
    GIVE,
    LIST,
    RELOAD;

    /** The lowercase word a player types for this subcommand. */
    public String label() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * Resolves a typed argument to its {@link Subcommand}, case-insensitively, or {@link
     * Optional#empty()} for {@code null} or an unrecognised word.
     */
    public static Optional<Subcommand> fromArg(String arg) {
        if (arg == null) {
            return Optional.empty();
        }
        for (Subcommand sub : values()) {
            if (sub.label().equalsIgnoreCase(arg)) {
                return Optional.of(sub);
            }
        }
        return Optional.empty();
    }
}
