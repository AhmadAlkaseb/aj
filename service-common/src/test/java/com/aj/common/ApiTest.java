package com.aj.common;

import io.javalin.http.BadRequestResponse;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ApiTest {
    @Test
    void acceptsCanonicalGuidButRejectsAbbreviatedAndInvalidReferences() {
        // Given
        UUID guid = UUID.randomUUID();

        // When
        UUID parsedGuid = Api.guid(guid.toString());

        // Then
        assertEquals(guid, parsedGuid);
        assertThrows(BadRequestResponse.class, () -> Api.guid("1-1-1-1-1"));
        assertThrows(BadRequestResponse.class, () -> Api.guid("not-a-guid"));
    }

    @Test
    void validatesMoneyWithoutRoundingOrAcceptingNegativeOrTextAmounts() {
        // Given
        var validPrice = Json.read("{\"price\":219995.50}");
        String[] invalidPrices = {"-1", "1.001", "\"100\"", "null", "10000000000"};

        // When
        BigDecimal parsedPrice = Api.money(validPrice, "price");

        // Then
        assertEquals(new BigDecimal("219995.50"), parsedPrice);
        for (String invalid : invalidPrices) {
            assertThrows(BadRequestResponse.class,
                () -> Api.money(Json.read("{\"price\":" + invalid + "}"), "price"), invalid);
        }
    }

    @Test
    void requiresNonBlankTextAndRestrictsManualStatusChoices() {
        // Given
        var oversizedName = Json.read(Json.write(java.util.Map.of("name","x".repeat(256))));
        var paddedName = Json.read("{\"name\":\" Anne \"}");
        var blankName = Json.read("{\"name\":\" \"}");
        var disallowedStatus = Json.read("{\"status\":\"SOLD\"}");

        // When
        String normalizedName = Api.required(paddedName, "name");

        // Then
        assertEquals("Anne", normalizedName);
        assertThrows(BadRequestResponse.class, () -> Api.required(oversizedName,"name"));
        assertThrows(BadRequestResponse.class, () -> Api.required(blankName, "name"));
        assertThrows(BadRequestResponse.class,
            () -> Api.choice(disallowedStatus, "status", "AVAILABLE", "AVAILABLE", "UNAVAILABLE"));
    }
}
