package vn.edu.dhktpm19att.group10.petfood.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // No login/admin API yet. Keep CSRF enabled and deny unfinished routes.
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.GET, "/api/v1/info", "/actuator/health").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/categories", "/api/v1/products", "/api/v1/products/{id}").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/cart", "/api/v1/auth/csrf").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/cart/items").permitAll()
                .requestMatchers(HttpMethod.PATCH, "/api/v1/cart/items/{id}").permitAll()
                .requestMatchers(HttpMethod.DELETE, "/api/v1/cart/items/{id}").permitAll()
                .anyRequest().denyAll());
        return http.build();
    }
}

