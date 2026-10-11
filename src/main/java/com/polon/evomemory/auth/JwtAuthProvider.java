package com.polon.evomemory.auth;

import org.springframework.stereotype.Component;

@Component
public class JwtAuthProvider implements AuthProvider {

    @Override
    public boolean validate(String token) {
        return token != null && token.startsWith("Bearer ") && token.length() > 20;
    }

    @Override
    public String extractUserId(String token) {
        if (!validate(token)) return null;
        // Simples: parte após Bearer, até o primeiro ponto; em produção usaria JWT.
        String payload = token.substring(7);
        int dot = payload.indexOf('.');
        return dot > 0 ? payload.substring(0, dot) : payload;
    }
}
