package app;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CustomerRulesTest { @Test void enforcesBusinessBoundaries(){assertEquals("a@example.test",CustomerRules.email(" A@Example.Test ")); assertThrows(RuntimeException.class,()->CustomerRules.email("a@bad")); assertThrows(RuntimeException.class,()->CustomerRules.email("a b@example.test"));} }
