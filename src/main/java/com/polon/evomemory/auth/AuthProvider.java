package com.polon.evomemory.auth;

public interface AuthProvider {
    boolean validate(String token);
    String extractUserId(String token);
}
