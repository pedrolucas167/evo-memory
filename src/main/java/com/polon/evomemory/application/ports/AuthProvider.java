package com.polon.evomemory.application.ports;

public interface AuthProvider extends com.polon.evomemory.application.ports.AuthPort {
    boolean validate(String token);
    String extractUserId(String token);
}
