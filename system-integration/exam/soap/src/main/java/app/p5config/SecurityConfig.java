package app.p5config;

import java.util.Map;
import org.springframework.context.annotation.*;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        
        return http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                // POST-kald kræver et gyldigt CSRF-token, der passer til sessionen.
                .csrf(Customizer.withDefaults())
                // Afviste kald, fx ved forkert CSRF-token, får HTTP 403 (Forbidden).
                .exceptionHandling(errors -> errors.accessDeniedHandler(
                        (request, response, exception) -> response.setStatus(403)))
                // Ekstra browserbeskyttelse: blokerer bl.a. scripts og indlejring i frames.
                .headers(headers -> headers.contentSecurityPolicy(csp ->
                        csp.policyDirectives("default-src 'none'; frame-ancestors 'none'")))
                .build();
    }

    // Lille HTTP-controller, der udleverer CSRF-tokenet som JSON.
    @RestController
    public static class CsrfController {
        // Klienten henter tokenet med GET /csrf før sit SOAP-kald.
        @GetMapping("/csrf")
        public Map<String, String> csrf(CsrfToken token) {
            return Map.of("headerName", token.getHeaderName(), "token", token.getToken());
        }
    }
}
