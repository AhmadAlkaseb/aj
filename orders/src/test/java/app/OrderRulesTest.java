package app;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class OrderRulesTest { @Test void enforcesBusinessBoundaries(){var a=java.util.UUID.randomUUID();assertTrue(OrderRules.claimable("PAYMENT_PENDING",a,a));assertFalse(OrderRules.claimable("CANCELLED",null,a));assertEquals("REVIEW_REQUIRED",OrderRules.overdue("PAYMENT_PENDING",1800));assertEquals("COMPLETED",OrderRules.overdue("COMPLETED",99999));assertEquals("CANCELLED",OrderRules.overdue("RESERVED",1800));assertEquals("RESERVED",OrderRules.overdue("RESERVED",1799));} }
