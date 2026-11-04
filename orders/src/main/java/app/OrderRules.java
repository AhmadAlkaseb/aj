package app;
import java.util.*;
public final class OrderRules {
 private OrderRules() {}
 public static boolean editable(String status) {return Set.of("PENDING","RESERVING","RESERVED").contains(status);}
 public static boolean claimable(String status,UUID current,UUID requested) {return "RESERVED".equals(status) || "PAYMENT_PENDING".equals(status) && requested.equals(current);}
 public static String overdue(String status,long ageSeconds) {
  if(Set.of("PENDING","RESERVING").contains(status) && ageSeconds>=300) return "CANCELLED";
  if(status.equals("RESERVED") && ageSeconds>=1800) return "CANCELLED";
  if(status.equals("PAYMENT_PENDING") && ageSeconds>=1800) return "REVIEW_REQUIRED";
  return status;
 }
}
