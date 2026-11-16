package vn.edu.dhktpm19att.group10.petfood.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, HttpSessionSecurityContextRepository contexts,
            HttpSessionCsrfTokenRepository csrf) throws Exception {
        http.securityContext(config -> config.securityContextRepository(contexts))
                .csrf(config -> config.csrfTokenRepository(csrf))
                .exceptionHandling(config -> config.authenticationEntryPoint((request, response, error) -> {
                    String me = request.getContextPath() + "/api/v1/me";
                    boolean profile = me.equals(request.getRequestURI()) || request.getRequestURI().startsWith(me + "/");
                    boolean checkout = (request.getContextPath() + "/api/v1/checkout/preview").equals(request.getRequestURI())
                            || (request.getContextPath() + "/api/v1/orders").equals(request.getRequestURI());
                    boolean orderDetail = request.getRequestURI().startsWith(request.getContextPath() + "/api/v1/orders/");
                    response.setStatus(profile || checkout || orderDetail ? 401 : 403);
                }))
                .logout(config -> config.logoutUrl("/api/v1/auth/logout")
                        .logoutSuccessHandler((request, response, authentication) -> response.setStatus(204)));
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.GET, "/api/v1/info", "/actuator/health").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/categories", "/api/v1/products", "/api/v1/products/{id}").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/cart", "/api/v1/auth/csrf").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login").anonymous()
                // Public but CSRF-protected and limited per Session by AiQuota.
                .requestMatchers(HttpMethod.POST, "/api/v1/search/semantic", "/api/v1/assistant/messages").permitAll()
                // Opt-in tracking works per Session for guests too; writes still need CSRF.
                .requestMatchers(HttpMethod.GET, "/api/v1/consent", "/api/v1/recommendations").permitAll()
                .requestMatchers(HttpMethod.PUT, "/api/v1/consent").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/events").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/me").authenticated()
                .requestMatchers("/api/v1/me/pets", "/api/v1/me/pets/**").hasAnyRole("CUSTOMER", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/checkout/preview").hasAnyRole("CUSTOMER", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/orders").hasAnyRole("CUSTOMER", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/orders/{id}/cancel").hasAnyRole("CUSTOMER", "ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/orders", "/api/v1/orders/{id}").hasAnyRole("CUSTOMER", "ADMIN")
                // Controllers re-check that the admin is still ACTIVE in the database on every request.
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/cart/items").permitAll()
                .requestMatchers(HttpMethod.PATCH, "/api/v1/cart/items/{id}").permitAll()
                .requestMatchers(HttpMethod.DELETE, "/api/v1/cart/items/{id}").permitAll()
                .anyRequest().denyAll());
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }

    @Bean
    HttpSessionSecurityContextRepository securityContexts() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    HttpSessionCsrfTokenRepository csrfTokens() {
        return new HttpSessionCsrfTokenRepository();
    }
}

