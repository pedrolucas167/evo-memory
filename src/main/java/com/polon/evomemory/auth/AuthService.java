package com.polon.evomemory.auth;

import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final AuthProvider provider;

    public AuthService(AuthProvider provider) {
        this.provider = provider;
    }

    public String authenticate(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            return null;
        }
        if (!provider.validate(authorization)) {
            return null;
        }
        return provider.extractUserId(authorization);
    }
}
