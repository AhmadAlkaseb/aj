package com.aj.frontend;
import com.aj.common.*;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
public class Application {
    public static void main(String[] args) {
        var app=Api.app("frontend",c -> c.staticFiles.add(s -> { s.directory="/public"; s.location=Location.CLASSPATH; s.hostedPath="/"; }));
        String gateway=Api.env("GATEWAY_URL","http://localhost:8080");
        Proxy.routes(app,ctx -> gateway);
        app.get("/api/v1/status",ctx -> Proxy.forward(ctx,gateway));
        app.get("/api/v1/auth/session",ctx -> Proxy.forward(ctx,gateway));
        app.get("/auth/config",ctx -> ctx.header("Cache-Control","no-store").json(java.util.Map.of(
            "url",Api.env("OIDC_PUBLIC_URL","http://localhost:8180"),"realm","aj","clientId","aj-frontend")));
        Api.health(app,"frontend",() -> true);
        Runtime.getRuntime().addShutdownHook(new Thread(app::stop)); Api.start(app,3000);
    }
}
