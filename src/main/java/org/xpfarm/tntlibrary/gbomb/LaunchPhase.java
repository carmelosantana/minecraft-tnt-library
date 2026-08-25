/*
 * TNTLibrary - a custom-TNT framework and a set of creative, premium explosives.
 * Copyright (C) 2026 Carmelo Santana
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * See the LICENSE file at the project root for the full license text.
 */
package org.xpfarm.tntlibrary.gbomb;

/**
 * The four phases a G-Bomb detonation moves through, in order.
 *
 * <p>{@link #LAUNCH} arms and throws the targets upward, {@link #HANG} holds them at apex with gravity
 * disabled, {@link #SLAM} restores gravity and applies the FALL finisher on the boundary tick, and
 * {@link #DONE} is the terminal state once the slam tick has passed. The runtime tick loop consults
 * {@link GBombSequence#phaseAt(long, long)} each tick to decide which phase an in-flight detonation is
 * in; only {@code DONE} is terminal.
 */
public enum LaunchPhase {
    LAUNCH,
    HANG,
    SLAM,
    DONE
}
