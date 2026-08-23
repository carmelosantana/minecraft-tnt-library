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
 * The Phase-1 default {@link ProtectionService}: permits every break and every placement.
 *
 * <p>This is the correct default given how detonation is built (see {@link ProtectionService}):
 * block <em>breaking</em> is already filtered natively by region plugins reacting to the real
 * explosion entity, and the Water Bomb only ever <em>places</em> water inside the resulting crater —
 * cells the explosion was, by definition, already allowed to clear. Returning {@code true} here
 * therefore neither destroys nor floods protected terrain in the crater-only design.
 *
 * <p>It is stateless and immutable, so a single instance can be shared. A region-aware replacement
 * that queries a claim API is a later enhancement; nothing else in the detonation layer needs to
 * change to adopt one — it is passed a {@link ProtectionService}, not this concrete type.
 */
public final class AllowAllProtection implements ProtectionService {

    @Override
    public boolean canBreak(Location where) {
        return true;
    }

    @Override
    public boolean canPlace(Location where) {
        return true;
    }
}
