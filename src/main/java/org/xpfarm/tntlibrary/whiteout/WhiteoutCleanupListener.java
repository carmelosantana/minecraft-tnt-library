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

import java.util.Objects;
import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * The unload safety net for the White Out's no-leaked-state gate. This bomb applies blindness, slowness,
 * and freeze ticks to caught entities. Potion effects and freeze ticks persist across chunk/entity
 * unload and reload, so an entity that leaves the world mid-storm — its chunk unloads, it dies, or it is
 * otherwise removed — would keep this bomb's debuffs in its persisted state if the sequence's own clear
 * never ran. This listener closes that hole: whenever an entity is removed from the world, it clears
 * this bomb's effects immediately via {@link WhiteoutRuntime#clearEffects(org.bukkit.entity.Entity)},
 * passing the still-valid event entity so the corrected state is written before it serializes. A
 * non-tracked entity (the common case) is ignored for free.
 *
 * <p>Handles {@link EntityRemoveFromWorldEvent}, which fires on chunk unload, death, and every other
 * removal — one handler covers all the unload cases. On Paper 26.1.2 this event resolves under
 * {@code com.destroystokyo.paper.event.entity} (verified in {@code gbomb/GravityRestoreListener}).
 *
 * <p>Server-dependent; verified at the runtime gate (gate 12), not in JUnit — mirroring
 * {@code gbomb/GravityRestoreListener}.
 */
public final class WhiteoutCleanupListener implements Listener {

    private final WhiteoutRuntime runtime;

    public WhiteoutCleanupListener(WhiteoutRuntime runtime) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
    }

    @EventHandler
    public void onEntityRemoveFromWorld(EntityRemoveFromWorldEvent event) {
        runtime.clearEffects(event.getEntity());
    }
}
