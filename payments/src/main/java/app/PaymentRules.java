package app;
import io.javalin.http.ConflictResponse;
public final class PaymentRules {
 private PaymentRules() {}
 public static void allow(String current,String requested) {
  if(!java.util.Set.of("COMPLETED","FAILED").contains(requested)) throw new IllegalArgumentException("Invalid result");
  if(!current.equals(requested) && !current.equals("PENDING")) throw new ConflictResponse("Betalingen afventer godkendelse eller er afsluttet");
 }
}
