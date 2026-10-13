package com.polon.evomemory.application.model;

import java.util.List;

public record AssistantReply(
        String answer,
        List<MemoryView> learned,
        List<MemoryView> context) {
}
