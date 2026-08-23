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

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

/**
 * Verifies the server-free placement math in {@link RigGeometry}: a resting display fills the cell
 * from the corner with no translation, the interaction hitbox is centred on the block's base, and
 * the primed pulse stays centred as it scales.
 */
final class RigGeometryTest {

    private static final float EPS = 1.0e-6f;

    @Test
    void restingDisplayHasZeroTranslationAndUnitScale() {
        assertEquals(new Vector3f(), RigGeometry.displayTranslation());
        assertEquals(1.0f, RigGeometry.DISPLAY_SCALE, EPS);
    }

    @Test
    void interactionSpawnsAtCentreOfBlockBase() {
        assertEquals(new Vector3f(0.5f, 0.0f, 0.5f), RigGeometry.interactionOffset());
        assertEquals(1.0f, RigGeometry.INTERACTION_WIDTH, EPS);
        assertEquals(1.0f, RigGeometry.INTERACTION_HEIGHT, EPS);
    }

    @Test
    void centeredScaleTranslationIsZeroAtUnitScale() {
        assertEquals(new Vector3f(), RigGeometry.centeredScaleTranslation(1.0f));
    }

    @Test
    void centeredScaleTranslationRecentresAGrowingCube() {
        // At scale 1.2 the cube overhangs by 0.2 total, so it shifts back by -0.1 on each axis.
        Vector3f shift = RigGeometry.centeredScaleTranslation(1.2f);
        assertEquals(-0.1f, shift.x(), EPS);
        assertEquals(-0.1f, shift.y(), EPS);
        assertEquals(-0.1f, shift.z(), EPS);
    }

    @Test
    void centeredScaleTranslationRecentresAShrinkingCube() {
        // At scale 0.5 the cube leaves 0.5 of slack, centred by shifting +0.25 on each axis.
        Vector3f shift = RigGeometry.centeredScaleTranslation(0.5f);
        assertEquals(0.25f, shift.x(), EPS);
        assertEquals(0.25f, shift.y(), EPS);
        assertEquals(0.25f, shift.z(), EPS);
    }
}
