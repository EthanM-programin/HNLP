package org.epiccure.mtg.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Role-based access (replaces DevSecurityConfig):
 *  - Public (no ID required, Epic Cure's dignity-first policy): landing page, kiosk, shopper phone pages,
 *    and their APIs: /api/checkin, /api/capacity, /api/session/**  (a session token acts as the client's key)
 *  - VOLUNTEER: checkout terminal + restock route (/volunteer/**, /api/checkout/**, /api/restock)
 *  - MANAGER (also has VOLUNTEER): analytics + admin (/manager/**, /api/analytics/**, /api/admin/**)
 * Staff accounts come from environment variables so no password is ever committed.
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(a -> a
                .requestMatchers("/manager/**", "/api/analytics/**", "/api/admin/**").hasRole("MANAGER")
                .requestMatchers("/volunteer/**", "/api/checkout/**", "/api/restock").hasAnyRole("VOLUNTEER", "MANAGER")
                .anyRequest().permitAll())
            .formLogin(f -> f.loginPage("/login").permitAll().defaultSuccessUrl("/", false))
            .httpBasic(Customizer.withDefaults())          // lets curl / Invoke-RestMethod -Credential call staff APIs
            .logout(l -> l.logoutSuccessUrl("/login?logout"))
            .exceptionHandling(e -> e.accessDeniedHandler(accessDenied()))
            // JSON API is called by our own pages via fetch(); /logout is exempt so the "Sign out" and
            // "Sign in as a manager" buttons can sign out with a plain POST (logout CSRF is low risk here)
            .csrf(c -> c.ignoringRequestMatchers("/api/**", "/logout"));
        return http.build();
    }

    /**
     * Signed in, but the wrong role (e.g. a volunteer opening the manager dashboard):
     * pages go to a friendly explanation with a "sign in as a manager" button; API calls get JSON.
     */
    private AccessDeniedHandler accessDenied() {
        return (request, response, ex) -> {
            String uri = request.getRequestURI();
            if (uri.startsWith("/api/")) {
                response.setStatus(403);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"FORBIDDEN\",\"message\":\"Your account can't open this. Sign in with a manager account.\"}");
            } else {
                response.sendRedirect("/denied.html?from=" + URLEncoder.encode(uri, StandardCharsets.UTF_8));
            }
        };
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    UserDetailsService users(PasswordEncoder encoder,
                             @Value("${app.users.volunteer-password:volunteer}") String volunteerPassword,
                             @Value("${app.users.manager-password:manager}") String managerPassword) {
        return new InMemoryUserDetailsManager(
                User.withUsername("volunteer").password(encoder.encode(volunteerPassword)).roles("VOLUNTEER").build(),
                User.withUsername("manager").password(encoder.encode(managerPassword)).roles("MANAGER", "VOLUNTEER").build());
    }
}
