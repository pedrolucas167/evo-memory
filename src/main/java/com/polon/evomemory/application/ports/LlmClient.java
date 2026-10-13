package com.polon.evomemory.application.ports;

public interface LlmClient {

    String complete(LlmRequest request);
}
