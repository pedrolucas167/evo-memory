package com.polon.evomemory.infrastructure.llm;
import com.polon.evomemory.application.ports.EmbeddingClient;
import com.polon.evomemory.application.ports.LlmException;

import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.polon.evomemory.config.LlmProperties;

@Component
public class OpenRouterEmbeddingClient implements EmbeddingClient {

    private final LlmProperties properties;
    private final OpenRouterHttp http;

    public OpenRouterEmbeddingClient(LlmProperties properties, OpenRouterHttp http) {
        this.properties = properties;
        this.http = http;
    }

    @Override
    public float[] embed(String text) {
        JsonNode root = http.post("/embeddings", Map.of(
                "model", properties.getEmbeddingModel(),
                "input", text));

        JsonNode values = root.path("data").path(0).path("embedding");
        if (!values.isArray() || values.isEmpty()) {
            throw new LlmException("Resposta de embedding inesperada do OpenRouter");
        }
        float[] vector = new float[values.size()];
        for (int i = 0; i < vector.length; i++) {
            vector[i] = (float) values.get(i).asDouble();
        }
        return vector;
    }
}
