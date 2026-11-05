package com.aj.common;

import io.javalin.Javalin;
import io.javalin.http.*;
import com.nimbusds.jwt.JWTClaimsSet;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Tokens are verified independently at every business API, including direct ports. */
public final class Access {
    private Access() {}
    public record Principal(String subject, UUID customer, boolean admin) {}
    public static Principal from(JWTClaimsSet claims) {
        try {
            Map<String,Object> access=claims.getJSONObjectClaim("realm_access");
            Object roles=access==null ? List.of() : access.getOrDefault("roles",List.of());
            boolean admin=roles instanceof List<?> list && list.contains("admin");
            boolean customerRole=roles instanceof List<?> list && list.contains("customer");
            if(!admin && !customerRole) throw new ForbiddenResponse("Din konto har ingen applikationsrolle");
            String linked=claims.getStringClaim("customerGuid");
            // New self-registered identities cannot choose their customer UUID.
            UUID customer=linked==null ? UUID.nameUUIDFromBytes((claims.getIssuer()+":"+claims.getSubject()).getBytes(StandardCharsets.UTF_8)) : Api.guid(linked);
            return new Principal(claims.getSubject(),customer,admin);
        } catch (java.text.ParseException ex) { throw new UnauthorizedResponse("Ugyldig identitet"); }
    }
    public static Principal principal(Context ctx) {
        Principal p=ctx.attribute("principal");
        if(p==null) throw new UnauthorizedResponse("Log ind for at fortsætte");
        return p;
    }
    public static boolean admin(Context ctx) { return principal(ctx).admin(); }
    public static void owner(Context ctx, UUID customer) {
        Principal p=principal(ctx);
        if(!p.admin() && !p.customer().equals(customer)) throw new ForbiddenResponse("Du har kun adgang til dine egne oplysninger");
    }
    public static void requireAdmin(Context ctx) {
        if(!admin(ctx)) throw new ForbiddenResponse("Handlingen kræver administratoradgang");
    }
    public static void install(Javalin app,String service) {
        if(!Set.of("users","products","orders","payments","product-view").contains(service)) return;
        String issuer=Api.env("OIDC_ISSUER","http://localhost:8180/realms/aj");
        var verifier=new AccessTokenVerifier(issuer,Api.env("OIDC_JWKS_URL",issuer+"/protocol/openid-connect/certs"),"aj-api");
        app.before(ctx -> {
            String path=ctx.path();
            if(!path.startsWith("/api/v1/") || service.equals("apigateway") || service.equals("frontend")) return;
            boolean read=ctx.method()==HandlerType.GET;
            if((service.equals("products") || service.equals("product-view")) && read) return;
            if(path.equals("/api/v1/graphql") && service.equals("product-view")) return;
            JWTClaimsSet claims=verifier.verify(ctx.header("Authorization"));
            ctx.attribute("principal",from(claims)); ctx.attribute("claims",claims);
            ctx.header("Cache-Control","no-store");
            if(service.equals("products") || service.equals("payments") && !read) requireAdmin(ctx);
            if(service.equals("users") && ctx.method()==HandlerType.DELETE) requireAdmin(ctx);
            if(service.equals("users") && ctx.method()==HandlerType.POST && !path.equals("/api/v1/users/me")) requireAdmin(ctx);
        });
    }
}
