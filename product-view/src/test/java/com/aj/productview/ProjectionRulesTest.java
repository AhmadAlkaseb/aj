package com.aj.productview;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProjectionRulesTest {
    @Test
    void enforcesBusinessBoundaries() {
        // Given
        long storedVersion = 10;

        // When
        boolean olderVersionIsObsolete = ProjectionRules.obsolete(storedVersion, 9);
        boolean sameVersionIsObsolete = ProjectionRules.obsolete(storedVersion, 10);
        boolean newerVersionIsObsolete = ProjectionRules.obsolete(storedVersion, 11);

        // Then
        assertTrue(olderVersionIsObsolete);
        assertTrue(sameVersionIsObsolete);
        assertFalse(newerVersionIsObsolete);
        assertThrows(IllegalArgumentException.class, () -> ProjectionRules.obsolete(storedVersion, -1));
        assertThrows(IllegalArgumentException.class, () -> ProjectionRules.page(0, 101));
    }
}
