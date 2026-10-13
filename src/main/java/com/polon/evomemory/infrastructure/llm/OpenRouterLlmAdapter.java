package com.polon.evomemory.infrastructure.llm;

import com.polon.evomemory.application.ports.LlmPort;
import com.polon.evomemory.application.ports.LlmRequest;
import org.springframework.stereotype.Component;

@Component
public class OpenRouterLlmAdapter implements LlmPort {

    private final OpenRouterLlmClient client;

    public OpenRouterLlmAdapter(OpenRouterLlmClient client) {
        this.client = client;
    }

    @Override
    public String generate(LlmRequest request) {
        return client.complete(request);
    }
}
