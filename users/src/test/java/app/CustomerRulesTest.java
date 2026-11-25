package app;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CustomerRulesTest {
    @Test
    void enforcesBusinessBoundaries() {
        // Given
        String mixedCaseEmail = " A@Example.Test ";
        String malformedEmail = "a@bad";
        String spacedEmail = "a b@example.test";

        // When
        String normalizedEmail = CustomerRules.email(mixedCaseEmail);

        // Then
        assertEquals("a@example.test", normalizedEmail);
        assertThrows(RuntimeException.class, () -> CustomerRules.email(malformedEmail));
        assertThrows(RuntimeException.class, () -> CustomerRules.email(spacedEmail));
    }
}
