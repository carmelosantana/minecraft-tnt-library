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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Pins the gravity-restore safety contract — the #1 review gate. Every id added to the ledger must be
 * restorable regardless of path (normal slam, task cancel, plugin disable, chunk/entity unload, server
 * stop), the first recorded prior gravity value must survive a re-launch, and {@code drain()} must be
 * idempotent so a slam-restore followed by a disable-restore can never double-toggle or throw.
 */
final class GravityLedgerTest {

    @Test
    void recordReturnsTrueWhenNewlyRecorded() {
        GravityLedger ledger = new GravityLedger();
        assertTrue(ledger.record(UUID.randomUUID(), true));
    }

    @Test
    void duplicateRecordIsNoOpReturningFalseAndDoesNotGrow() {
        GravityLedger ledger = new GravityLedger();
        UUID id = UUID.randomUUID();
        assertTrue(ledger.record(id, true));
        assertFalse(ledger.record(id, false));
        assertEquals(1, ledger.size());
    }

    @Test
    void duplicateRecordPreservesTheFirstPriorGravityValue() {
        GravityLedger ledger = new GravityLedger();
        UUID id = UUID.randomUUID();
        ledger.record(id, true); // prior gravity was ON
        ledger.record(id, false); // a re-launch must NOT overwrite the true prior value
        assertEquals(Optional.of(true), ledger.forget(id));
    }

    @Test
    void sizeReflectsAdds() {
        GravityLedger ledger = new GravityLedger();
        assertEquals(0, ledger.size());
        ledger.record(UUID.randomUUID(), true);
        ledger.record(UUID.randomUUID(), false);
        assertEquals(2, ledger.size());
    }

    @Test
    void containsTracksMembership() {
        GravityLedger ledger = new GravityLedger();
        UUID id = UUID.randomUUID();
        assertFalse(ledger.contains(id));
        ledger.record(id, true);
        assertTrue(ledger.contains(id));
    }

    @Test
    void forgetReturnsThePriorFlagOnceThenEmpty() {
        GravityLedger ledger = new GravityLedger();
        UUID id = UUID.randomUUID();
        ledger.record(id, false);
        assertEquals(Optional.of(false), ledger.forget(id));
        assertEquals(Optional.empty(), ledger.forget(id));
        assertFalse(ledger.contains(id));
    }

    @Test
    void forgetOfUntrackedIdReturnsEmpty() {
        GravityLedger ledger = new GravityLedger();
        assertEquals(Optional.empty(), ledger.forget(UUID.randomUUID()));
    }

    @Test
    void everyRecordedIdIsRetrievableViaDrainThenLedgerIsEmpty() {
        GravityLedger ledger = new GravityLedger();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        ledger.record(a, true);
        ledger.record(b, false);

        Map<UUID, Boolean> restored = ledger.drain();
        assertEquals(2, restored.size());
        assertEquals(true, restored.get(a));
        assertEquals(false, restored.get(b));
        assertEquals(0, ledger.size());
        assertFalse(ledger.contains(a));
        assertFalse(ledger.contains(b));
    }

    @Test
    void secondDrainIsAnEmptyNonThrowingMap() {
        GravityLedger ledger = new GravityLedger();
        ledger.record(UUID.randomUUID(), true);
        ledger.drain();
        Map<UUID, Boolean> second = ledger.drain();
        assertTrue(second.isEmpty());
    }

    @Test
    void recordForgetDrainLeavesLedgerEmptyWithNoException() {
        GravityLedger ledger = new GravityLedger();
        UUID id = UUID.randomUUID();
        ledger.record(id, true);
        ledger.forget(id); // normal slam restore
        Map<UUID, Boolean> leftover = ledger.drain(); // disable restore
        assertTrue(leftover.isEmpty());
        assertEquals(0, ledger.size());
    }

    @Test
    void recordDrainDrainLeavesLedgerEmptyWithNoException() {
        GravityLedger ledger = new GravityLedger();
        ledger.record(UUID.randomUUID(), true);
        ledger.drain(); // normal slam restore (batch)
        Map<UUID, Boolean> leftover = ledger.drain(); // disable restore
        assertTrue(leftover.isEmpty());
        assertEquals(0, ledger.size());
    }

    @Test
    void drainSnapshotIsUnmodifiable() {
        GravityLedger ledger = new GravityLedger();
        ledger.record(UUID.randomUUID(), true);
        Map<UUID, Boolean> snapshot = ledger.drain();
        assertThrows(UnsupportedOperationException.class, () -> snapshot.put(UUID.randomUUID(), false));
    }
}
