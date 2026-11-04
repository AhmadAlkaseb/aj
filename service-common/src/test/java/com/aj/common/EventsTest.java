package com.aj.common;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class EventsTest {
    @Test void preservesCorrelationAcrossDifferentBusinessPayloadsAndClearsContext() {
        UUID id=UUID.randomUUID(), order=UUID.randomUUID(), product=UUID.randomUUID();
        var body=Json.read(Events.encode(id,"quantitycheck",Instant.now(),Map.of("orderGuid",order)));
        var decoded=Events.decode(id.toString(),"quantitycheck",body);
        Events.process(decoded, () -> {
            var snapshot=Json.read(Events.encode(UUID.randomUUID(),"product.reserved",Instant.now(),Map.of("guid",product,"version",42)));
            assertEquals(order.toString(),snapshot.path("correlationId").asText());
            assertEquals(1,snapshot.path("schemaVersion").asInt());
            assertEquals(42,snapshot.path("payload").path("version").asInt());
        });
        assertEquals(product.toString(),Json.read(Events.encode(id,"product.updated",Instant.now(),Map.of("guid",product))).path("correlationId").asText());
    }
    @Test void readsLegacyMessagesWithoutChangingTheirBusinessPayload() {
        var body=Json.read("{\"orderGuid\":\""+UUID.randomUUID()+"\"}");
        assertEquals(body,Events.decode(UUID.randomUUID().toString(),"quantitycheck",body).payload());
    }
    @Test void rejectsInvalidMetadataBeforeCallingBusinessCode() {
        UUID id=UUID.randomUUID();
        var body=(com.fasterxml.jackson.databind.node.ObjectNode)Json.read(Events.encode(id,"customer.check",Instant.now(),Map.of()));
        assertThrows(IllegalArgumentException.class,() -> Events.decode(id.toString(),"wrong.topic",body));
        assertThrows(IllegalArgumentException.class,() -> Events.decode(UUID.randomUUID().toString(),"customer.check",body));
        body.put("schemaVersion",2);
        assertThrows(IllegalArgumentException.class,() -> Events.decode(id.toString(),"customer.check",body));
        body.put("schemaVersion",4294967297L);
        assertThrows(IllegalArgumentException.class,() -> Events.decode(id.toString(),"customer.check",body));
        body.put("schemaVersion",1).put("occurredAt","invalid");
        assertThrows(IllegalArgumentException.class,() -> Events.decode(id.toString(),"customer.check",body));
    }
}
