package com.aj.common;

import io.javalin.Javalin;
import org.junit.jupiter.api.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/** Real HTTP servers exercise Javalin, exception mapping, JSON and the shared proxy. */
class HttpBoundaryIT {
    private Javalin upstream;
    private Javalin gateway;
    private final HttpClient client = HttpClient.newHttpClient();

    @BeforeEach
    void start() {
        upstream = Api.app("upstream");
        upstream.get("/api/v1/users/{guid}", ctx -> {
            Api.guid(ctx.pathParam("guid"));
            ctx.json(Map.of("guid", ctx.pathParam("guid")));
        });
        upstream.start("127.0.0.1", 0);
        gateway = Api.app("gateway");
        Proxy.routes(gateway, ctx -> "http://127.0.0.1:" + upstream.port());
        gateway.start("127.0.0.1", 0);
    }

    @AfterEach
    void stop() {
        if (gateway != null) gateway.stop();
        if (upstream != null) upstream.stop();
    }

    private HttpResponse<String> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + gateway.port() + path))
            .timeout(Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void forwardsValidationErrorsAsStructuredJson() throws Exception {
        var response = get("/api/v1/users/invalid");
        assertEquals(400, response.statusCode());
        assertTrue(response.headers().firstValue("Content-Type").orElse("").contains("application/json"));
        var body = Json.read(response.body());
        assertEquals("upstream", body.path("service").asText());
        assertEquals(400, body.path("status").asInt());
        assertFalse(body.path("message").asText().isBlank());
    }

    @Test
    void reportsUnavailableUpstreamWithoutStoppingTheGateway() throws Exception {
        upstream.stop();
        var response = get("/api/v1/users/8aa7d611-5236-45d2-937a-dca08d381cba");
        assertEquals(503, response.statusCode());
        assertEquals("gateway", Json.read(response.body()).path("service").asText());
        assertTrue(gateway.port() > 0);
    }
}
