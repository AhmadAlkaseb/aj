package security;

public final class AuthSettings {
    // getOrDefault bruger miljøvariablen, hvis den findes. Ellers bruges den lokale standardværdi.
    // ISSUER identificerer det Keycloak-realm, som skal have udstedt vores JWT'er.
    public static final String ISSUER = System.getenv().getOrDefault(
            "KEYCLOAK_ISSUER", "http://localhost:8080/realms/bookcatalog");

    // Klientens navn i Keycloak. Bruges ved login og som forventet audience i access tokenet.
    public static final String CLIENT_ID = "book-api";

    // API'ets offentlige basisadresse. Kan ændres via miljøvariablen API_URL.
    public static final String API_URL = System.getenv().getOrDefault("API_URL", "http://localhost:7070");

    // Her sender Keycloak browseren tilbage med en autorisationskode efter login.
    // Adressen skal være tilladt som redirect URI for klienten i Keycloak.
    public static final String CALLBACK = API_URL + "/api/v1/auth/callback";

    // Vores login-side. Brugeren kan sendes hertil efter logout hos Keycloak.
    public static final String LOGIN_PAGE = API_URL + "/api/v1/auth";

    // Redis gemmer midlertidig login-state og blacklisten over tilbagekaldte token-ID'er.
    public static final String REDIS_URL = System.getenv().getOrDefault("REDIS_URL", "redis://localhost:6379");

    // Klassen indeholder kun konfiguration og skal ikke oprettes med new.
    private AuthSettings() { }
}
