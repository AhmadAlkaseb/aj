package com.aj.products;
import java.util.UUID;
public final class InventoryRules {
 private InventoryRules() {}
 public static boolean canReserve(String status,UUID current,UUID requested) {return "AVAILABLE".equals(status) || requested.equals(current) && "RESERVED".equals(status);}
 public static String signal(String current,String topic) {
  if("PAID".equals(current) || topic.equals("payment.completed")) return "PAID";
  if("RELEASED".equals(current) || java.util.Set.of("order.cancelled","paymentfail").contains(topic)) return "RELEASED";
  return "REQUESTED";
 }
 public static boolean editable(String status) {return !java.util.Set.of("RESERVED","SOLD").contains(status);}
}
