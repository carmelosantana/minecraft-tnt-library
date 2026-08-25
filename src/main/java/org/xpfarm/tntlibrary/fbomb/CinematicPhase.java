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

/**
 * The stages of the F-Bomb cinematic, in order: the rig fades in during {@link #SUMMON}, skulls
 * fire on schedule and the boss bar counts down during {@link #MENACE}, the bomb detonates and
 * teardown begins exactly once at {@link #BLAST}, and the cinematic is finished at {@link #DONE}.
 *
 * @see CinematicStateMachine
 */
public enum CinematicPhase {
    SUMMON,
    MENACE,
    BLAST,
    DONE
}
