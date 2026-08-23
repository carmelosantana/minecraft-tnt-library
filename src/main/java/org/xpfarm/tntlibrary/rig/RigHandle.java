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

import java.util.Objects;
import java.util.UUID;
import org.bukkit.Location;

/**
 * An opaque handle to one placed-bomb rig: the bomb's string id plus the UUIDs of the two entities
 * that make it up (the visible {@code BlockDisplay} and the {@code Interaction} hitbox) and the
 * block cell it occupies.
 *
 * <p>{@link TntRig} hands one of these back from {@code spawnRig} and every other operation
 * ({@code prime}, {@code removeRig}) takes one. It is a plain immutable value carrying only ids and
 * a location, so the wiring and detonation tasks can hold, persist, or compare rigs without touching
 * live entities. Entities are resolved lazily from the UUIDs at call time, which means a handle
 * stays valid across the entities unloading and reloading with their chunk.
 *
 * <p>The {@link Location} is stored as a defensive clone and returned as a clone so callers cannot
 * mutate a rig's recorded cell. Constructing this record needs no running server (a {@code Location}
 * is a plain data holder), so it is unit-tested directly.
 *
 * @param bombId the {@link org.xpfarm.tntlibrary.core.CustomTnt#id()} this rig represents
 * @param blockDisplayId UUID of the {@code BlockDisplay} showing the bomb cube
 * @param interactionId UUID of the {@code Interaction} entity covering the block
 * @param blockLocation the block cell the rig fills
 */
public record RigHandle(String bombId, UUID blockDisplayId, UUID interactionId,
        Location blockLocation) {

    public RigHandle {
        Objects.requireNonNull(bombId, "bombId");
        Objects.requireNonNull(blockDisplayId, "blockDisplayId");
        Objects.requireNonNull(interactionId, "interactionId");
        Objects.requireNonNull(blockLocation, "blockLocation");
        blockLocation = blockLocation.clone();
    }

    /** The block cell this rig occupies. Returned as a clone so the stored value stays immutable. */
    @Override
    public Location blockLocation() {
        return blockLocation.clone();
    }
}
