package com.polon.evomemory.infrastructure.llm;
import com.polon.evomemory.application.ports.TransientLlmException;
import com.polon.evomemory.application.ports.LlmException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.polon.evomemory.config.LlmProperties;

@Component
public class OpenRouterHttp {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(120);
    private static final int MAX_ATTEMPTS = 3;

    private final LlmProperties properties;
    private final ObjectMapper mapper;
    private final HttpClient httpClient;

    public OpenRouterHttp(LlmProperties properties, ObjectMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    public JsonNode post(String path, Object body) {
        String payload;
        try {
            payload = mapper.writeValueAsString(body);
        } catch (Exception error) {
            throw new LlmException("Falha ao serializar a requisição", error);
        }

        TransientLlmException last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return send(path, payload);
            } catch (TransientLlmException error) {
                last = error;
                pause(attempt);
            }
        }
        throw last;
    }

    private JsonNode send(String path, String payload) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(properties.getBaseUrl() + path))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status == 429 || status >= 500) {
                throw new TransientLlmException("OpenRouter respondeu " + status);
            }
            if (status >= 400) {
                throw new LlmException("OpenRouter respondeu " + status);
            }
            return mapper.readTree(response.body());
        } catch (LlmException error) {
            throw error;
        } catch (IOException error) {
            throw new TransientLlmException("Falha ao chamar a LLM", error);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new LlmException("Chamada à LLM interrompida", error);
        } catch (Exception error) {
            throw new LlmException("Falha ao chamar a LLM", error);
        }
    }

    private void pause(int attempt) {
        long base = Math.max(0, properties.getRetryBackoffMillis());
        try {
            Thread.sleep(base * (1L << (attempt - 1)));
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        }
    }

    private static String truncate(String text) {
        return text.length() > 500 ? text.substring(0, 500) + "..." : text;
    }
}
