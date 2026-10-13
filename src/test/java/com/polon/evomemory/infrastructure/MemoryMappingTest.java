package com.polon.evomemory.infrastructure;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import com.polon.evomemory.domain.memory.*;
import com.polon.evomemory.infrastructure.persistence.SemanticMemory;
import static org.assertj.core.api.Assertions.*;

class MemoryMappingTest {
    @Test
    void mappingPreservesIdentityDatesStateAndValidity() {
        var first = Instant.parse("2026-01-01T00:00:00Z");
        var next = Instant.parse("2026-01-02T00:00:00Z");
        var memory = new Memory(UUID.randomUUID(), "user", MemoryKind.PLAN, "trip", "old", 0.7,
                UUID.randomUUID(), first, "research");
        memory.confirm(next);
        memory.revise("changed", 0.9, next);
        memory.setValidUntil(LocalDate.of(2027, 1, 1));
        var restored = SemanticMemory.fromDomain(memory).toDomain();
        assertThat(restored.state()).isEqualTo(memory.state());
        assertThat(restored.getId()).isEqualTo(memory.getId());
        assertThat(restored.getFirstSeenAt()).isEqualTo(first);
        assertThat(restored.getWing()).isEqualTo("research");
        assertThat(restored.getValidUntil()).isEqualTo(memory.getValidUntil());
        assertThat(restored.getSourceEpisodeId()).isEqualTo(memory.getSourceEpisodeId());
    }
}
