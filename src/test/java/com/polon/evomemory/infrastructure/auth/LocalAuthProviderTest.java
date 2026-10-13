package com.polon.evomemory.infrastructure.auth;

import java.nio.file.Path;
import java.nio.file.Files;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.MACSigner;
import static org.assertj.core.api.Assertions.*;

class LocalAuthProviderTest {
    @TempDir Path directory;

    @Test
    void locallySignedTokensAreVerifiedAndTheKeyPersists() throws Exception {
        Path path = directory.resolve("secret");
        var provider = new LocalAuthProvider(path.toString(), "127.0.0.1");
        byte[] key = Base64.getDecoder().decode(Files.readString(path));
        var claims = new JWTClaimsSet.Builder().issuer("evo-memory-local").audience("evo-memory")
                .subject("benchmark-user").expirationTime(Date.from(Instant.now().plusSeconds(300))).build();
        var token = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        token.sign(new MACSigner(key));
        assertThat(provider.extractUserId("Bearer " + token.serialize())).isEqualTo("benchmark-user");
        assertThat(new LocalAuthProvider(path.toString(), "127.0.0.1")
                .extractUserId("Bearer " + token.serialize())).isEqualTo("benchmark-user");
        byte[] wrong = key.clone();
        wrong[0] ^= 1;
        var fake = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        fake.sign(new MACSigner(wrong));
        assertThat(provider.extractUserId("Bearer " + fake.serialize())).isNull();
    }

    @Test
    void localAuthenticationCannotBindToTheNetwork() {
        assertThatThrownBy(() -> new LocalAuthProvider(directory.resolve("secret").toString(), "0.0.0.0"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(directory.resolve("secret")).doesNotExist();
    }
}
