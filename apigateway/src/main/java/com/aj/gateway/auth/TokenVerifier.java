package com.aj.gateway.auth;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
/** Gateway compatibility facade; domain services use the same verifier. */
public final class TokenVerifier extends com.aj.common.AccessTokenVerifier {
    public TokenVerifier(String issuer,String jwks,String audience) { super(issuer,jwks,audience); }
    TokenVerifier(String issuer,String audience,JWKSource<SecurityContext> keys) { super(issuer,audience,keys); }
}
