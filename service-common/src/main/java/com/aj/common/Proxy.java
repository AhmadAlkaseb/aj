package com.aj.common;
import io.javalin.Javalin;
import io.javalin.http.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

public final class Proxy {
    private Proxy() {}
    public static void routes(Javalin app,java.util.function.Function<Context,String> target) {
        for(HandlerType method:List.of(HandlerType.GET,HandlerType.POST,HandlerType.PUT,HandlerType.DELETE)) {
            for(String path:List.of("/api/v1/graphql","/api/v1/users","/api/v1/users/{userGuid}","/api/v1/users/{userGuid}/orders","/api/v1/products","/api/v1/products/description-draft","/api/v1/products/{productGuid}","/api/v1/orders","/api/v1/orders/{orderGuid}","/api/v1/orders/{orderGuid}/payments","/api/v1/orders/{orderGuid}/history","/api/v1/orders/{orderGuid}/confirmation","/api/v1/payments","/api/v1/payments/{paymentGuid}")) {
                app.addHttpHandler(method,path,ctx -> forward(ctx,target.apply(ctx)));
            }
        }
    }
    public static void forward(Context ctx,String base) {
        try {
            String query=ctx.queryString(); String path=ctx.path()+(query==null ? "" : "?"+query);
            var req=HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofSeconds(ctx.path().endsWith("/description-draft") ? 55 : 6)).header("Content-Type","application/json")
                .method(ctx.method().name(),HttpRequest.BodyPublishers.ofByteArray(ctx.bodyAsBytes()));
            String authorization = ctx.header("Authorization");
            if (authorization != null) req.header("Authorization", authorization);
            String correlation=ctx.attribute("correlationId"); if(correlation!=null)req.header("X-Correlation-ID",correlation);
            String key=ctx.header("Idempotency-Key"); if(key!=null) req.header("Idempotency-Key",key);
            var res=Api.HTTP.send(req.build(),HttpResponse.BodyHandlers.ofByteArray()); ctx.status(res.statusCode());
            res.headers().firstValue("X-Correlation-ID").ifPresent(v -> Telemetry.correlation(ctx,v));
            ctx.contentType(res.headers().firstValue("Content-Type").orElse("application/json"));
            res.headers().firstValue("Location").ifPresent(v -> ctx.header("Location",v)); ctx.result(res.body());
        } catch(InterruptedException interrupted) {
            Thread.currentThread().interrupt(); throw unavailable();
        } catch(java.io.IOException | IllegalArgumentException failure) { throw unavailable(); }
    }
    private static ServiceUnavailableResponse unavailable() {
        return new ServiceUnavailableResponse("Forbindelsen til tjenesten er afbrudt. Dine oplysninger er bevaret i formularen. Prøv igen om lidt.");
    }
}
