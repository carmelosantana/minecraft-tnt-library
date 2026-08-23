/*
 * TNTLibrary - a custom-TNT framework and a set of creative, premium explosives.
 * Copyright (C) 2026 Carmelo Santana
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * See the LICENSE file at the project root for the full license text.
 */
package org.xpfarm.tntlibrary.detonation;

import java.util.Objects;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;
import org.xpfarm.tntlibrary.config.BombSettings;
import org.xpfarm.tntlibrary.core.CustomTnt;
import org.xpfarm.tntlibrary.protect.ProtectionService;

/**
 * The immutable bundle of services handed to a bomb when it detonates.
 *
 * <p>This replaces the old {@code detonate(Location, Entity)} signature: rather than pass just where
 * and who, the framework threads in everything a bomb's effect needs — the tuned {@link
 * BombSettings} for its magnitudes, the {@link Plugin} for scheduling, and the {@link
 * ProtectionService} seam it must respect before changing the world. A bomb reads what it needs and
 * ignores the rest, which is what lets new detonation services be added here without touching every
 * bomb's signature again.
 *
 * <p>Built by {@link Detonator} once per detonation and consumed by {@link CustomTnt#detonate}.
 *
 * @param center the block location the bomb detonates at; never {@code null}
 * @param primer the entity that primed the bomb, or {@code null} for non-entity ignition (a
 *     redstone/command trigger). Carried for attribution and future effects; the Water Bomb does not
 *     require it
 * @param plugin the owning plugin, for scheduling the deferred crater fill and any follow-up tasks;
 *     never {@code null}
 * @param settings this bomb's validated, master-switch-gated settings (radius/fuse/hang); never
 *     {@code null}
 * @param protection the guard consulted before the plugin itself places a block; never {@code null}
 */
public record DetonationContext(
        Location center,
        @Nullable Entity primer,
        Plugin plugin,
        BombSettings settings,
        ProtectionService protection) {

    /** Validates the non-null components. {@code primer} is intentionally allowed to be null. */
    public DetonationContext {
        Objects.requireNonNull(center, "center");
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(protection, "protection");
    }

    /**
     * The world the detonation happens in, taken from {@link #center()}. May be {@code null} only if
     * the center location has been detached from its world (a broken caller); a bomb should treat a
     * null world as "nothing to do".
     */
    public @Nullable World world() {
        return center.getWorld();
    }
}
