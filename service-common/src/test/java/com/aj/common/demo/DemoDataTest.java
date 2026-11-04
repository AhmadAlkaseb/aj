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
        Set<UUID> ids = new HashSet<>();
        for (String domain : new String[]{"users", "products", "orders", "payments"}) {
            for (int index = 1; index <= DemoData.COUNT; index++) {
                UUID id = DemoData.guid(domain, index);
                assertEquals(id, DemoData.guid(domain, index));
                assertTrue(ids.add(id), "Demo identities must not collide");
            }
        }
        assertEquals(80, ids.size());
    }

    @Test
    void demoPaymentsUseTheNormalRetryIdentityForTheirOrder() {
        for (int index = 1; index <= DemoData.COUNT; index++) {
            UUID payment = UUID.nameUUIDFromBytes(("aj-payment:" + DemoData.guid("orders", index)).getBytes(StandardCharsets.UTF_8));
            assertEquals(payment, DemoData.guid("payments", index));
        }
    }

    @Test
    void sampleInventoryHasMatchingOutcomesAndCarsAvailableForLiveDemonstrations() {
        assertEquals(20, DemoData.CARS.size());
        long available = 0;
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
        assertEquals(10, available);
    }
}
