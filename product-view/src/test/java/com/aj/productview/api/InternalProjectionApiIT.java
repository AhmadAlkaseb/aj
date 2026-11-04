package com.aj.productview.api;

import com.aj.common.Api;
import com.aj.common.Json;
import io.javalin.Javalin;
import org.junit.jupiter.api.*;
import java.net.URI;
import java.net.http.*;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

/** Real HTTP boundary checks, with storage substituted to isolate authentication and delivery. */
class InternalProjectionApiIT {
    private Javalin app;
    private final AtomicInteger writes=new AtomicInteger();
    private final HttpClient http=HttpClient.newHttpClient();
    private final String product=UUID.randomUUID().toString();
    private boolean failWrite;

    @BeforeEach
    void start() {
        app=Api.app("product-view-test");
        InternalProjectionApi.register(app,"test-projection-key",(id,topic,data) -> {
            if(failWrite) throw new IllegalStateException("Storage unavailable");
            writes.incrementAndGet();
        },() -> true);
        app.start("127.0.0.1",0);
    }
    @AfterEach void stop() { app.stop(); }

    private Map<String,Object> event(long version) {
        return Map.of("eventId",UUID.randomUUID().toString(),"topic","product.updated",
            "snapshot",Map.of("guid",product,"version",version,"brand","Volvo","model","EX30","price",279900,"currency","DKK","status","AVAILABLE"));
    }
    private HttpResponse<String> put(Object event,String key) throws Exception {
        var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+app.port()+"/internal/projections/products/"+product))
            .header("Content-Type","application/json").PUT(HttpRequest.BodyPublishers.ofString(Json.write(event)));
        if(key!=null) request.header("X-Projection-Token",key);
        return http.send(request.build(),HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void missingOrWrongCredentialsNeverReachStorage() throws Exception {
        assertEquals(401,put(event(1),null).statusCode());
        assertEquals(401,put(event(1),"wrong-key").statusCode());
        assertEquals(0,writes.get());
    }
    @Test
    void acceptsValidSnapshotsAndRejectsInvalidVersions() throws Exception {
        assertEquals(204,put(event(1),"test-projection-key").statusCode());
        assertEquals(400,put(event(-1),"test-projection-key").statusCode());
        assertEquals(1,writes.get());
    }
    @Test
    void failedStorageIsNotAcknowledgedAsSuccess() throws Exception {
        failWrite=true;
        assertEquals(503,put(event(1),"test-projection-key").statusCode());
        assertEquals(0,writes.get());
    }
}
