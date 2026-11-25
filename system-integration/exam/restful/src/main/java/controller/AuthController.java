package controller;

import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import security.AccessTokenValidator;
import security.AuthSettings;
import security.HttpSecurityConfig;
import security.KeycloakOAuthService;
import security.LoginStateStore;
import security.TokenRevocationStore;
import java.util.Map;

public class AuthController {
    // Controlleren håndterer HTTP-requests. Services udfører arbejdet med Redis, OAuth og JWT.
    private final TokenRevocationStore revocations;
    private final LoginStateStore loginStateStore;
    private final AccessTokenValidator jwt;
    private final KeycloakOAuthService oauth;

    public AuthController(TokenRevocationStore revocations, LoginStateStore loginStateStore,
                          AccessTokenValidator jwt) {
        // Modtager det lager og den JWT-service, som blev oprettet ved registrering af routes.
        this.revocations = revocations;
        this.loginStateStore = loginStateStore;
        this.jwt = jwt;
        this.oauth = new KeycloakOAuthService(loginStateStore);
    }

    // Data til token-requestet: code er Keycloaks engangskode, og state forbinder login med browseren.
    // Ingen af de to værdier er et JWT; tokenet modtages først efter kodeudvekslingen.
    public record TokenRequest(String code, String state) { }

    // Starter browser-flowet. Selve tokenet udstedes af POST /auth/tokens.
    public void authorize(Context ctx) {
        // Browseren og eventuelle mellemliggende caches må ikke gemme login-svaret.
        ctx.header("Cache-Control", "no-store");
        // Opretter state, PKCE-verifier og URL'en til Keycloak. Verifier bliver på serveren.
        var login = oauth.startLogin();
        // Gemmer state i browseren i 15 minutter. Dette er adskilt fra JWT'ets levetid på 15 minutter.
        setStateCookie(ctx, login.state(), 900);
        // Login-sidens JavaScript læser URL'en og sender browseren videre til Keycloak.
        ctx.status(HttpStatus.OK).json(Map.of("authorizationUrl", login.authorizationUrl()));
    }

    // POST /api/v1/auth/tokens: udveksler en gyldig engangskode til et access token.
    public void createToken(Context ctx) {
        ctx.header("Cache-Control", "no-store");
        TokenRequest request;
        // Callback-siden sender en HTML-formular, så felterne læses som formularparametre.
        if (ctx.contentType() != null && ctx.contentType().startsWith("application/x-www-form-urlencoded")) {
            request = new TokenRequest(ctx.formParam("code"), ctx.formParam("state"));
        } else {
            // Understøtter også JSON. Manglende felter afvises med 400 Bad Request af validatoren.
            request = ctx.bodyValidator(TokenRequest.class)
                    .check(body -> body != null && body.code() != null && !body.code().isBlank(),
                            "Login-kode mangler")
                    .check(body -> body != null && body.state() != null && !body.state().isBlank(),
                            "Login-state mangler").get();
        }
        // Kontrollen gælder begge inputtyper. State skal matche cookien fra starten af login.
        if (request.code() == null || request.code().isBlank() || !validState(ctx, request.state())) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("message", "Login-kode eller browser-state er ugyldig"));
            return;
        }
        // Max-Age=0 sletter login-cookien. Den skal ikke bruges til at holde brugeren logget ind.
        setStateCookie(ctx, "", 0);
        // KeycloakOAuthService henter og sletter verifier i Redis og sender code + verifier til Keycloak.
        var tokens = oauth.exchangeCode(request.code(), request.state());
        String accessToken = tokens.path("access_token").asText();
        // Kontrollerer signatur, claims, udløbstid og blacklist, inden tokenet gives til klienten.
        var claims = jwt.validate(accessToken);
        // Location angiver token-ressourcens adresse. Samme adresse bruges med DELETE ved logout.
        ctx.header("Location", routes.Routes.BASE_PATH + "/auth/tokens/" + claims.getJWTID());
        // 201 Created: tokenet er udstedt. token_id er JWT'ets jti, ikke selve JWT'et.
        // expires_in angiver antal sekunder til udløb; værdien kommer fra Keycloak.
        ctx.status(HttpStatus.CREATED).json(Map.of(
                "access_token", accessToken,
                "token_id", claims.getJWTID(),
                "token_type", "Bearer",
                "expires_in", tokens.path("expires_in").asLong()));
    }

    // DELETE /api/v1/auth/tokens/{tokenId}: logger det aktuelle API-token ud.
    public void deleteToken(Context ctx) {
        ctx.header("Cache-Control", "no-store");
        // Læser og validerer Authorization: Bearer <JWT>. Manglende/ugyldigt token giver 401.
        var claims = jwt.authenticate(ctx);
        // Læser token-ID'et fra adressen, som skal matche det validerede tokens jti.
        String tokenId = ctx.pathParam("tokenId");
        // Brugeren må kun tilbagekalde det access token, som requestet benytter.
        if (!tokenId.equals(claims.getJWTID())) {
            ctx.status(HttpStatus.NOT_FOUND).json(Map.of("message", "Tokenet findes ikke"));
            return;
        }
        // Redis gemmer ID'et på blacklisten, indtil JWT'et udløber. Derefter afvises samme JWT.
        revocations.revoke(tokenId, claims.getExpirationTime().toInstant());
        // Åbn dette link i browseren for også at afslutte Keycloaks SSO-session.
        ctx.header("Link", "<" + oauth.logoutUrl() + ">; rel=logout");
        // 204 No Content betyder, at logout lykkedes, og der ikke sendes en response-body.
        ctx.status(HttpStatus.NO_CONTENT);
    }

    // GET /api/v1/auth/callback: her vender browseren tilbage efter login hos Keycloak.
    public void callback(Context ctx) {
        ctx.header("Cache-Control", "no-store");
        // Skjul login-koden i Referer, men bevar formularens Origin ved POST til /auth/tokens.
        ctx.header("Referrer-Policy", "strict-origin");
        // Keycloak sender state tilbage i URL'en. Cookien viser, hvilket login browseren startede.
        String state = ctx.queryParam("state");
        if (!validState(ctx, state)) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("message", "Login-state matcher ikke browseren"));
            return;
        }
        // Keycloak kan returnere error, fx hvis brugeren afviser login.
        // Både browser-cookie og serverens loginoplysninger ryddes så op.
        if (ctx.queryParam("error") != null) {
            setStateCookie(ctx, "", 0);
            loginStateStore.consumeLogin(state);
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("message", "Login blev afvist"));
            return;
        }
        // Efter succesfuldt login skal URL'en indeholde Keycloaks autorisationskode.
        String code = ctx.queryParam("code");
        if (code == null || code.isBlank()) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("message", "Login-kode mangler"));
            return;
        }
        // Browseren sender koden som POST. Callbacket udsteder ikke selv et token.
        // Tillader vores eget script med en nonce: en tilfældig markering af det tilladte script.
        HttpSecurityConfig.allowLoginScripts(ctx);
        // De to første %s udfyldes med HTML-encodet code/state; den sidste er scriptets nonce.
        // Skjulte felter sendes automatisk til POST /auth/tokens. Knappen virker også uden JavaScript.
        ctx.html("""
                <!doctype html>
                <html lang="da"><meta charset="utf-8"><title>Afslut login</title>
                <form id="token-form" action="/api/v1/auth/tokens" method="post">
                    <input type="hidden" name="code" value="%s">
                    <input type="hidden" name="state" value="%s">
                    <button type="submit">Hent token</button>
                </form>
                <script nonce="%s">document.getElementById('token-form').submit();</script>
                </html>
                """.formatted(escapeHtml(code), escapeHtml(state), ctx.attribute("cspNonce")));
    }

    // GET /api/v1/auth: en lille hjælpeside til at starte hele login-flowet i samme browser.
    public void loginPage(Context ctx) {
        ctx.header("Cache-Control", "no-store");
        HttpSecurityConfig.allowLoginScripts(ctx);
        // Knappen henter login-URL'en fra authorize og åbner den i browseren.
        // Den deaktiveres undervejs, så gentagne klik ikke opretter flere konkurrerende loginforsøg.
        // Fejlbeskeden indsættes med textContent, så den behandles som tekst og ikke HTML.
        ctx.html("""
                <!doctype html>
                <html lang="da"><meta charset="utf-8"><title>Book Catalog Login</title>
                <h1>Book Catalog</h1>
                <p>Log ind via Keycloak. Vælg GitHub på næste side.</p>
                <button id="login">Log ind</button><p id="message"></p>
                <script nonce="%s">
                document.getElementById('login').onclick = async () => {
                    document.getElementById('login').disabled = true;
                    const response = await fetch('/api/v1/auth/authorize');
                    const data = await response.json();
                    if (response.ok) window.location.assign(data.authorizationUrl);
                    else {
                        document.getElementById('message').textContent = 'Login kunne ikke startes';
                        document.getElementById('login').disabled = false;
                    }
                };
                </script></html>
                """.formatted((String) ctx.attribute("cspNonce")));
    }

    private boolean validState(Context ctx, String state) {
        // Afviser manglende, tom eller unødigt lang state og sammenligner med browserens cookie.
        // Serverens engangsoplysninger kontrolleres senere af KeycloakOAuthService under kodeudvekslingen.
        return state != null && !state.isBlank() && state.length() <= 128
                && state.equals(ctx.cookie("oauth_state"));
    }

    private String escapeHtml(String value) {
        // Erstatter HTML-specialtegn, før input sættes ind i en HTML-attribut.
        // Fx bliver et citattegn til &quot;, så input ikke kan bryde ud af value="..." (XSS).
        return value.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace(String.valueOf((char) 34), "&quot;")
                .replace("'", "&#x27;");
    }

    private void setStateCookie(Context ctx, String state, int maxAge) {
        // Secure betyder, at cookien kun sendes via HTTPS. Lokal udvikling bruger HTTP.
        String secure = AuthSettings.API_URL.startsWith("https://") ? "; Secure" : "";
        // Path begrænser, hvilke adresser cookien sendes til. Max-Age er levetiden i sekunder.
        // HttpOnly forhindrer JavaScript i at læse cookien.
        // SameSite=Lax tillader navigation tilbage fra Keycloak, men begrænser cross-site POST.
        ctx.header("Set-Cookie", "oauth_state=" + state
                + "; Path=/api/v1/auth; Max-Age=" + maxAge + "; HttpOnly; SameSite=Lax" + secure);
    }
}
