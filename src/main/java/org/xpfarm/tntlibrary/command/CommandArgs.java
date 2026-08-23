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

import java.util.OptionalInt;

/**
 * Pure argument parsing for {@code /tntlibrary}, split out from the executor so it can be unit-tested
 * with no {@code CommandSender} or running server.
 *
 * <p>The only genuinely server-free decision the command makes is validating a give-amount; player
 * and bomb resolution both need the live server (and the registry) and stay in the executor. Pinning
 * the amount rule here keeps the "1..64, whole number" contract from drifting silently.
 */
public final class CommandArgs {

    private CommandArgs() {}

    /** Smallest amount {@code /tntlibrary give} will hand out. */
    public static final int MIN_AMOUNT = 1;

    /** Largest amount a single {@code /tntlibrary give} will hand out (one full TNT stack). */
    public static final int MAX_AMOUNT = 64;

    /**
     * Parses a give-amount: a whole number in {@code [}{@value #MIN_AMOUNT}{@code ,}
     * {@value #MAX_AMOUNT}{@code ]}. Returns {@link OptionalInt#empty()} for {@code null}, blank,
     * non-numeric, or out-of-range input — the executor turns an empty result into an operator-facing
     * error, so this never throws.
     *
     * @param raw the raw argument as typed, or {@code null}
     */
    public static OptionalInt parseAmount(String raw) {
        if (raw == null) {
            return OptionalInt.empty();
        }
        try {
            int value = Integer.parseInt(raw.trim());
            if (value < MIN_AMOUNT || value > MAX_AMOUNT) {
                return OptionalInt.empty();
            }
            return OptionalInt.of(value);
        } catch (NumberFormatException notANumber) {
            return OptionalInt.empty();
        }
    }
}
