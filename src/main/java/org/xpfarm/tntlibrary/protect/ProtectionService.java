/*
 * TNTLibrary - a custom-TNT framework and a set of creative, premium explosives.
 * Copyright (C) 2026 Carmelo Santana
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * See the LICENSE file at the project root for the full license text.
 */
package org.xpfarm.tntlibrary.protect;

import org.bukkit.Location;

/**
 * The seam a bomb's detonation asks before it changes the world at a given cell.
 *
 * <p>Phase 1 does <em>not</em> ship a region-aware adapter. The framework leans on <b>native
 * explosion protection</b> for the destructive half of a blast: because a bomb detonates via a real
 * {@link org.bukkit.entity.TNTPrimed} entity, WorldGuard's and GriefPrevention's own {@code
 * EntityExplodeEvent} handlers have already stripped protected blocks out of {@code
 * event.blockList()} before this plugin sees it. Protected terrain therefore never breaks and never
 * becomes part of the crater — so the Water Bomb's crater-only fill inherits that protection with
 * zero integration code.
 *
 * <p>This interface exists for the <em>constructive</em> half — the block placement (water) the
 * plugin performs itself, which no explosion event guards. The shipped {@link AllowAllProtection}
 * answers {@code true} everywhere, which is correct for the Phase-1 crater-only fill (the crater can
 * only contain cells the explosion was already allowed to break). A full region-aware implementation
 * — one that also refuses water placement inside protected <em>air</em> a player could otherwise
 * flood — is a deliberately deferred enhancement, not a bug in this phase. See the class javadoc of
 * {@code org.xpfarm.tntlibrary.detonation.DetonationListener} for how the two halves compose.
 */
public interface ProtectionService {

    /**
     * Whether a bomb may break the block at {@code where}. Phase 1 relies on native explosion
     * protection to filter breakage, so this is consulted only by any future non-explosion breakage
     * path; {@link AllowAllProtection} returns {@code true}.
     *
     * @param where the block location in question; never {@code null}
     * @return {@code true} if breaking is permitted here
     */
    boolean canBreak(Location where);

    /**
     * Whether the plugin may place a block (e.g. a Water Bomb water source) at {@code where}. This is
     * the guard that matters for the crater fill: every candidate flood cell is checked before water
     * is set. {@link AllowAllProtection} returns {@code true}.
     *
     * @param where the block location in question; never {@code null}
     * @return {@code true} if placement is permitted here
     */
    boolean canPlace(Location where);
}
