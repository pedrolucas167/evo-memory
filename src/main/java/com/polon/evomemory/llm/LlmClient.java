package com.polon.evomemory.llm;

public interface LlmClient {

    String complete(LlmRequest request);
}
