package app;
import io.javalin.http.BadRequestResponse;
import java.util.Locale;
public final class CustomerRules {
 private CustomerRules() {}
 public static String email(String value) {
  String email=value.trim().toLowerCase(Locale.ROOT);
  if(!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$") || email.length()>254) throw new BadRequestResponse("Ugyldig e-mailadresse");return email;
 }
}
