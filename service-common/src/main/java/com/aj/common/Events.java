package com.aj.common;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Broker contract metadata; product snapshot versions remain inside payload. */
public final class Events {
    private static final ThreadLocal<String> CORRELATION = new ThreadLocal<>();
    private Events() {}
    public record Decoded(JsonNode payload, String correlationId, Instant occurredAt) {}
    public static String correlationOr(String fallback){return CORRELATION.get()==null ? fallback : CORRELATION.get();}
    public static String encode(UUID id, String type, Instant time, Object value) {
        JsonNode payload = Json.read(Json.write(value));
        String correlation = CORRELATION.get();
        if (correlation == null) correlation = payload.path("orderGuid").asText(payload.path("guid").asText(id.toString()));
        return Json.write(Map.of("eventId", id, "eventType", type, "occurredAt", time,
            "correlationId", correlation, "schemaVersion", 1, "payload", payload));
    }
    public static Decoded decode(String id, String topic, JsonNode body) {
        UUID.fromString(id);
        if (!body.isObject()) throw new IllegalArgumentException("Event must be an object");
        // Already persisted outbox rows and queued messages from the previous release.
        if (!body.has("schemaVersion") && !body.has("payload"))
            return new Decoded(body, body.path("orderGuid").asText(body.path("guid").asText(id)),Instant.now());
        if (!body.path("schemaVersion").isIntegralNumber() || !body.path("schemaVersion").canConvertToInt() || body.path("schemaVersion").asInt() != 1
            || !id.equals(body.path("eventId").asText()) || !topic.equals(body.path("eventType").asText()))
            throw new IllegalArgumentException("Unsupported schema or inconsistent event identity/type");
        try { Instant.parse(body.path("occurredAt").asText()); }
        catch (java.time.format.DateTimeParseException ex) { throw new IllegalArgumentException("Invalid event timestamp", ex); }
        String correlation = body.path("correlationId").asText();
        UUID.fromString(correlation);
        if (!body.path("payload").isObject()) throw new IllegalArgumentException("Missing event payload");
        return new Decoded(body.get("payload"), correlation,Instant.parse(body.path("occurredAt").asText()));
    }
    public static void process(Decoded event, Runnable handler) {
        CORRELATION.set(event.correlationId());
        try { handler.run(); } finally { CORRELATION.remove(); }
    }
}
