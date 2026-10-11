package com.polon.evomemory.llm;

public record LlmRequest(
        String model,
        String systemPrompt,
        String userPrompt,
        double temperature) {
}
