package com.polon.evomemory.web;

import java.util.List;

public record AssistantReply(
        String answer,
        List<MemoryView> learned,
        List<MemoryView> context) {
}
