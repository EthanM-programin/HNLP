package org.epiccure.mtg.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * TEMPORARY: opens every endpoint so the API can be tested before the role-based config exists.
 * Aleksander's SecurityConfig (client / volunteer / manager roles) replaces this file: delete it when that lands.
 */
@Configuration
public class DevSecurityConfig {

    @Bean
    SecurityFilterChain devFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(a -> a.anyRequest().permitAll())
            .csrf(c -> c.disable());
        return http.build();
    }
}
