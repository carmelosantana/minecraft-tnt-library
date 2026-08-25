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

/**
 * A plain immutable 3D point in continuous (double) space — the G-Bomb launch/hang/slam geometry's
 * pure sample. It carries no world id; the runtime turns it into velocity and position work at the
 * Bukkit edge. This is a G-Bomb-local copy, deliberately independent of any other package's point
 * type so {@code org.xpfarm.tntlibrary.gbomb} stays self-contained.
 *
 * @param x the x coordinate
 * @param y the y coordinate
 * @param z the z coordinate
 */
public record Vec3(double x, double y, double z) {}
