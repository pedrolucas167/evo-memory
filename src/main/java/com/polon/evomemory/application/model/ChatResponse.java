package com.polon.evomemory.application.model;

import java.util.UUID;
import java.util.List;

public record ChatResponse(
        UUID episodeId,
        List<MemoryView> memories) {
}
