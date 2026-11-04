package com.aj.synchronizer;

import com.aj.common.Api;
import com.aj.common.Json;
import io.javalin.Javalin;
import io.javalin.http.ServiceUnavailableResponse;
import org.junit.jupiter.api.*;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class ProjectionClientIT {
    private Javalin owner;
    private ProjectionClient client;
    private int status=204;
    private final AtomicReference<String> body=new AtomicReference<>();

    @BeforeEach
    void start() {
        owner=Api.app("projection-owner-test");
        owner.put("/internal/projections/products/{guid}",ctx -> {
            assertEquals("test-key",ctx.header("X-Projection-Token"));
            body.set(ctx.body()); ctx.status(status);
        });
        owner.get("/internal/projections/health",ctx -> ctx.status(200));
        owner.start("127.0.0.1",0);
        client=new ProjectionClient("http://127.0.0.1:"+owner.port(),"test-key");
    }
    @AfterEach void stop() { owner.stop(); }
    private void deliver() {
        client.project(UUID.randomUUID().toString(),"product.created",Json.read("{\"guid\":\"35ced323-4f28-49c0-b81d-669a0f1973c2\",\"version\":1}"));
    }

    @Test
    void relaysEventIdentityTopicAndSnapshotToOwner() {
        deliver();
        var event=Json.read(body.get());
        assertEquals("product.created",event.path("topic").asText());
        assertEquals(1,event.path("snapshot").path("version").asLong());
        assertFalse(event.path("eventId").asText().isBlank());
        assertTrue(client.healthy());
    }
    @Test
    void availabilityAndCredentialFailuresAreRetriedRatherThanDropped() {
        for(int code:new int[]{401,403,404,500,503}) {
            status=code;
            assertThrows(ServiceUnavailableResponse.class,this::deliver,"HTTP "+code);
        }
    }
    @Test
    void malformedEventsAreDistinguishedFromTransientFailure() {
        status=400;
        assertThrows(IllegalArgumentException.class,this::deliver);
    }
}
