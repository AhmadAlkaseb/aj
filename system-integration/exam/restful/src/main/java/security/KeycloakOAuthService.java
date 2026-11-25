package security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.ServiceUnavailableResponse;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Duration;
import java.util.Base64;

public class KeycloakOAuthService {
    // Redis-lageret gemmer midlertidig state og PKCE-verifier under login.
    private final LoginStateStore loginStateStore;
    // Genbruger samme HTTP-klient. Forbindelsen til Keycloak må højst tage 5 sekunder.
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    // Omdanner Keycloaks JSON-svar til et Java-objekt, vi kan læse felter fra.
    private final ObjectMapper json = new ObjectMapper();

    public KeycloakOAuthService(LoginStateStore loginStateStore) {
        this.loginStateStore = loginStateStore;
    }

    // Samler loginets state og den URL, browseren skal åbne hos Keycloak.
    public record Login(String state, String authorizationUrl) { }

    public Login startLogin() {
        // State forbinder callbacket med det login, browseren startede, og beskytter mod login-CSRF.
        String state = randomValue();
        // Verifier er en hemmelig engangsværdi, som API'et senere sender ved kodeudvekslingen.
        String verifier = randomValue();
        String challenge;
        try {
            // PKCE: challenge er SHA-256-hashen af verifier, kodet som URL-sikker Base64.
            // Keycloak får challenge nu og kontrollerer senere, at verifier passer til den.
            challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException exception) {
            // SHA-256 skal findes i Java. Mangler den, er det en fejl i programmets miljø.
            throw new IllegalStateException(exception);
        }
        // Gemmer verifier under state i Redis i 5 minutter. Verifier sendes ikke til browseren.
        loginStateStore.saveLogin(state, verifier);

        // Bygger Keycloaks login-URL:
        // client_id identificerer vores klient, og redirect_uri er vores callback efter login.
        // response_type=code vælger Authorization Code-flowet; openid tilføjer OpenID Connect.
        // S256 fortæller, at PKCE-challenge er lavet med SHA-256.
        String url = AuthSettings.ISSUER + "/protocol/openid-connect/auth"
                + "?client_id=" + encode(AuthSettings.CLIENT_ID)
                + "&redirect_uri=" + encode(AuthSettings.CALLBACK)
                + "&response_type=code&scope=openid&state=" + encode(state)
                + "&code_challenge=" + encode(challenge) + "&code_challenge_method=S256";
        return new Login(state, url);
    }

    public JsonNode exchangeCode(String code, String state) {
        // AuthController har allerede kontrolleret state mod browserens cookie.
        // Henter og sletter verifier atomisk, så dette loginforsøg kun kan bruges én gang.
        String verifier = loginStateStore.consumeLogin(state);
        if (verifier == null) {
            throw new BadRequestResponse("Login er udløbet eller allerede brugt");
        }
        // Sender autorisationskoden og den oprindelige verifier til Keycloak.
        // Callback-adressen skal være den samme som ved starten af login-flowet.
        String form = "grant_type=authorization_code&client_id=" + encode(AuthSettings.CLIENT_ID)
                + "&redirect_uri=" + encode(AuthSettings.CALLBACK)
                + "&code=" + encode(code) + "&code_verifier=" + encode(verifier);
        try {
            // Token-endpointet modtager formularfelter via POST, ikke en JSON-body.
            // Hele requestet må højst tage 10 sekunder.
            var request = HttpRequest.newBuilder(URI.create(
                            AuthSettings.ISSUER + "/protocol/openid-connect/token"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form)).build();
            // Venter på Keycloaks svar og læser response-body som tekst.
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 400) {
                // Keycloak afviser fx en udløbet kode, genbrug af kode eller forkert verifier.
                throw new BadRequestResponse("Login-koden er ugyldig eller udløbet");
            }
            if (response.statusCode() != 200) {
                throw new ServiceUnavailableResponse("Keycloak kunne ikke gennemføre login");
            }
            // Returnerer token-svaret som JSON. AuthController validerer access tokenet
            // gennem AccessTokenValidator, før det sendes tilbage til klienten.
            return json.readTree(response.body());
        } catch (java.io.IOException exception) {
            throw new ServiceUnavailableResponse("Keycloak er utilgængelig");
        } catch (InterruptedException exception) {
            // Bevarer informationen om afbrydelsen, så resten af programmet kan reagere på den.
            Thread.currentThread().interrupt();
            throw new ServiceUnavailableResponse("Login blev afbrudt");
        }
    }

    public String logoutUrl() {
        // Browseren åbner denne URL for at afslutte Keycloaks SSO-session.
        // Bagefter sendes brugeren tilbage til login-siden. API-tokenet tilbagekaldes separat i Redis.
        return AuthSettings.ISSUER + "/protocol/openid-connect/logout"
                + "?client_id=" + encode(AuthSettings.CLIENT_ID)
                + "&post_logout_redirect_uri=" + encode(AuthSettings.LOGIN_PAGE);
    }

    private static String randomValue() {
        // Laver 32 sikre tilfældige bytes og omdanner dem til URL-sikker tekst uden afsluttende '='.
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String encode(String value) {
        // Koder specialtegn i en parameter, så fx '&' ikke bliver læst som en ny parameter.
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
