package com.aj.common;

import com.fasterxml.jackson.databind.JsonNode;
import io.javalin.Javalin;
import io.javalin.http.*;
import io.javalin.json.JavalinJackson;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;

public final class Api {
    public static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private Api() {}
    public static String env(String key, String fallback) { return System.getenv().getOrDefault(key, fallback); }
    public static Javalin app(String service) {
        return app(service, c -> {});
    }
    public static Javalin app(String service, java.util.function.Consumer<io.javalin.config.JavalinConfig> config) {
        Javalin app = Javalin.create(c -> { c.jsonMapper(new JavalinJackson(Json.MAPPER, false)); c.http.maxRequestSize = 1_000_000; config.accept(c); });
        app.exception(Exception.class, (e, ctx) -> error(service,e,ctx));
        app.exception(HttpResponseException.class, (e, ctx) -> error(service,e,ctx));
        app.error(404,ctx -> ctx.json(Map.of("status",404,"service",service,"message","Oplysningerne eller endpointet blev ikke fundet","timestamp",Instant.now().toString())));
        Telemetry.install(app,service);
        Access.install(app,service);
        return app;
    }
    private static void error(String service, Exception e, Context ctx) {
            int status = e instanceof HttpResponseException h ? h.getStatus() : sqlConflict(e) ? 409 : 503;
            String message = e instanceof HttpResponseException ? e.getMessage() : status == 409 ? "Oplysningerne er allerede registreret eller ændret. Opdatér og prøv igen." : "Tjenesten er midlertidigt utilgængelig. Prøv igen om lidt.";
            if (status >= 500) org.slf4j.LoggerFactory.getLogger(service).error("Request failed: {}", e.getClass().getSimpleName());
            ctx.status(status).json(Map.of("status", status, "service", service, "message", message, "timestamp", Instant.now().toString()));
    
    }
    private static boolean sqlConflict(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof org.hibernate.exception.ConstraintViolationException || t instanceof jakarta.persistence.OptimisticLockException) return true;
        }
        return false;
    }
    public static void health(Javalin app, String name, Supplier<Boolean> check) {
        app.get("/health/live", ctx -> ctx.json(Map.of("service", name, "status", "UP")));
        app.get("/health", ctx -> { boolean up; try { up = check.get(); } catch (Exception e) { up = false; }
            ctx.status(up ? 200 : 503).json(Map.of("service", name, "status", up ? "UP" : "DOWN")); });
    }
    public static void start(Javalin app, int port) { app.start("0.0.0.0", Integer.parseInt(env("PORT", "" + port))); }
    public static UUID guid(String value) {
        try { UUID id = UUID.fromString(value); if (!id.toString().equalsIgnoreCase(value)) throw new IllegalArgumentException(); return id; }
        catch (Exception e) { throw new BadRequestResponse("Ugyldigt GUID"); }
    }
    public static UUID guid(JsonNode node, String field) { return guid(required(node, field)); }
    public static String required(JsonNode node, String field) {
        if (!node.hasNonNull(field) || !node.get(field).isTextual() || node.get(field).asText().isBlank() || node.get(field).asText().length() > (field.equals("query") ? 10000 : field.equals("description") ? 1200 : 255)) throw new BadRequestResponse("Feltet '" + field + "' mangler eller er ugyldigt");
        return node.get(field).asText().trim();
    }
    public static String text(JsonNode node, String field) {
        if(!node.hasNonNull(field))return "";
        if(!node.get(field).isTextual())throw new BadRequestResponse("Ugyldigt tekstfelt: "+field);
        String value=node.get(field).asText().trim();
        if(value.length()>(field.equals("description") ? 10000 : 255))throw new BadRequestResponse("Tekstfeltet er for langt: "+field);
        return value;
    }
    public static String choice(JsonNode n, String f, String fallback, String... allowed) {
        String value = n.has(f) ? required(n, f) : fallback;
        if (!Arrays.asList(allowed).contains(value)) throw new BadRequestResponse("Ugyldig " + f);
        return value;
    }
    public static BigDecimal money(JsonNode node, String field) {
        try { if (!node.path(field).isNumber()) throw new IllegalArgumentException();
            BigDecimal v = node.get(field).decimalValue().setScale(2, java.math.RoundingMode.UNNECESSARY);
            if (v.signum() < 0 || v.precision() > 12) throw new IllegalArgumentException(); return v;
        } catch (Exception e) { throw new BadRequestResponse("Ugyldigt beløb: " + field); }
    }
    public static String currency(JsonNode node) {
        String value = node.has("currency") ? required(node, "currency") : "DKK";
        try { Currency.getInstance(value); return value; } catch (Exception e) { throw new BadRequestResponse("Ugyldig valuta"); }
    }
    public static JsonNode remote(String service, String path) { return remote(service, path, "GET", null); }
    public static JsonNode remote(String service, String path, String method, Object body) {
        String base = env(service.toUpperCase().replace('-', '_') + "_URL", "http://" + service + ":7000");
        try {
            var req = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(4)).header("Content-Type", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(Json.write(body))).build();
            var res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() == 404) throw new NotFoundResponse(service + ": oplysningerne blev ikke fundet");
            if (res.statusCode() == 409) throw new ConflictResponse(service + ": handlingen kan ikke gennemføres i den aktuelle status");
            if (res.statusCode() >= 400) throw new ServiceUnavailableResponse(service + " svarer ikke. Prøv igen om lidt.");
            return Json.read(res.body());
        } catch (HttpResponseException e) { throw e; }
        catch (Exception e) { throw new ServiceUnavailableResponse(service + " er midlertidigt utilgængelig. Prøv igen om lidt."); }
    }
}
