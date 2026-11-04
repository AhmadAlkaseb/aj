package com.aj.common;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.RemoteJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.util.DefaultResourceRetriever;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.BadJWTException;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import io.javalin.http.ServiceUnavailableResponse;
import io.javalin.http.UnauthorizedResponse;
import java.net.URI;
import java.util.Set;

/** Verify access tokens against the configured provider, never token-supplied key URLs. */
public class TokenVerifier {
    private final DefaultJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
    private final String audience;

    public TokenVerifier(String issuer, String jwksUrl, String audience) {
        this(issuer, audience, remoteKeys(jwksUrl));
    }

    private static JWKSource<SecurityContext> remoteKeys(String jwksUrl) {
        try {
            return new RemoteJWKSet<SecurityContext>(URI.create(jwksUrl).toURL(),
                new DefaultResourceRetriever(2000, 2000, 65536));
        } catch (Exception e) { throw new IllegalArgumentException("Invalid OIDC configuration", e); }
    }

    protected TokenVerifier(String issuer, String audience, JWKSource<SecurityContext> keys) {
        this.audience = audience;
            processor.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, keys));
            var claims = new DefaultJWTClaimsVerifier<SecurityContext>(
                new JWTClaimsSet.Builder().issuer(issuer).build(), Set.of("iss", "sub", "aud", "exp", "iat"));
            claims.setMaxClockSkew(30);
            processor.setJWTClaimsSetVerifier(claims);
    }

    public JWTClaimsSet verify(String authorization) {
        if (authorization == null || authorization.length() > 16384 || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            throw new UnauthorizedResponse("Log ind for at hente din identitet");
        }
        try {
            JWTClaimsSet claims = processor.process(authorization.substring(7).trim(), null);
            if (!claims.getAudience().contains(audience)) throw new BadJWTException("Wrong audience");
            if (!"Bearer".equals(claims.getStringClaim("typ"))) throw new BadJWTException("Not an access token");
            return claims;
        } catch (JOSEException e) {
            throw new ServiceUnavailableResponse("Identitetsudbyderen kan ikke kontaktes. Prøv igen om lidt.");
        } catch (Exception e) {
            throw new UnauthorizedResponse("Login er udløbet eller ugyldigt. Log ind igen.");
        }
    }
}
