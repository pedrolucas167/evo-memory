package com.polon.evomemory.application.ports;

public interface LlmPort {
    String generate(LlmRequest request);
}
