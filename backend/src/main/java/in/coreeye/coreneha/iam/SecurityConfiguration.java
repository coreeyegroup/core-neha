package in.coreeye.coreneha.iam;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.function.Supplier;

import in.coreeye.coreneha.audit.AuditEntry;
import in.coreeye.coreneha.audit.AuditLog;
import in.coreeye.coreneha.audit.AuditOutcome;
import in.coreeye.coreneha.kernel.CoreNehaProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.oidc.authentication.OidcIdTokenDecoderFactory;
import org.springframework.security.oauth2.client.oidc.authentication.OidcIdTokenValidator;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.jwt.JwtDecoderFactory;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;

/**
 * Login and session security (ADR-0005). The backend is the OIDC confidential
 * client; the browser holds only an HttpOnly session cookie (path-scoped to the
 * API) and a CSRF token cookie. API calls without a valid session get 401 (no
 * redirect); the staff application starts login by navigating to
 * {@code <api>/oauth2/authorization/keycloak}.
 */
@Configuration
@ConditionalOnWebApplication
class SecurityConfiguration {

    static final String AUTHENTICATED_AT = "coreneha.authenticatedAt";

    @Bean
    @Order(1)
    SecurityFilterChain managementEndpoints(HttpSecurity http) throws Exception {
        http.securityMatcher(request -> request.getRequestURI().startsWith("/actuator"))
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
            .csrf(csrf -> csrf.disable());
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain api(HttpSecurity http, CoreNehaProperties props, AuditLog audit) throws Exception {
        CookieCsrfTokenRepository csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        // Readable by the staff application at <base>/, sent back as X-XSRF-TOKEN.
        csrfRepository.setCookieCustomizer(cookie -> cookie.path(props.basePath()).secure(true).sameSite("Lax"));
        http.authorizeHttpRequests(auth -> auth
                    .requestMatchers("/v1/public/**", "/oauth2/**", "/login/**", "/error").permitAll()
                    .anyRequest().authenticated())
            .exceptionHandling(ex -> ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
            .csrf(csrf -> csrf.csrfTokenRepository(csrfRepository).csrfTokenRequestHandler(new LoadingCsrfTokenRequestHandler()))
            .oauth2Login(login -> login
                    .successHandler(successHandler(props, audit))
                    .failureHandler(failureHandler(props, audit)))
            .logout(logout -> logout
                    .logoutUrl("/logout")
                    .logoutSuccessHandler(logoutSuccessHandler(props, audit)));
        return http.build();
    }

    /** Validates ID tokens against the public issuer although keys are fetched over the internal network. */
    @Bean
    JwtDecoderFactory<ClientRegistration> idTokenDecoderFactory(CoreNehaProperties props) {
        OidcIdTokenDecoderFactory factory = new OidcIdTokenDecoderFactory();
        factory.setJwtValidatorFactory(registration -> new DelegatingOAuth2TokenValidator<>(
                new OidcIdTokenValidator(registration), new JwtIssuerValidator(props.identity().publicIssuer())));
        return factory;
    }

    private static AuthenticationSuccessHandler successHandler(CoreNehaProperties props, AuditLog audit) {
        return (request, response, authentication) -> {
            request.getSession().setAttribute(AUTHENTICATED_AT, Instant.now().toEpochMilli());
            audit.recordIndependently(new AuditEntry(null, null, "subject:" + authentication.getName(), "iam.login",
                    "login_identity", authentication.getName(), null, AuditOutcome.SUCCESS, null, Instant.now(), Map.of()));
            response.sendRedirect(props.publicBaseUrl() + "/staff/");
        };
    }

    private static AuthenticationFailureHandler failureHandler(CoreNehaProperties props, AuditLog audit) {
        return (request, response, exception) -> {
            audit.recordIndependently(new AuditEntry(null, null, "anonymous", "iam.login", "login_identity", null, null,
                    AuditOutcome.FAILED, exception.getClass().getSimpleName(), Instant.now(), Map.of()));
            response.sendRedirect(props.publicBaseUrl() + "/staff/signed-out?reason=login-failed");
        };
    }

    /** Ends the application session, then the identity-provider session, then returns to the signed-out page. */
    private static LogoutSuccessHandler logoutSuccessHandler(CoreNehaProperties props, AuditLog audit) {
        return (HttpServletRequest request, HttpServletResponse response, Authentication authentication) -> {
            // Registered post-logout address; the gateway forwards it to the staff application's signed-out page.
            String signedOut = props.publicBaseUrl() + "/signed-out";
            if (authentication != null) {
                audit.recordIndependently(new AuditEntry(null, null, "subject:" + authentication.getName(), "iam.logout",
                        "login_identity", authentication.getName(), null, AuditOutcome.SUCCESS, null, Instant.now(), Map.of()));
            }
            if (authentication != null && authentication.getPrincipal() instanceof OidcUser oidc) {
                String url = props.identity().publicIssuer() + "/protocol/openid-connect/logout"
                        + "?client_id=" + enc(props.identity().staffClientId())
                        + "&id_token_hint=" + enc(oidc.getIdToken().getTokenValue())
                        + "&post_logout_redirect_uri=" + enc(signedOut);
                response.sendRedirect(url);
            } else {
                response.sendRedirect(signedOut);
            }
        };
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /**
     * Plain (non-masked) CSRF tokens read from the cookie, sent as header by
     * scripts or as the _csrf form field by the logout form. Loading the token on
     * every request keeps the cookie present for the single-page application.
     */
    static final class LoadingCsrfTokenRequestHandler implements CsrfTokenRequestHandler {
        private final CsrfTokenRequestAttributeHandler delegate = new CsrfTokenRequestAttributeHandler();

        @Override
        public void handle(HttpServletRequest request, HttpServletResponse response, Supplier<CsrfToken> csrfToken) {
            delegate.handle(request, response, csrfToken);
            csrfToken.get();
        }

        @Override
        public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
            return delegate.resolveCsrfTokenValue(request, csrfToken);
        }
    }
}
