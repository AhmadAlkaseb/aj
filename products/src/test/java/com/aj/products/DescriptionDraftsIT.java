package com.aj.products;

import com.aj.common.Json;
import com.sun.net.httpserver.HttpServer;
import io.javalin.http.*;
import org.junit.jupiter.api.Test;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;

class DescriptionDraftsIT {
    private final String input="{\"brand\":\"Volvo\",\"model\":\"EX30\",\"year\":2024,\"email\":\"private@example.test\",\"price\":1}";
    @Test void acceptsDraftAndSendsOnlyWhitelistedFacts() throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",exchange -> {
            String body=new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);
            assertFalse(body.contains("private@example")); assertFalse(body.contains("\"price\":1"));
            byte[] response=Json.write(java.util.Map.of("response",Json.write(java.util.Map.of("description","En Volvo EX30 fra 2024 til din næste tur.")))).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200,response.length);exchange.getResponseBody().write(response);exchange.close();
        });server.start();
        try {
            var adapter=new DescriptionDrafts(URI.create("http://127.0.0.1:"+server.getAddress().getPort()),"test",Duration.ofSeconds(2));
            assertEquals(true,adapter.generate(Json.read(input)).get("requiresApproval"));
        } finally { server.stop(0); }
    }
    @Test void rejectsInvalidInputBeforeCallingModel() {
        var adapter=new DescriptionDrafts(URI.create("http://127.0.0.1:1"),"test",Duration.ofMillis(20));
        assertThrows(BadRequestResponse.class,()->adapter.generate(Json.read("{\"brand\":\"<script>\",\"model\":\"x\",\"year\":2024}")));
    }
    @Test void turnsUnavailableModelIntoFriendlyServiceError() {
        var adapter=new DescriptionDrafts(URI.create("http://127.0.0.1:1"),"test",Duration.ofMillis(20));
        assertThrows(ServiceUnavailableResponse.class,()->adapter.generate(Json.read(input)));
    }
}
