package com.polon.evomemory.llm;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.polon.evomemory.config.LlmProperties;

@Component
public class OpenRouterLlmClient implements LlmClient {

    private final LlmProperties properties;
    private final OpenRouterHttp http;

    public OpenRouterLlmClient(LlmProperties properties, OpenRouterHttp http) {
        this.properties = properties;
        this.http = http;
    }

    @Override
    public String complete(LlmRequest request) {
        Map<String, Object> body = Map.of(
                "model", request.model(),
                "temperature", request.temperature(),
                "messages", List.of(
                        Map.of("role", "system", "content", request.systemPrompt()),
                        Map.of("role", "user", "content", request.userPrompt())));

        JsonNode root = http.post("/chat/completions", body);
        JsonNode content = root.path("choices").path(0).path("message").path("content");
        if (content.isMissingNode()) {
            throw new LlmException("Resposta inesperada do OpenRouter: " + root);
        }
        return content.asText();
    }
}
