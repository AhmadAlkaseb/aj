package app;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PaymentRulesTest {
    @Test
    void enforcesBusinessBoundaries() {
        // Given
        String pendingStatus = "PENDING";
        String completedStatus = "COMPLETED";
        String requestedStatus = "REQUESTED";
        String failedStatus = "FAILED";

        // When
        assertDoesNotThrow(() -> PaymentRules.allow(pendingStatus, completedStatus));
        assertDoesNotThrow(() -> PaymentRules.allow(completedStatus, completedStatus));

        // Then
        assertThrows(RuntimeException.class, () -> PaymentRules.allow(requestedStatus, completedStatus));
        assertThrows(RuntimeException.class, () -> PaymentRules.allow(failedStatus, completedStatus));
    }
}
