package com.polon.evomemory.application.ports;

public interface AuthPort {
    String extractUserId(String authorization);
}
