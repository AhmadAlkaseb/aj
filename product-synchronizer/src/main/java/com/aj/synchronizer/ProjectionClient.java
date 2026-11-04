package com.aj.synchronizer;

import com.aj.common.Api;
import com.aj.common.Json;
import com.fasterxml.jackson.databind.JsonNode;
import io.javalin.http.ServiceUnavailableResponse;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/** Relay events to the database owner's API; this service has no MongoDB dependency. */
public final class ProjectionClient {
    private final String baseUrl;
    private final String token;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

    public ProjectionClient(String baseUrl,String token) {
        this.baseUrl=baseUrl.replaceAll("/+$","");
        if(token==null || token.isBlank()) throw new IllegalArgumentException("Projection token must be configured");
        this.token=token;
    }

    public void project(String eventId,String topic,JsonNode data) {
        String guid=Api.guid(data,"guid").toString();
        var event=Map.of("eventId",eventId,"topic",topic,"snapshot",data);
        HttpRequest request=HttpRequest.newBuilder(URI.create(baseUrl+"/internal/projections/products/"+guid))
            .timeout(Duration.ofSeconds(5)).header("Content-Type","application/json").header("X-Projection-Token",token)
            .PUT(HttpRequest.BodyPublishers.ofString(Json.write(event))).build();
        int status=send(request);
        DeliveryRules.accepted(status);
    }

    public boolean healthy() {
        try {
            return send(HttpRequest.newBuilder(URI.create(baseUrl+"/internal/projections/health"))
                .timeout(Duration.ofSeconds(2)).header("X-Projection-Token",token).GET().build())==200;
        } catch(Exception e) { return false; }
    }

    private int send(HttpRequest request) {
        try { return http.send(request,HttpResponse.BodyHandlers.discarding()).statusCode(); }
        catch(InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServiceUnavailableResponse("Projection request interrupted; retry required");
        } catch(java.io.IOException e) {
            throw new ServiceUnavailableResponse("Projection owner unavailable; retry required");
        }
    }
}
