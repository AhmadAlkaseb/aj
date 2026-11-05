package com.aj.common;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CommandsTest {
    @Test void fingerprintsObjectFieldsIndependentlyOfJsonKeyOrder() {
        assertEquals(Commands.fingerprint(Json.read("{\"a\":1,\"nested\":{\"x\":1,\"y\":2}}")),Commands.fingerprint(Json.read("{\"nested\":{\"y\":2,\"x\":1},\"a\":1}")));
    }
    @Test void distinguishesChangedCommandContentsAndArrayOrder() {
        assertNotEquals(Commands.fingerprint(Json.read("{\"a\":[1,2]}")),Commands.fingerprint(Json.read("{\"a\":[2,1]}")));
    }
}
