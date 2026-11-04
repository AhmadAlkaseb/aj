package com.aj.common;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
public final class HealthProbe {
    public static void main(String[] args) {
        try {
            var response=HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://localhost:"+Api.env("PORT","7000")+"/health"))
                .timeout(Duration.ofSeconds(5)).GET().build(),HttpResponse.BodyHandlers.discarding());
            System.exit(response.statusCode()==200 ? 0 : 1);
        } catch(Exception e) { System.exit(1); }
    }
}
