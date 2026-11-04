package com.aj.common;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public final class Json {
    public static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private Json() {}
    public static String write(Object value) {
        try { return MAPPER.writeValueAsString(value); }
        catch (Exception e) { throw new IllegalArgumentException("Kan ikke skrive JSON", e); }
    }
    public static JsonNode read(String value) {
        try { return MAPPER.readTree(value); }
        catch (Exception e) { throw new io.javalin.http.BadRequestResponse("Ugyldig JSON"); }
    }
}
