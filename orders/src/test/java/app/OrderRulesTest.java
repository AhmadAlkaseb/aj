package app;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class OrderRulesTest { @Test void enforcesBusinessBoundaries(){var a=java.util.UUID.randomUUID();assertTrue(OrderRules.claimable("PAYMENT_PENDING",a,a));assertFalse(OrderRules.claimable("CANCELLED",null,a));assertEquals("REVIEW_REQUIRED",OrderRules.expired("PAYMENT_PENDING"));assertEquals("COMPLETED",OrderRules.expired("COMPLETED"));assertEquals("CANCELLED",OrderRules.expired("RESERVED"));assertEquals("FAILED",OrderRules.expired("FAILED"));} }
