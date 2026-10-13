package com.polon.evomemory.application.auth;

import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final com.polon.evomemory.application.ports.AuthPort provider;

    public AuthService(com.polon.evomemory.application.ports.AuthPort provider) {
        this.provider = provider;
    }

    public String authenticate(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            return null;
        }
        return provider.extractUserId(authorization);
    }
}
