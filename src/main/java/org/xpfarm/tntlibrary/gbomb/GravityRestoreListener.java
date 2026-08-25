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

import java.util.Objects;
import java.util.UUID;
import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * The mandatory unload safety net for the G-Bomb's #1 review gate — gravity restore on every path.
 *
 * <p>The G-Bomb disables entity gravity ({@code setGravity(false)}) for its launch→hang→slam sequence.
 * The {@code NoGravity} NBT flag persists across chunk/entity unload, so an entity that leaves the
 * world mid-hang — its chunk unloads, it dies, or it is otherwise removed — would be left gravity-off
 * in its persisted NBT forever if the sequence's own restore never ran. This listener closes that hole:
 * whenever a tracked entity is removed from the world, it restores that entity's gravity immediately
 * via {@link GBombRuntime#restore(UUID)} (idempotent — a later slam- or disable-restore of the same id
 * is a harmless no-op).
 *
 * <h2>Chosen event</h2>
 *
 * <p>This handles {@link EntityRemoveFromWorldEvent}, which fires on chunk unload, death, and every
 * other removal — one handler covers all the unload cases, so there is no need to iterate a chunk's
 * entities on {@code ChunkUnloadEvent}. On Paper 26.1.2 this event resolves under the
 * {@code com.destroystokyo.paper.event.entity} package (verified against the
 * {@code paper-api-26.1.2} jar via {@code javap}), not {@code org.bukkit.event.entity}; it is the same
 * event, and it extends {@link org.bukkit.event.entity.EntityEvent} so {@link
 * EntityRemoveFromWorldEvent#getEntity()} is available. The {@code ChunkUnloadEvent} fallback in the
 * brief is therefore unnecessary — the preferred event exists and is used.
 *
 * <p>Server-dependent (it consumes a live Bukkit event and resolves entities through the runtime), so
 * this class is verified at the runtime gate (gate 12) rather than in JUnit — mirroring {@code
 * smartbomb/SmartBombWatcher} and {@code smartbomb/SmartBombListener}.
 */
public final class GravityRestoreListener implements Listener {

    private final GBombRuntime runtime;

    /**
     * @param runtime the shared runtime carrying the one {@link GravityLedger} and the restore path;
     *     never {@code null}
     */
    public GravityRestoreListener(GBombRuntime runtime) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
    }

    /**
     * Restores gravity for a tracked entity the moment it is removed from the world, before its
     * gravity-off state can be persisted to NBT. A non-tracked entity (the overwhelmingly common case)
     * is ignored, so this stays cheap on the hot unload path.
     *
     * @param event the removal event, fired on chunk unload, death, or removal
     */
    @EventHandler
    public void onEntityRemoveFromWorld(EntityRemoveFromWorldEvent event) {
        Entity entity = event.getEntity();
        UUID id = entity.getUniqueId();
        if (runtime.ledger().contains(id)) {
            runtime.restore(id);
        }
    }
}
