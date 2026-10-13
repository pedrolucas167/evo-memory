package com.polon.evomemory.domain.memory;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MemoryEvolutionTest {

    @Test
    void confirmationPreservesContentAndIncreasesEvidenceAndConfidence() {
        var next = MemoryEvolution.evolve(original, ChangeType.CONFIRMED, "ignored", 0.2, now);
        assertEquals("old", next.content());
        assertEquals(0.82, next.confidence(), 0.000001);
        assertEquals(4, next.evidenceCount());
        assertEquals(5, next.version());
        assertEquals(now, next.lastConfirmedAt());
        assertEquals(4, original.version());
    }

    private final Instant before = Instant.parse("2026-01-01T00:00:00Z");
    private final Instant now = Instant.parse("2026-01-02T00:00:00Z");
    private final MemoryEvolution.State original = new MemoryEvolution.State(
            "old", 0.8, MemoryStatus.ACTIVE, 3, 4, before, before);

    @Test
    void revisionReplacesContentAndResetsEvidence() {
        for (var change : new ChangeType[]{ChangeType.COMPLEMENTED, ChangeType.CONTRADICTED}) {
            var next = MemoryEvolution.evolve(original, change, "new", 0.6, now);
            assertEquals("new", next.content());
            assertEquals(0.6, next.confidence());
            assertEquals(1, next.evidenceCount());
            assertEquals(5, next.version());
        }
    }

    @Test
    void forgettingPreservesHistoryAndCorrectionReactivates() {
        var forgotten = MemoryEvolution.evolve(original, ChangeType.FORGOTTEN, null, 0, now);
        assertEquals(MemoryStatus.RETRACTED, forgotten.status());
        assertEquals(before, forgotten.lastConfirmedAt());
        assertEquals("old", forgotten.content());
        var corrected = MemoryEvolution.evolve(forgotten, ChangeType.CORRECTED, "corrected", 0, now);
        assertEquals(MemoryStatus.ACTIVE, corrected.status());
        assertEquals(1.0, corrected.confidence());
        assertEquals("corrected", corrected.content());
        assertEquals(6, corrected.version());
    }
}
