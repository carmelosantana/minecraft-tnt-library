/*
 * TNTLibrary - a custom-TNT framework and a set of creative, premium explosives.
 * Copyright (C) 2026 Carmelo Santana
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * See the LICENSE file at the project root for the full license text.
 */
package org.xpfarm.tntlibrary.twins;

/**
 * A plain immutable 3D point in continuous (double) space — the beam sampler's output. Unlike
 * {@link TwinLocation} (integer block coordinates tied to a world), a {@code Vec3} is a bare
 * geometric sample the runtime turns into particle spawns; it carries no world id.
 *
 * @param x the x coordinate
 * @param y the y coordinate
 * @param z the z coordinate
 */
public record Vec3(double x, double y, double z) {}
