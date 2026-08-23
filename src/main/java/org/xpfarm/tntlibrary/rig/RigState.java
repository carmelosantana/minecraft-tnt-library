/*
 * TNTLibrary - a custom-TNT framework and a set of creative, premium explosives.
 * Copyright (C) 2026 Carmelo Santana
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * See the LICENSE file at the project root for the full license text.
 */
package org.xpfarm.tntlibrary.rig;

import java.util.Optional;
import org.xpfarm.tntlibrary.core.Keys;

/**
 * The lifecycle state of a placed bomb rig, as stored under {@link Keys#RIG_STATE} in each rig
 * entity's persistent-data container.
 *
 * <p>The enum constants own their exact on-the-wire string ({@link #wire()}) so the value written to
 * an entity PDC and read back by the detonation layer is defined in one place. A newly placed,
 * inert bomb is {@link #PLACED}; once its fuse is running it is {@link #PRIMED}. This is pure,
 * server-free data — {@code RigStateTest} pins the wire strings.
 */
public enum RigState {

    /** A placed, inert bomb: sitting in the world, fuse not yet running. */
    PLACED("placed"),

    /** A primed bomb: fuse running, awaiting the fuse-elapsed moment that the detonation layer owns. */
    PRIMED("primed");

    private final String wire;

    RigState(String wire) {
        this.wire = wire;
    }

    /** The exact string persisted under {@link Keys#RIG_STATE}. Never changes once shipped. */
    public String wire() {
        return wire;
    }

    /**
     * Resolves a persisted wire string back to its {@link RigState}, or {@link Optional#empty()} if
     * the value is {@code null} or unrecognised (e.g. written by a newer version).
     */
    public static Optional<RigState> fromWire(String wire) {
        if (wire == null) {
            return Optional.empty();
        }
        for (RigState state : values()) {
            if (state.wire.equals(wire)) {
                return Optional.of(state);
            }
        }
        return Optional.empty();
    }
}
