package com.aj.common;

import io.javalin.http.BadRequestResponse;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ApiTest {
    @Test
    void acceptsCanonicalGuidButRejectsAbbreviatedAndInvalidReferences() {
        UUID guid = UUID.randomUUID();
        assertEquals(guid, Api.guid(guid.toString()));
        assertThrows(BadRequestResponse.class, () -> Api.guid("1-1-1-1-1"));
        assertThrows(BadRequestResponse.class, () -> Api.guid("not-a-guid"));
    }

    @Test
    void validatesMoneyWithoutRoundingOrAcceptingNegativeOrTextAmounts() {
        assertEquals(new BigDecimal("219995.50"), Api.money(Json.read("{\"price\":219995.50}"), "price"));
        for (String invalid : new String[]{"-1", "1.001", "\"100\"", "null", "10000000000"}) {
            assertThrows(BadRequestResponse.class,
                () -> Api.money(Json.read("{\"price\":" + invalid + "}"), "price"), invalid);
        }
    }

    @Test
    void requiresNonBlankTextAndRestrictsManualStatusChoices() {
        assertThrows(BadRequestResponse.class, () -> Api.required(Json.read(Json.write(java.util.Map.of("name","x".repeat(256)))),"name"));
        assertEquals("Anne", Api.required(Json.read("{\"name\":\" Anne \"}"), "name"));
        assertThrows(BadRequestResponse.class, () -> Api.required(Json.read("{\"name\":\" \"}"), "name"));
        assertThrows(BadRequestResponse.class, () -> Api.choice(Json.read("{\"status\":\"SOLD\"}"), "status", "AVAILABLE", "AVAILABLE", "UNAVAILABLE"));
    }
}
