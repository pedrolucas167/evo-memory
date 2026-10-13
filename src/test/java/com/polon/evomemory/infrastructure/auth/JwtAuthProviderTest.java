package com.polon.evomemory.infrastructure.auth;

import java.time.Instant;
import java.util.Date;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtAuthProviderTest {
    private static RSAKey key;

    @BeforeAll
    static void generateKey() throws Exception {
        key = new RSAKeyGenerator(2048).generate();
    }

    private JwtAuthProvider provider() {
        return new JwtAuthProvider(new ImmutableJWKSet<>(new JWKSet(key.toPublicJWK())),
                "https://issuer.example", "evo-memory");
    }

    private JWTClaimsSet.Builder claims() {
        return new JWTClaimsSet.Builder().issuer("https://issuer.example").audience("evo-memory")
                .subject("owner").expirationTime(Date.from(Instant.now().plusSeconds(300)));
    }

    private String token(JWTClaimsSet claims, RSAKey signer) throws Exception {
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
        jwt.sign(new RSASSASigner(signer));
        return "Bearer " + jwt.serialize();
    }

    @Test
    void acceptsVerifiedSubject() throws Exception {
        assertThat(provider().extractUserId(token(claims().build(), key))).isEqualTo("owner");
    }

    @Test
    void rejectsInvalidSignatureAndUnsignedToken() throws Exception {
        assertThat(provider().extractUserId(token(claims().build(), new RSAKeyGenerator(2048).generate())))
                .isNull();
        assertThat(provider().extractUserId("Bearer " + new PlainJWT(claims().build()).serialize())).isNull();
    }

    @Test
    void rejectsWrongIssuerOrAudience() throws Exception {
        assertThat(provider().extractUserId(token(claims().issuer("https://other.example").build(), key))).isNull();
        assertThat(provider().extractUserId(token(claims().audience("other").build(), key))).isNull();
    }

    @Test
    void rejectsExpiredAndNotYetValidTokens() throws Exception {
        assertThat(provider().extractUserId(token(claims()
                .expirationTime(Date.from(Instant.now().minusSeconds(120))).build(), key))).isNull();
        assertThat(provider().extractUserId(token(claims()
                .notBeforeTime(Date.from(Instant.now().plusSeconds(300))).build(), key))).isNull();
    }

    @Test
    void requiresExpirationAndNonblankSubject() throws Exception {
        assertThat(provider().extractUserId(token(claims().expirationTime(null).build(), key))).isNull();
        assertThat(provider().extractUserId(token(claims().subject(null).build(), key))).isNull();
        assertThat(provider().extractUserId(token(claims().subject(" ").build(), key))).isNull();
    }

    @Test
    void rejectsMissingMalformedAndOversizedTokens() {
        assertThat(provider().extractUserId(null)).isNull();
        assertThat(provider().extractUserId("Bearer malformed-token")).isNull();
        assertThat(provider().extractUserId("Bearer " + "x".repeat(16385))).isNull();
    }

    @Test
    void configurationMustBeExplicitAndUseHttps() {
        assertThatThrownBy(() -> new JwtAuthProvider("", "", "")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JwtAuthProvider("http://issuer.example/keys", "issuer", "audience"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JwtAuthProvider(new ImmutableJWKSet<>(new JWKSet(key.toPublicJWK())),
                "", "audience")).isInstanceOf(IllegalArgumentException.class);
    }
}
