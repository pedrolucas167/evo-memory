package com.polon.evomemory.domain.memory;

import java.time.Instant;

/**
 * Regra de domínio pura: decide evolução de uma memória.
 * Sem Spring, sem banco, sem LLM.
 */
public class MemoryEvolution {

    public record State(String content, double confidence, MemoryStatus status,
                        int evidenceCount, int version, Instant lastConfirmedAt, Instant updatedAt) {}

    public static State evolve(State current, ChangeType change, String content,
                               double confidence, Instant now) {
        return switch (change) {
            case CONFIRMED -> new State(current.content(),
                    Math.min(1.0, current.confidence() + 0.1 * (1 - current.confidence())),
                    current.status(), current.evidenceCount() + 1, current.version() + 1, now, now);
            case COMPLEMENTED, CONTRADICTED -> new State(content, confidence, current.status(),
                    1, current.version() + 1, now, now);
            case CORRECTED -> new State(content, 1.0, MemoryStatus.ACTIVE,
                    current.evidenceCount(), current.version() + 1, now, now);
            case FORGOTTEN -> new State(current.content(), current.confidence(), MemoryStatus.RETRACTED,
                    current.evidenceCount(), current.version() + 1, current.lastConfirmedAt(), now);
            case CREATED -> throw new IllegalArgumentException("Use creation for the first observation");
        };
    }
}
