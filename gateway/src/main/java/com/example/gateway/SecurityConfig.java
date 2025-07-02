package com.example.gateway;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        return http.csrf(csrf -> csrf.disable()).authorizeExchange(authorizeExchangeSpec ->
            authorizeExchangeSpec
                .pathMatchers("/actuator/**").permitAll() // Allow access to actuator endpoints
                .anyExchange().authenticated() // Require authentication for all other requests
        ).oauth2ResourceServer(
            oauth2 -> oauth2.jwt(Customizer.withDefaults()) // Use JWT for OAuth2 resource server
        ).build();
    }

}
