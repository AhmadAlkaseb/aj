package com.aj.gateway;
import com.aj.common.*;
import io.javalin.http.Context;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

public class Application {
    private static final List<String> SERVICES=List.of("users","products","orders","payments","product-view","product-synchronizer","keycloak");
    private static String url(String service) { return Api.env(service.toUpperCase().replace('-','_')+"_URL",service.equals("keycloak") ? "http://localhost:8180" : "http://"+service+":7000"); }
    public static String target(Context ctx) {
        String path=ctx.path();
        if(path.equals("/api/v1/graphql")) return url("product-view");
        if(path.startsWith("/api/v1/products")) return url(ctx.method().name().equals("GET") ? "product-view" : "products");
        if(path.matches("/api/v1/users/[^/]+/orders") || path.startsWith("/api/v1/orders") && !path.endsWith("/payments")) return url("orders");
        if(path.startsWith("/api/v1/payments") || path.matches("/api/v1/orders/[^/]+/payments")) return url("payments");
        return url("users");
    }
    public static void main(String[] args) {
        var app=Api.app("apigateway"); Proxy.routes(app,Application::target);
        com.aj.gateway.auth.OidcRoutes.register(app);
        Api.health(app,"apigateway",() -> true);
        app.get("/api/v1/status",ctx -> {
            List<CompletableFuture<Map<String,Object>>> checks=new ArrayList<>();
            for(String s:SERVICES) {
                String healthPath=s.equals("keycloak") ? "/realms/aj/.well-known/openid-configuration" : "/health";
                var request=HttpRequest.newBuilder(URI.create(url(s)+healthPath)).timeout(Duration.ofSeconds(3)).GET().build();
                checks.add(Api.HTTP.sendAsync(request,HttpResponse.BodyHandlers.ofString()).handle((r,e) -> Map.<String,Object>of("service",s,"status",e==null && r.statusCode()==200 ? "UP" : "DOWN")));
            }
            var states=checks.stream().map(CompletableFuture::join).toList();
            boolean up=states.stream().allMatch(s -> s.get("status").equals("UP"));
            ctx.json(Map.of("status",up ? "UP" : "DEGRADED","services",states,"message",up ? "Alle tjenester er klar" : "En eller flere tjenester er midlertidigt utilgængelige"));
        });
        Runtime.getRuntime().addShutdownHook(new Thread(app::stop)); Api.start(app,8080);
    }
}
