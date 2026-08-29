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

/**
 * The four phases a White Out detonation moves through, in order.
 *
 * <p>{@link #PULL} draws every caught entity inward and applies the storm debuffs; {@link #COLLAPSE}
 * teleports stragglers to the center and applies the guaranteed FREEZE finisher; {@link #SWEEP}
 * converts the terrain scar to white concrete ring by ring; {@link #DONE} is the terminal state once
 * the sweep window has passed. The runtime tick loop consults {@link WhiteoutSequence#phaseAt} each
 * tick; only {@code DONE} is terminal.
 */
public enum WhiteoutPhase {
    PULL,
    COLLAPSE,
    SWEEP,
    DONE
}
