package com.aj.common;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CommandsTest {
    @Test void fingerprintsObjectFieldsIndependentlyOfJsonKeyOrder() {
        // Given
        var first = Json.read("{\"a\":1,\"nested\":{\"x\":1,\"y\":2}}");
        var sameFieldsInDifferentOrder = Json.read("{\"nested\":{\"y\":2,\"x\":1},\"a\":1}");

        // When
        String firstFingerprint = Commands.fingerprint(first);
        String reorderedFingerprint = Commands.fingerprint(sameFieldsInDifferentOrder);

        // Then
        assertEquals(firstFingerprint, reorderedFingerprint);
    }

    @Test void distinguishesChangedCommandContentsAndArrayOrder() {
        // Given
        var firstCommand = Json.read("{\"a\":[1,2]}");
        var reorderedCommand = Json.read("{\"a\":[2,1]}");

        // When
        String firstFingerprint = Commands.fingerprint(firstCommand);
        String reorderedFingerprint = Commands.fingerprint(reorderedCommand);

        // Then
        assertNotEquals(firstFingerprint, reorderedFingerprint);
    }
}
