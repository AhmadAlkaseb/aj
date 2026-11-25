package com.aj.products;

import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryRulesTest {

    private static final UUID CURRENT_OWNER_ID =
            UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_BUYER_ID =
            UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Test
    void shouldAllowReservationWhenInventoryIsAvailable() {
        // Given
        String inventoryStatus = "AVAILABLE";
        UUID requestedBuyerId = CURRENT_OWNER_ID;

        // When
        boolean canReserve = InventoryRules.canReserve(
                inventoryStatus,
                null,
                requestedBuyerId
        );

        // Then
        assertTrue(canReserve, "Available inventory should be reservable");
    }

    @Test
    void shouldAllowCurrentOwnerToKeepExistingReservation() {
        // Given
        String inventoryStatus = "RESERVED";
        UUID currentOwnerId = CURRENT_OWNER_ID;
        UUID requestedBuyerId = CURRENT_OWNER_ID;

        // When
        boolean canReserve = InventoryRules.canReserve(
                inventoryStatus,
                currentOwnerId,
                requestedBuyerId
        );

        // Then
        assertTrue(canReserve, "The current owner should be allowed to keep the reservation");
    }

    @Test
    void shouldNotAllowAnotherBuyerToTakeExistingReservation() {
        // Given
        String inventoryStatus = "RESERVED";
        UUID currentOwnerId = CURRENT_OWNER_ID;
        UUID requestedBuyerId = OTHER_BUYER_ID;

        // When
        boolean canReserve = InventoryRules.canReserve(
                inventoryStatus,
                currentOwnerId,
                requestedBuyerId
        );

        // Then
        assertFalse(canReserve,
                "A different buyer must not reserve inventory that is already reserved");
    }

    @Test
    void shouldNotAllowEditingSoldInventory() {
        // Given
        String inventoryStatus = "SOLD";

        // When
        boolean editable = InventoryRules.editable(inventoryStatus);

        // Then
        assertFalse(editable, "Sold inventory must not be editable");
    }

    @Test
    void shouldReleaseInventoryWhenCancellationAndReservationCheckArriveInEitherOrder() {
        // Given
        String stateAfterCancellation = InventoryRules.signal(null, "order.cancelled");
        String stateAfterReservationCheck = InventoryRules.signal(null, "quantitycheck");

        // When
        String cancellationThenReservationCheck =
                InventoryRules.signal(stateAfterCancellation, "quantitycheck");
        String reservationCheckThenCancellation =
                InventoryRules.signal(stateAfterReservationCheck, "order.cancelled");

        // Then
        assertEquals("RELEASED", cancellationThenReservationCheck);
        assertEquals("RELEASED", reservationCheckThenCancellation);
    }

    @Test
    void shouldKeepPaidInventoryPaidWhenCancellationArrives() {
        // Given
        String stateAfterPayment = InventoryRules.signal(null, "payment.completed");

        // When
        String finalState = InventoryRules.signal(stateAfterPayment, "order.cancelled");

        // Then
        assertEquals("PAID", finalState);
    }
}
