package com.aj.products;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class InventoryRulesTest { @Test void enforcesBusinessBoundaries(){var a=java.util.UUID.randomUUID();var b=java.util.UUID.randomUUID();assertTrue(InventoryRules.canReserve("AVAILABLE",null,a));assertTrue(InventoryRules.canReserve("RESERVED",a,a));assertFalse(InventoryRules.canReserve("RESERVED",a,b));assertFalse(InventoryRules.canReserve("SOLD",a,a));assertFalse(InventoryRules.editable("SOLD"));
assertEquals("RELEASED",InventoryRules.signal(InventoryRules.signal(null,"order.cancelled"),"quantitycheck"));
assertEquals("RELEASED",InventoryRules.signal(InventoryRules.signal(null,"quantitycheck"),"order.cancelled"));
assertEquals("PAID",InventoryRules.signal(InventoryRules.signal(null,"payment.completed"),"order.cancelled"));} }
