package com.polon.evomemory.application.model;

import java.time.Instant;
import java.util.UUID;

import com.polon.evomemory.domain.memory.MemoryKind;
import com.polon.evomemory.domain.memory.MemoryStatus;
import com.polon.evomemory.domain.memory.Memory;

public record MemoryView(
        UUID id,
        MemoryKind kind,
        String subjectKey,
        String content,
        double confidence,
        MemoryStatus status,
        int version,
        Instant updatedAt) {

    public static MemoryView from(Memory memory) {
        return new MemoryView(
                memory.getId(),
                memory.getKind(),
                memory.getSubjectKey(),
                memory.getContent(),
                memory.getConfidence(),
                memory.getStatus(),
                memory.getVersion(),
                memory.getUpdatedAt());
    }
}
