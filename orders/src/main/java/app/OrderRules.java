package app;
import java.util.*;
public final class OrderRules {
 private OrderRules() {}
 public static boolean editable(String status) {return Set.of("PENDING","RESERVING","RESERVED").contains(status);}
 public static boolean claimable(String status,UUID current,UUID requested) {return "RESERVED".equals(status) || "PAYMENT_PENDING".equals(status) && requested.equals(current);}
 public static String expired(String status) {
  if(editable(status))return "CANCELLED";
  if(status.equals("PAYMENT_PENDING"))return "REVIEW_REQUIRED";
  return status;
 }
}
