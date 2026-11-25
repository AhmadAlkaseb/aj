package app;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OrderRulesTest {
    @Test
    void enforcesBusinessBoundaries() {
        // Given
        var orderId = java.util.UUID.randomUUID();

        // When
        boolean pendingPaymentCanBeClaimed = OrderRules.claimable("PAYMENT_PENDING", orderId, orderId);
        boolean cancelledOrderCanBeClaimed = OrderRules.claimable("CANCELLED", null, orderId);
        String pendingPaymentExpiry = OrderRules.expired("PAYMENT_PENDING");
        String completedOrderExpiry = OrderRules.expired("COMPLETED");
        String reservedOrderExpiry = OrderRules.expired("RESERVED");
        String failedOrderExpiry = OrderRules.expired("FAILED");

        // Then
        assertTrue(pendingPaymentCanBeClaimed);
        assertFalse(cancelledOrderCanBeClaimed);
        assertEquals("REVIEW_REQUIRED", pendingPaymentExpiry);
        assertEquals("COMPLETED", completedOrderExpiry);
        assertEquals("CANCELLED", reservedOrderExpiry);
        assertEquals("FAILED", failedOrderExpiry);
    }
}
