package com.polon.evomemory.application.ports;

public record LlmRequest(
        String model,
        String systemPrompt,
        String userPrompt,
        double temperature) {
}
