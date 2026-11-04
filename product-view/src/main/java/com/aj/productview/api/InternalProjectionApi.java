package com.aj.productview.api;

import com.aj.common.Api;
import com.aj.common.Json;
import com.fasterxml.jackson.databind.JsonNode;
import io.javalin.Javalin;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.UnauthorizedResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;

/** This interface owns all writes to the catalogue, including retries from the relay. */
public final class InternalProjectionApi {
    public static final String TOKEN_HEADER="X-Projection-Token";
    @FunctionalInterface public interface Writer {
        void project(String eventId,String topic,JsonNode snapshot);
    }
    private InternalProjectionApi() {}

    public static void register(Javalin app,String token,Writer writer,BooleanSupplier healthy) {
        if(token==null || token.isBlank()) throw new IllegalArgumentException("Projection token must be configured");
        app.get("/internal/projections/health",ctx -> {
            authorize(ctx,token);
            boolean up=healthy.getAsBoolean();
            ctx.status(up ? 200 : 503).json(Map.of("status",up ? "UP" : "DOWN"));
        });
        app.put("/internal/projections/products/{productGuid}",ctx -> {
            authorize(ctx,token);
            JsonNode event=Json.read(ctx.body());
            String eventId=Api.guid(event,"eventId").toString();
            String topic=Api.required(event,"topic");
            if(!Set.of("product.created","product.updated","product.reserved","product.removed").contains(topic))
                throw new BadRequestResponse("Unknown product projection event");
            JsonNode data=event.path("snapshot");
            if(!data.isObject() || !Api.guid(data,"guid").equals(Api.guid(ctx.pathParam("productGuid"))))
                throw new BadRequestResponse("Product GUID does not match snapshot");
            JsonNode version=data.path("version");
            if(!version.isIntegralNumber() || !version.canConvertToLong() || version.asLong()<0)
                throw new BadRequestResponse("A non-negative snapshot version is required");
            Api.required(data,"brand"); Api.required(data,"model"); Api.money(data,"price"); Api.currency(data);
            Api.choice(data,"status",null,"AVAILABLE","UNAVAILABLE","RESERVED","SOLD");
            writer.project(eventId,topic,data);
            ctx.header("Cache-Control","no-store").status(204);
        });
    }

    private static void authorize(Context ctx,String token) {
        String provided=ctx.header(TOKEN_HEADER);
        if(provided==null || !MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8),provided.getBytes(StandardCharsets.UTF_8)))
            throw new UnauthorizedResponse("Projection credentials required");
    }
}
