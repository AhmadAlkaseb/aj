package com.aj.common.demo;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class DemoDataTest {
    @Test
    void fixtureReferencesAreStableAndUniqueAcrossAllDomains() {
        // Given
        Set<UUID> ids = new HashSet<>();

        // When
        for (String domain : new String[]{"users", "products", "orders", "payments"}) {
            for (int index = 1; index <= DemoData.COUNT; index++) {
                UUID id = DemoData.guid(domain, index);
                assertEquals(id, DemoData.guid(domain, index));
                assertTrue(ids.add(id), "Demo identities must not collide");
            }
        }

        // Then
        assertEquals(80, ids.size());
    }

    @Test
    void demoPaymentsUseTheNormalRetryIdentityForTheirOrder() {
        // Given
        int fixtureCount = DemoData.COUNT;

        // When / Then
        for (int index = 1; index <= fixtureCount; index++) {
            UUID payment = UUID.nameUUIDFromBytes(("aj-payment:" + DemoData.guid("orders", index)).getBytes(StandardCharsets.UTF_8));
            assertEquals(payment, DemoData.guid("payments", index));
        }
    }

    @Test
    void sampleInventoryHasMatchingOutcomesAndCarsAvailableForLiveDemonstrations() {
        // Given
        long available = 0;

        // When
        assertEquals(20, DemoData.CARS.size());
        for (int index = 1; index <= DemoData.COUNT; index++) {
            assertTrue(DemoData.car(index).amount().signum() > 0);
            switch (DemoData.paymentStatus(index)) {
                case "COMPLETED" -> {
                    assertEquals("COMPLETED", DemoData.orderStatus(index));
                    assertEquals("SOLD", DemoData.productStatus(index));
                }
                case "PENDING" -> {
                    assertEquals("PAYMENT_PENDING", DemoData.orderStatus(index));
                    assertEquals("RESERVED", DemoData.productStatus(index));
                }
                case "FAILED" -> {
                    assertEquals("FAILED", DemoData.orderStatus(index));
                    assertEquals("AVAILABLE", DemoData.productStatus(index));
                    available++;
                }
                default -> fail("Unknown payment state");
            }
        }

        // Then
        assertEquals(10, available);
    }
}
