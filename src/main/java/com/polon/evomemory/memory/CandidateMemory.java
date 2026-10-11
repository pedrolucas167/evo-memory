package com.polon.evomemory.memory;

import java.time.LocalDate;

public record CandidateMemory(MemoryKind kind, String subjectKey, String content, double confidence,
                              LocalDate validUntil) {

    public CandidateMemory {
        if (confidence < 0 || confidence > 1) {
            throw new IllegalArgumentException("confidence deve estar entre 0 e 1");
        }
    }

    public CandidateMemory(MemoryKind kind, String subjectKey, String content, double confidence) {
        this(kind, subjectKey, content, confidence, null);
    }
}
