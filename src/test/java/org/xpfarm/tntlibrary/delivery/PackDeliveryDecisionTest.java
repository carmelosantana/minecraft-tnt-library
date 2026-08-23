/*
 * TNTLibrary - a custom-TNT framework and a set of creative, premium explosives.
 * Copyright (C) 2026 Carmelo Santana
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * See the LICENSE file at the project root for the full license text.
 */
package org.xpfarm.tntlibrary.delivery;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Exhaustive truth-table test of {@link PackDeliveryDecision#shouldSend(boolean, boolean)} -- the
 * entire "should we send this player the Java resource pack?" decision.
 */
final class PackDeliveryDecisionTest {

    @Test
    void sendsWhenDeliveryEnabledAndPlayerIsJava() {
        assertTrue(PackDeliveryDecision.shouldSend(true, false));
    }

    @Test
    void skipsWhenDeliveryDisabledEvenIfPlayerIsJava() {
        assertFalse(PackDeliveryDecision.shouldSend(false, false));
    }

    @Test
    void skipsBedrockPlayerEvenWhenDeliveryEnabled() {
        assertFalse(PackDeliveryDecision.shouldSend(true, true));
    }

    @Test
    void skipsWhenDeliveryDisabledAndPlayerIsBedrock() {
        assertFalse(PackDeliveryDecision.shouldSend(false, true));
    }
}
