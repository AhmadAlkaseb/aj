package security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import io.javalin.http.Context;
import io.javalin.http.UnauthorizedResponse;

import java.net.URI;
import java.util.Set;

public class AccessTokenValidator {
    // Nimbus-bibliotekets processor læser JWT'et og kontrollerer signatur og claims.
    // Claims er oplysninger i tokenet, fx udsteder, bruger-ID og udløbstid.
    private final DefaultJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
    // Bruges til at kontrollere, om tokenets ID er tilbagekaldt i Redis.
    private final TokenRevocationStore store;

    public AccessTokenValidator(TokenRevocationStore store) {
        this.store = store;
        try {
            // Keycloak underskriver med sin private nøgle. Vi kontrollerer signaturen
            // med de offentlige nøgler fra certs-endpointet. Biblioteket cacher nøglerne.
            var keys = JWKSourceBuilder.<SecurityContext>create(
                    URI.create(AuthSettings.ISSUER + "/protocol/openid-connect/certs").toURL())
                    .build();
            // Accepterer kun signaturer med RS256 og de nøgler, vi henter fra Keycloak.
            processor.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, keys));

            // Audience skal indeholde vores CLIENT_ID, og issuer skal matche vores Keycloak-realm.
            // Derudover skal disse claims være til stede:
            // exp: udløbstid, sub: bruger-ID, jti: token-ID, iss: udsteder, aud: modtager.
            var verifier = new DefaultJWTClaimsVerifier<SecurityContext>(
                    AuthSettings.CLIENT_ID,
                    new JWTClaimsSet.Builder().issuer(AuthSettings.ISSUER).build(),
                    Set.of("exp", "sub", "jti", "iss", "aud"));
            // Ingen ekstra tidsmargin: et udløbet token afvises med det samme.
            verifier.setMaxClockSkew(0);
            // Tilknytter claim-kontrollen, så den udføres under processor.process nedenfor.
            processor.setJWTClaimsSetVerifier(verifier);
        } catch (java.net.MalformedURLException exception) {
            throw new IllegalStateException("Ugyldig Keycloak-adresse", exception);
        }
    }

    public JWTClaimsSet validate(String token) {
        JWTClaimsSet claims;
        try {
            // Kontrollerer tokenets format, signatur, krævede claims og gyldighedstid.
            // null betyder her, at vi ikke sender en ekstra SecurityContext til biblioteket.
            claims = processor.process(token, null);
            // Keycloaks access token har typ=Bearer. Et ID-token må ikke bruges til API-adgang.
            // Token-ID og bruger-ID må heller ikke være tomme tekster.
            if (!"Bearer".equals(claims.getStringClaim("typ"))
                    || claims.getJWTID().isBlank() || claims.getSubject().isBlank()) {
                throw new UnauthorizedResponse("Ugyldigt access token");
            }
        } catch (java.text.ParseException | com.nimbusds.jose.JOSEException
                 | com.nimbusds.jose.proc.BadJOSEException exception) {
            // Ugyldigt format, signatur eller claims giver HTTP 401 Unauthorized.
            throw new UnauthorizedResponse("Ugyldigt eller udløbet access token");
        }
        // Et korrekt signeret token kan stadig være spærret, fx efter logout.
        if (store.isRevoked(claims.getJWTID())) {
            throw new UnauthorizedResponse("Tokenet er tilbagekaldt");
        }
        // Først nu kan resten af programmet bruge tokenets validerede oplysninger.
        return claims;
    }

    public JWTClaimsSet authenticate(Context ctx) {
        // Klienten skal sende: Authorization: Bearer <JWT>.
        String header = ctx.header("Authorization");
        // De første 7 tegn er "Bearer ". true betyder, at store/små bogstaver ignoreres.
        // substring(7) er selve JWT'et, som ikke må være tomt.
        if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)
                || header.substring(7).isBlank()) {
            // Fortæller klienten, at endpointet kræver bearer-autentifikation.
            ctx.header("WWW-Authenticate", "Bearer");
            throw new UnauthorizedResponse("Bearer-token mangler");
        }
        try {
            // Fjerner "Bearer " og sender kun tokenet til valideringen.
            return validate(header.substring(7));
        } catch (UnauthorizedResponse exception) {
            // Bevarer 401-fejlen og tilføjer også autentifikationsheaderen ved ugyldigt token.
            ctx.header("WWW-Authenticate", "Bearer");
            throw exception;
        }
    }
}
