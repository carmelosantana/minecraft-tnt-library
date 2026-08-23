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

import org.joml.Vector3f;

/**
 * The pure placement math for a bomb rig: where its display and interaction entities sit relative to
 * the placed block, and how the primed-state pulse keeps the cube centred while it scales.
 *
 * <p>Split out from {@link TntRig} so the offsets can be unit-tested without a server — none of these
 * methods touch Bukkit. A {@code BlockDisplay} renders its block model from the entity's own corner
 * outward at scale 1, so a display spawned at a block's corner with an identity transform fills the
 * 1x1x1 cell exactly. An {@code Interaction} is centred horizontally on its location and rises from
 * it, so it is spawned at the centre of the block's base.
 */
public final class RigGeometry {

    /** The uniform scale a resting display is drawn at: one block model filling one cell. */
    public static final float DISPLAY_SCALE = 1.0f;

    /** Interaction hitbox width (blocks) — a full cell, per the rig spec. */
    public static final float INTERACTION_WIDTH = 1.0f;

    /** Interaction hitbox height (blocks) — a full cell, per the rig spec. */
    public static final float INTERACTION_HEIGHT = 1.0f;

    private RigGeometry() {}

    /**
     * The resting display's transform translation: none. The display is spawned at the block corner
     * and, at scale 1 with no translation, fills the cell.
     */
    public static Vector3f displayTranslation() {
        return new Vector3f();
    }

    /**
     * Offset from a block's corner to the spawn point of its {@code Interaction} entity: the centre
     * of the block's base, so a {@value #INTERACTION_WIDTH}-wide, {@value #INTERACTION_HEIGHT}-tall
     * hitbox covers the cell.
     */
    public static Vector3f interactionOffset() {
        return new Vector3f(0.5f, 0.0f, 0.5f);
    }

    /**
     * Translation that keeps a corner-anchored cube centred in its cell as it is scaled uniformly to
     * {@code scale}. A block display grows toward +x/+y/+z from its corner, so shifting it by
     * {@code (1 - scale) / 2} on each axis keeps the pulse visually centred rather than lurching to a
     * corner. At {@code scale == 1} this is the zero vector.
     */
    public static Vector3f centeredScaleTranslation(float scale) {
        float shift = (1.0f - scale) / 2.0f;
        return new Vector3f(shift, shift, shift);
    }
}
