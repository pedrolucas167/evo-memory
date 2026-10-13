package com.polon.evomemory.infrastructure.auth;
import com.polon.evomemory.application.ports.AuthProvider;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;

@Component
@Profile("!prod")
@ConditionalOnProperty(name = "memory.auth.mode", havingValue = "local")
public class LocalAuthProvider implements AuthProvider {
    private final DefaultJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();

    public LocalAuthProvider(@Value("${memory.auth.local-key-file:.local/auth-secret}") String keyFile,
                             @Value("${server.address:}") String bindAddress) throws java.io.IOException {
        if (!Set.of("127.0.0.1", "::1", "localhost").contains(bindAddress)) {
            throw new IllegalArgumentException("Local authentication requires a loopback server.address");
        }
        Path path = Path.of(keyFile);
        if (!Files.exists(path)) {
            if (path.toAbsolutePath().getParent() != null) Files.createDirectories(path.toAbsolutePath().getParent());
            byte[] generated = new byte[32];
            new SecureRandom().nextBytes(generated);
            try {
                Files.writeString(path, Base64.getEncoder().encodeToString(generated), StandardOpenOption.CREATE_NEW);
            } catch (java.nio.file.FileAlreadyExistsException ignored) {
                // Another local process created the same key first.
            }
        }
        byte[] secret = Base64.getDecoder().decode(Files.readString(path).trim());
        if (secret.length < 32) throw new IllegalArgumentException("Local authentication requires a 256-bit key");
        var keys = new ImmutableJWKSet<SecurityContext>(new JWKSet(new OctetSequenceKey.Builder(secret).build()));
        processor.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.HS256, keys));
        processor.setJWTClaimsSetVerifier(new DefaultJWTClaimsVerifier<>(Set.of("evo-memory"),
                new JWTClaimsSet.Builder().issuer("evo-memory-local").build(), Set.of("sub", "exp"), Set.of()));
    }

    @Override
    public boolean validate(String token) { return extractUserId(token) != null; }

    @Override
    public String extractUserId(String authorization) {
        if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) return null;
        String encoded = authorization.substring(7).trim();
        if (encoded.isEmpty() || encoded.length() > 16384) return null;
        try {
            String subject = processor.process(encoded, null).getSubject();
            return subject != null && !subject.isBlank() && subject.length() <= 100 ? subject : null;
        } catch (java.text.ParseException | com.nimbusds.jose.JOSEException
                 | com.nimbusds.jose.proc.BadJOSEException error) {
            return null;
        }
    }
}
