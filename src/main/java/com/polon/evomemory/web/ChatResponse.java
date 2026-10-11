package com.polon.evomemory.web;

import java.util.UUID;
import java.util.List;

public record ChatResponse(
        UUID episodeId,
        List<MemoryView> memories) {
}
