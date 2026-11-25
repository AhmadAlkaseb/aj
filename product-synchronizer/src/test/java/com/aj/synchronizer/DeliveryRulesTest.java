package com.aj.synchronizer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DeliveryRulesTest {
    @Test
    void enforcesBusinessBoundaries() {
        // Given
        int successfulResponse = 204;
        int malformedRequest = 400;
        int unauthorizedResponse = 401;
        int unavailableResponse = 503;

        // When / Then
        assertDoesNotThrow(() -> DeliveryRules.accepted(successfulResponse));
        assertThrows(IllegalArgumentException.class, () -> DeliveryRules.accepted(malformedRequest));
        assertThrows(io.javalin.http.ServiceUnavailableResponse.class,
                () -> DeliveryRules.accepted(unauthorizedResponse));
        assertThrows(io.javalin.http.ServiceUnavailableResponse.class,
                () -> DeliveryRules.accepted(unavailableResponse));
    }
}
