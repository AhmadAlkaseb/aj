package security;

import io.javalin.Javalin;
import io.javalin.http.ForbiddenResponse;
import java.net.URI;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Set;

public final class HttpSecurityConfig {
    // Sikker tilfældighed bruges til at lave en ny nonce for hver HTML-login-side.
    private static final SecureRandom RANDOM = new SecureRandom();
    // Disse HTTP-metoder kan ændre data og skal derfor kontrolleres for CSRF.
    private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    // Klassen bruges kun gennem den statiske register-metode og skal ikke oprettes med new.
    private HttpSecurityConfig() { }

    public static void register(Javalin app) {
        // Kører før controlleren ved hvert request, også hvis requestet senere bliver afvist.
        app.before(ctx -> {
            // JSON-svar skal ikke kunne køre scripts, hvis de åbnes som et dokument.
            ctx.header("Content-Security-Policy", "default-src 'none'");
            // Browseren skal læse JSON som JSON og ikke gætte, at det er HTML.
            ctx.header("X-Content-Type-Options", "nosniff");

            // CSRF-kontrol gælder ændrende requests samt starten af login, som opretter login-state.
            // Callbackets GET skal kunne komme fra Keycloak; dér kontrollerer AuthController state.
            boolean sensitive = WRITE_METHODS.contains(ctx.method().name())
                    || ctx.path().equals("/api/v1/auth/authorize");
            if (sensitive) {
                // Origin angiver afsenderens protokol, hostname og eventuelle port.
                // Hvis headeren er med, skal den matche vores konfigurerede API-origin.
                String origin = ctx.header("Origin");
                // Sec-Fetch-Site er browserens oplysning om, hvor requestet kommer fra.
                // En fremmed Origin eller værdien cross-site giver 403 Forbidden.
                // Klienter uden disse headers skal stadig opfylde kravene til JWT eller OAuth-state.
                if ((origin != null && !origin.equals(apiOrigin()))
                        || "cross-site".equals(ctx.header("Sec-Fetch-Site"))) {
                    throw new ForbiddenResponse("Request fra en fremmed side er ikke tilladt");
                }
            }
        });
    }

    // Kun vores HTML-login-sider behøver scripts og den længere CSP.
    public static void allowLoginScripts(io.javalin.http.Context ctx) {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        // Nonce er en tilfældig værdi, der markerer sidens egne scripts som tilladte.
        String nonce = Base64.getEncoder().encodeToString(bytes);
        ctx.attribute("cspNonce", nonce);
        ctx.header("Content-Security-Policy", "default-src 'none'; script-src 'nonce-" + nonce
                + "'; connect-src 'self'; form-action 'self'; base-uri 'none'; frame-ancestors 'none'");
    }

    private static String apiOrigin() {
        // Udleder fx http://localhost:7070 fra API_URL. En eventuel sti udelades.
        // Vi bruger vores egen konfiguration frem for at stole på requestets Host-header.
        URI uri = URI.create(AuthSettings.API_URL);
        return uri.getScheme() + "://" + uri.getRawAuthority();
    }
}
