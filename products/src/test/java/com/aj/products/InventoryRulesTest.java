package com.aj.products;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InventoryRulesTest {
    @Test
    void enforcesBusinessBoundaries() {
        // Given
        var buyer = java.util.UUID.randomUUID();
        var anotherBuyer = java.util.UUID.randomUUID();

        // When
        boolean availableCarCanBeReserved = InventoryRules.canReserve("AVAILABLE", null, buyer);
        boolean currentOwnerCanKeepReservation = InventoryRules.canReserve("RESERVED", buyer, buyer);
        boolean anotherBuyerCanTakeReservation = InventoryRules.canReserve("RESERVED", buyer, anotherBuyer);
        boolean soldCarIsEditable = InventoryRules.editable("SOLD");
        String cancelThenQuantityCheck = InventoryRules.signal(
                InventoryRules.signal(null, "order.cancelled"), "quantitycheck");
        String quantityCheckThenCancel = InventoryRules.signal(
                InventoryRules.signal(null, "quantitycheck"), "order.cancelled");
        String paidThenCancel = InventoryRules.signal(
                InventoryRules.signal(null, "payment.completed"), "order.cancelled");

        // Then
        assertTrue(availableCarCanBeReserved);
        assertTrue(currentOwnerCanKeepReservation);
        assertFalse(anotherBuyerCanTakeReservation);
        assertFalse(soldCarIsEditable);
        assertEquals("RELEASED", cancelThenQuantityCheck);
        assertEquals("RELEASED", quantityCheckThenCancel);
        assertEquals("PAID", paidThenCancel);
    }
}
