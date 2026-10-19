package com.polon.evomemory.infrastructure.llm;
import com.polon.evomemory.application.ports.LlmRequest;
import com.polon.evomemory.application.ports.LlmException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.polon.evomemory.config.LlmProperties;
import com.sun.net.httpserver.HttpServer;

class OpenRouterLlmClientTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void returnsContentFromOpenRouterFormat() throws Exception {
        AtomicReference<String> capturedBody = new AtomicReference<>();
        AtomicReference<String> capturedAuth = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            if ("POST".equals(exchange.getRequestMethod())) {
                capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                capturedAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
                String response = """
                        {"choices": [{"message": {"role": "assistant", "content": "resposta da llm"}}]}""";
                byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(bytes);
                }
            } else {
                exchange.sendResponseHeaders(404, -1);
            }
        });
        server.start();
        try {
            LlmProperties properties = new LlmProperties();
            properties.setRetryBackoffMillis(0);
            properties.setBaseUrl("http://localhost:" + server.getAddress().getPort());
            properties.setApiKey("test-key");
            OpenRouterLlmClient client = new OpenRouterLlmClient(properties, new OpenRouterHttp(properties, mapper));

            String content = client.complete(new LlmRequest("test-model", "sistema", "usuário", 0.0));

            assertThat(content).isEqualTo("resposta da llm");
            assertThat(capturedAuth.get()).isEqualTo("Bearer test-key");
            assertThat(capturedBody.get()).contains("test-model", "\"role\":\"system\"", "\"role\":\"user\"");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void throwsOnHttpError() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            byte[] bytes = "{\"error\": \"invalid api key\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(401, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
        try {
            LlmProperties properties = new LlmProperties();
            properties.setRetryBackoffMillis(0);
            properties.setBaseUrl("http://localhost:" + server.getAddress().getPort());
            properties.setApiKey("bad-key");
            OpenRouterLlmClient client = new OpenRouterLlmClient(properties, new OpenRouterHttp(properties, mapper));

            assertThatThrownBy(() -> client.complete(new LlmRequest("m", "s", "u", 0.0)))
                    .isInstanceOf(LlmException.class)
                    .hasMessageContaining("401");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void throwsOnUnexpectedResponseShape() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            byte[] bytes = "{\"unexpected\": true}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
        try {
            LlmProperties properties = new LlmProperties();
            properties.setRetryBackoffMillis(0);
            properties.setBaseUrl("http://localhost:" + server.getAddress().getPort());
            properties.setApiKey("key");
            OpenRouterLlmClient client = new OpenRouterLlmClient(properties, new OpenRouterHttp(properties, mapper));

            assertThatThrownBy(() -> client.complete(new LlmRequest("m", "s", "u", 0.0)))
                    .isInstanceOf(LlmException.class)
                    .hasMessageContaining("Resposta inesperada");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void retriesTransientServerErrorThenSucceeds() throws Exception {
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            boolean first = calls.incrementAndGet() == 1;
            String response = first
                    ? "{\"error\": \"indisponível\"}"
                    : "{\"choices\": [{\"message\": {\"content\": \"ok\"}}]}";
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(first ? 503 : 200, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
        try {
            LlmProperties properties = new LlmProperties();
            properties.setRetryBackoffMillis(0);
            properties.setBaseUrl("http://localhost:" + server.getAddress().getPort());
            properties.setApiKey("key");
            OpenRouterLlmClient client = new OpenRouterLlmClient(properties, new OpenRouterHttp(properties, mapper));

            assertThat(client.complete(new LlmRequest("m", "s", "u", 0.0))).isEqualTo("ok");
            assertThat(calls.get()).isEqualTo(2);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void doesNotRetryPermanentClientError() throws Exception {
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            calls.incrementAndGet();
            byte[] bytes = "{\"error\": \"chave inválida\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(401, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
        try {
            LlmProperties properties = new LlmProperties();
            properties.setRetryBackoffMillis(0);
            properties.setBaseUrl("http://localhost:" + server.getAddress().getPort());
            properties.setApiKey("bad");
            OpenRouterLlmClient client = new OpenRouterLlmClient(properties, new OpenRouterHttp(properties, mapper));

            assertThatThrownBy(() -> client.complete(new LlmRequest("m", "s", "u", 0.0)))
                    .isInstanceOf(LlmException.class)
                    .hasMessageContaining("401");
            assertThat(calls.get()).isEqualTo(1);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void wrapsIoFailure() {
        LlmProperties properties = new LlmProperties();
        properties.setRetryBackoffMillis(0);
        properties.setBaseUrl("http://localhost:1");
        properties.setApiKey("key");
        OpenRouterLlmClient client = new OpenRouterLlmClient(properties, new OpenRouterHttp(properties, mapper));

        assertThatThrownBy(() -> client.complete(new LlmRequest("m", "s", "u", 0.0)))
                .isInstanceOf(LlmException.class)
                .hasMessageContaining("Falha ao chamar a LLM");
    }
}
