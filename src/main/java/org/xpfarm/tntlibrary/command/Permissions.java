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

import java.util.Objects;

/**
 * The permission-node strings this plugin checks, in one place.
 *
 * <p>Every node here is declared in {@code plugin.yml}; {@code PluginDescriptorTest} pins that the
 * descriptor declares each one the code looks up, so a rename here that outran the descriptor is
 * caught before a server ever boots. The command layer uses the three admin nodes; the listener
 * layer uses {@link #use(String)} to gate placing and igniting a specific bomb.
 *
 * <p>Pure string constants — no running server needed — so {@code PermissionsTest} exercises them
 * directly.
 */
public final class Permissions {

    private Permissions() {}

    /** Umbrella admin node; parents the three command nodes in {@code plugin.yml}. */
    public static final String ADMIN = "tntlibrary.admin";

    /** Gate for {@code /tntlibrary give} (and {@code list}). */
    public static final String GIVE = "tntlibrary.command.give";

    /** Gate for {@code /tntlibrary reload}. */
    public static final String RELOAD = "tntlibrary.command.reload";

    /** Prefix for the per-bomb craft/place/ignite node, e.g. {@code tntlibrary.use.waterbomb}. */
    public static final String USE_PREFIX = "tntlibrary.use.";

    /**
     * The per-bomb use node for {@code bombId}, e.g. {@code use("waterbomb")} yields {@code
     * tntlibrary.use.waterbomb} — the node the placement and ignition listeners check.
     *
     * @param bombId a bomb's stable id; never {@code null}
     */
    public static String use(String bombId) {
        return USE_PREFIX + Objects.requireNonNull(bombId, "bombId");
    }
}
