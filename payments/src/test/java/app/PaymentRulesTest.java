package app;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PaymentRulesTest { @Test void enforcesBusinessBoundaries(){PaymentRules.allow("PENDING","COMPLETED");PaymentRules.allow("COMPLETED","COMPLETED");assertThrows(RuntimeException.class,()->PaymentRules.allow("REQUESTED","COMPLETED"));assertThrows(RuntimeException.class,()->PaymentRules.allow("FAILED","COMPLETED"));} }
