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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Pins the no-leaked-state contract for the White Out storm effects: every entity this bomb debuffs is
 * tracked so an abort/cancel/reload can drain and clear it. Simpler than the G-Bomb's
 * {@code GravityLedger} — it stores a set of affected ids with no prior value — but the same idempotent
 * {@code drain()} discipline.
 */
final class EffectLedgerTest {

    @Test
    void recordReturnsTrueWhenNewlyRecorded() {
        EffectLedger ledger = new EffectLedger();
        assertTrue(ledger.record(UUID.randomUUID()));
    }

    @Test
    void duplicateRecordIsNoOpReturningFalseAndDoesNotGrow() {
        EffectLedger ledger = new EffectLedger();
        UUID id = UUID.randomUUID();
        assertTrue(ledger.record(id));
        assertFalse(ledger.record(id));
        assertEquals(1, ledger.size());
    }

    @Test
    void containsTracksMembership() {
        EffectLedger ledger = new EffectLedger();
        UUID id = UUID.randomUUID();
        assertFalse(ledger.contains(id));
        ledger.record(id);
        assertTrue(ledger.contains(id));
    }

    @Test
    void sizeReflectsAdds() {
        EffectLedger ledger = new EffectLedger();
        assertEquals(0, ledger.size());
        ledger.record(UUID.randomUUID());
        ledger.record(UUID.randomUUID());
        assertEquals(2, ledger.size());
    }

    @Test
    void forgetReturnsTrueOnceThenFalse() {
        EffectLedger ledger = new EffectLedger();
        UUID id = UUID.randomUUID();
        ledger.record(id);
        assertTrue(ledger.forget(id));
        assertFalse(ledger.forget(id));
        assertFalse(ledger.contains(id));
    }

    @Test
    void forgetOfUntrackedIdReturnsFalse() {
        EffectLedger ledger = new EffectLedger();
        assertFalse(ledger.forget(UUID.randomUUID()));
    }

    @Test
    void everyRecordedIdIsRetrievableViaDrainThenLedgerIsEmpty() {
        EffectLedger ledger = new EffectLedger();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        ledger.record(a);
        ledger.record(b);

        Set<UUID> drained = ledger.drain();
        assertEquals(2, drained.size());
        assertTrue(drained.contains(a));
        assertTrue(drained.contains(b));
        assertEquals(0, ledger.size());
        assertFalse(ledger.contains(a));
        assertFalse(ledger.contains(b));
    }

    @Test
    void secondDrainIsAnEmptyNonThrowingSet() {
        EffectLedger ledger = new EffectLedger();
        ledger.record(UUID.randomUUID());
        ledger.drain();
        Set<UUID> second = ledger.drain();
        assertTrue(second.isEmpty());
    }

    @Test
    void drainSnapshotIsUnmodifiable() {
        EffectLedger ledger = new EffectLedger();
        ledger.record(UUID.randomUUID());
        Set<UUID> snapshot = ledger.drain();
        assertThrows(UnsupportedOperationException.class, () -> snapshot.add(UUID.randomUUID()));
    }
}
