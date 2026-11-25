package com.aj.frontend;
import com.aj.common.*;
import io.javalin.http.staticfiles.Location;
public class Application {
    public static void main(String[] args) {
        String gateway=Api.env("GATEWAY_URL","http://localhost:8080");
        var app=Api.app("frontend",c -> {
            c.staticFiles.add(s -> { s.directory="/public"; s.location=Location.CLASSPATH; s.hostedPath="/"; });
            Proxy.routes(c.routes,ctx -> gateway);
            c.routes.get("/api/v1/status",ctx -> Proxy.forward(ctx,gateway));
            c.routes.get("/api/v1/auth/session",ctx -> Proxy.forward(ctx,gateway));
            c.routes.get("/auth/config",ctx -> ctx.header("Cache-Control","no-store").json(java.util.Map.of(
                "url",Api.env("OIDC_PUBLIC_URL","http://localhost:8180"),"realm","aj","clientId","aj-frontend")));
            Api.health(c.routes,"frontend",() -> true);
        });
        Runtime.getRuntime().addShutdownHook(new Thread(app::stop)); Api.start(app,3000);
    }
}
