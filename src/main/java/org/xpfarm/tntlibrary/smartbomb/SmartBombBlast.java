/*
 * TNTLibrary - a custom-TNT framework and a set of creative, premium explosives.
 * Copyright (C) 2026 Carmelo Santana
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * See the LICENSE file at the project root for the full license text.
 */
package org.xpfarm.tntlibrary.smartbomb;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.persistence.PersistentDataType;
import org.xpfarm.tntlibrary.core.Keys;

/**
 * The shared "boom" for every Smart Bomb detonation path.
 *
 * <p>Both {@link SmartBomb#detonate} (the generic fuse fallback, using the config radius) and the
 * per-block watcher (Task 5, using the programmed radius) must produce the same explosion, so that one
 * blast lives here rather than being duplicated at each site. Keeping it static and stateless makes the
 * DRY intent explicit: there is exactly one way a Smart Bomb pops.
 *
 * <h2>Why a real primed entity</h2>
 *
 * <p>Like the {@code WaterBomb}, this spawns a genuine {@link TNTPrimed} rather than calling {@code
 * World#createExplosion}. A real entity's {@link org.bukkit.event.entity.EntityExplodeEvent} is
 * filtered by WorldGuard and GriefPrevention, so protected terrain is removed from the block list
 * before the crater lands — the explosion is region-safe for free.
 *
 * <h2>The detonation tag is deliberately inert here</h2>
 *
 * <p>The spawned entity is tagged with {@link Keys#DETONATION_ID} = {@link SmartBomb#ID}
 * ({@code "smartbomb"}) so the detonation layer recognises it as one of ours. That tag is
 * <em>deliberately ignored</em> by {@code DetonationListener}, which only runs its post-blast effect
 * (the Water Bomb's crater flood) for {@link org.xpfarm.tntlibrary.item.WaterBomb#ID}. A Smart Bomb
 * therefore gets region-safe destruction with no post-blast effect — the "smart" part is entirely in
 * <em>when</em> it fires, not in what the blast does afterwards.
 *
 * <p>Server-dependent (spawns an entity); verified at the runtime gate, not in JUnit.
 */
public final class SmartBombBlast {

    /** Lowest explosion power, so a misconfigured {@code radius} still pops. */
    public static final float MIN_POWER = 1.0f;

    /** Upper clamp on explosion power — keeps the blast modest, matching {@code WaterBomb}. */
    public static final float MAX_POWER = 8.0f;

    private SmartBombBlast() {}

    /**
     * Spawns the tagged, non-incendiary explosion at {@code center}. The {@code radius} is clamped to
     * [{@value #MIN_POWER}, {@value #MAX_POWER}] and used as the TNT yield. A null {@code world} (a
     * detached center) is a no-op, mirroring {@code WaterBomb}.
     *
     * @param world the world to detonate in, or {@code null} for a detached center (no-op)
     * @param center the block location the blast is centred on
     * @param radius the desired explosion radius; clamped before use as the yield
     */
    public static void detonate(World world, Location center, int radius) {
        if (world == null) {
            return; // detached location; nothing to detonate against
        }
        float power = Math.max(MIN_POWER, Math.min(MAX_POWER, radius));
        world.spawn(center, TNTPrimed.class, tnt -> {
            tnt.setYield(power);
            tnt.setIsIncendiary(false);
            tnt.setFuseTicks(0); // explode on the next tick, driving the EntityExplodeEvent
            tnt.getPersistentDataContainer()
                    .set(Keys.DETONATION_ID, PersistentDataType.STRING, SmartBomb.ID);
        });
    }
}
