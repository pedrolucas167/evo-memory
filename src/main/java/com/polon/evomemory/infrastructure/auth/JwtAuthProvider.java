package com.polon.evomemory.infrastructure.auth;
import com.polon.evomemory.application.ports.AuthProvider;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.Assert;
import java.net.URI;
import java.util.Set;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.RemoteJWKSet;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;

@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "memory.auth.mode", havingValue = "jwks", matchIfMissing = true)
public class JwtAuthProvider implements AuthProvider {

    private final DefaultJWTProcessor<SecurityContext> processor;

    @Autowired
    public JwtAuthProvider(@Value("${memory.auth.jwks-uri:}") String jwksUri,
                           @Value("${memory.auth.issuer:}") String issuer,
                           @Value("${memory.auth.audience:}") String audience) {
        this(remoteKeys(jwksUri), issuer, audience);
    }

    JwtAuthProvider(JWKSource<SecurityContext> keys, String issuer, String audience) {
        Assert.hasText(issuer, "memory.auth.issuer is required");
        Assert.hasText(audience, "memory.auth.audience is required");
        processor = new DefaultJWTProcessor<>();
        processor.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, keys));
        processor.setJWTClaimsSetVerifier(new DefaultJWTClaimsVerifier<>(Set.of(audience),
                new JWTClaimsSet.Builder().issuer(issuer).build(), Set.of("sub", "exp"), Set.of()));
    }

    private static JWKSource<SecurityContext> remoteKeys(String jwksUri) {
        Assert.hasText(jwksUri, "memory.auth.jwks-uri is required");
        URI uri = URI.create(jwksUri);
        Assert.isTrue("https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null,
                "memory.auth.jwks-uri must be an HTTPS URL");
        try {
            return new RemoteJWKSet<>(uri.toURL());
        } catch (java.net.MalformedURLException error) {
            throw new IllegalArgumentException("Invalid JWKS URL", error);
        }
    }

    @Override
    public boolean validate(String token) {
        return extractUserId(token) != null;
    }

    @Override
    public String extractUserId(String token) {
        if (token == null || !token.regionMatches(true, 0, "Bearer ", 0, 7)) return null;
        String encoded = token.substring(7).trim();
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
