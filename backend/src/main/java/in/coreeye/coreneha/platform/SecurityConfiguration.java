package in.coreeye.coreneha.platform;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

/**
 * Web security. Increment 2a: every API request requires authentication and
 * is refused with 401 until login (OIDC back-end-for-front-end session) is
 * added in increment 2c. Actuator endpoints are served only on the internal
 * management port, which is never published (ADR-0003).
 */
@Configuration
class SecurityConfiguration {

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
    SecurityFilterChain api(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
            .exceptionHandling(ex -> ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));
        return http.build();
    }
}
