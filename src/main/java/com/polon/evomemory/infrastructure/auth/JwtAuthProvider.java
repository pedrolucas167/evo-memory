package com.polon.evomemory.infrastructure.auth;
import com.polon.evomemory.application.ports.AuthProvider;

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
