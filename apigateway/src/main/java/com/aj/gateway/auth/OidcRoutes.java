package com.aj.gateway.auth;

import com.aj.common.Api;
import io.javalin.Javalin;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class OidcRoutes {
    private OidcRoutes() {}
    public static void register(Javalin app) {
        String issuer = Api.env("OIDC_ISSUER", "http://localhost:8180/realms/aj");
        var verifier = new TokenVerifier(issuer,
            Api.env("OIDC_JWKS_URL", issuer + "/protocol/openid-connect/certs"), "aj-api");
        app.get("/api/v1/auth/session", ctx -> {
            var claims = verifier.verify(ctx.header("Authorization"));
            var result = new LinkedHashMap<String, Object>();
            result.put("subject", claims.getSubject());
            result.put("username", claims.getStringClaim("preferred_username"));
            result.put("name", claims.getStringClaim("name"));
            result.put("customerGuid", claims.getStringClaim("customerGuid"));
            Map<String, Object> access = claims.getJSONObjectClaim("realm_access");
            result.put("roles", access == null ? List.of() : access.getOrDefault("roles", List.of()));
            ctx.header("Cache-Control", "no-store").json(result);
        });
    }
}
