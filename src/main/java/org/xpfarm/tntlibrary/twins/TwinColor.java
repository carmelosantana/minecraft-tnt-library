/*
 * TNTLibrary - a custom-TNT framework and a set of creative, premium explosives.
 * Copyright (C) 2026 Carmelo Santana
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * See the LICENSE file at the project root for the full license text.
 */
package org.xpfarm.tntlibrary.twins;

import java.util.Optional;

/**
 * The inverse colour pair for "The Twins" bomb, and the single source of the Twin id strings.
 *
 * <p>Each Twin ships in two variants — {@link #WHITE} and {@link #BLACK} — that share one base
 * config/permission id ({@link #BASE_ID}). This enum owns the variant-id ↔ base-id mapping so the
 * orchestrator's {@code BombType.baseId} can delegate here rather than duplicating the literals.
 *
 * <p>Pure logic only: this enum touches no {@link org.bukkit.Bukkit} API and has no dependency on
 * any other Twins class, so it is fully unit-testable headless. The variant ids
 * ({@code "twins_white"}, {@code "twins_black"}) and the base id ({@code "twins"}) are STABLE
 * strings persisted on real items (registry keys / PDC values) — never change them once shipped.
 */
public enum TwinColor {

    WHITE("twins_white"),
    BLACK("twins_black");

    /** The base config/permission id both variants resolve to. */
    public static final String BASE_ID = "twins";

    private final String variantId;

    TwinColor(String variantId) {
        this.variantId = variantId;
    }

    /** This variant's stable registry/PDC id, e.g. {@code "twins_white"}. Never changes once shipped. */
    public String variantId() {
        return variantId;
    }

    /** The opposite colour — the colour a Twin of this colour pairs with. */
    public TwinColor opposite() {
        return this == WHITE ? BLACK : WHITE;
    }

    /** The colour whose variantId equals {@code id}, or empty if {@code id} is not a Twin variant. */
    public static Optional<TwinColor> fromVariantId(String id) {
        for (TwinColor colour : values()) {
            if (colour.variantId.equals(id)) {
                return Optional.of(colour);
            }
        }
        return Optional.empty();
    }

    /** {@code true} iff {@code id} is one of the two Twin variant ids. */
    public static boolean isVariant(String id) {
        return fromVariantId(id).isPresent();
    }

    /** The base id for a Twin variant id ({@code "twins"}); any non-variant id returns itself unchanged. */
    public static String baseId(String id) {
        return isVariant(id) ? BASE_ID : id;
    }
}
