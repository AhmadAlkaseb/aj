package com.aj.gateway.auth;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jwt.*;
import io.javalin.http.UnauthorizedResponse;
import org.junit.jupiter.api.*;
import java.time.Instant;
import java.util.Date;
import static org.junit.jupiter.api.Assertions.*;

class TokenVerifierTest {
    private static RSAKey key;
    private TokenVerifier verifier;

    @BeforeAll
    static void keys() throws Exception { key = new RSAKeyGenerator(2048).keyID("provider-key").generate(); }

    @BeforeEach
    void setup() {
        verifier = new TokenVerifier("http://issuer/realms/aj", "aj-api",
            new ImmutableJWKSet<>(new JWKSet(key.toPublicJWK())));
    }

    private String token(String issuer, String audience, Instant expiry, String type, RSAKey signingKey) throws Exception {
        var claims = new JWTClaimsSet.Builder().issuer(issuer).audience(audience).subject("demo-subject")
            .issueTime(new Date()).expirationTime(Date.from(expiry)).claim("typ", type).build();
        var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims);
        jwt.sign(new RSASSASigner(signingKey));
        return "Bearer " + jwt.serialize();
    }

    @Test
    void acceptsCorrectlySignedAccessToken() throws Exception {
        assertEquals("demo-subject", verifier.verify(token("http://issuer/realms/aj", "aj-api", Instant.now().plusSeconds(120), "Bearer", key)).getSubject());
    }

    @Test
    void rejectsMissingAndMalformedTokens() {
        assertThrows(UnauthorizedResponse.class, () -> verifier.verify(null));
        assertThrows(UnauthorizedResponse.class, () -> verifier.verify("Basic credentials"));
        assertThrows(UnauthorizedResponse.class, () -> verifier.verify("Bearer not-a-jwt"));
    }

    @Test
    void rejectsOtherIssuerOrAudience() throws Exception {
        String issuer = token("http://attacker", "aj-api", Instant.now().plusSeconds(120), "Bearer", key);
        String audience = token("http://issuer/realms/aj", "other-api", Instant.now().plusSeconds(120), "Bearer", key);
        assertThrows(UnauthorizedResponse.class, () -> verifier.verify(issuer));
        assertThrows(UnauthorizedResponse.class, () -> verifier.verify(audience));
    }

    @Test
    void rejectsExpiredTokensAndIdentityTokens() throws Exception {
        String expired = token("http://issuer/realms/aj", "aj-api", Instant.now().minusSeconds(120), "Bearer", key);
        String identity = token("http://issuer/realms/aj", "aj-api", Instant.now().plusSeconds(120), "ID", key);
        assertThrows(UnauthorizedResponse.class, () -> verifier.verify(expired));
        assertThrows(UnauthorizedResponse.class, () -> verifier.verify(identity));
    }

    @Test
    void rejectsForgedSignatureEvenWithTrustedKeyId() throws Exception {
        RSAKey attacker = new RSAKeyGenerator(2048).keyID("provider-key").generate();
        String forged = token("http://issuer/realms/aj", "aj-api", Instant.now().plusSeconds(120), "Bearer", attacker);
        assertThrows(UnauthorizedResponse.class, () -> verifier.verify(forged));
    }
}
