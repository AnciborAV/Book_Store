package ru.aav.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class WebSecurity {

    @Bean
    org.springframework.security.core.session.SessionRegistry sessionRegistry() {
        return new org.springframework.security.core.session.SessionRegistryImpl();
    }
    @Bean
    org.springframework.security.web.session.HttpSessionEventPublisher sessionEvents() {
        return new org.springframework.security.web.session.HttpSessionEventPublisher();
    }
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService users(UserRepository repository) {
        return email ->
            repository
                .findByEmail(CurrentAccount.normalize(email))
                .map(u ->
                    User.withUsername(u.getEmail())
                        .password(u.getPasswordHash())
                        .roles(u.getRole())
                        .build()
                )
                .orElseThrow(() ->
                    new UsernameNotFoundException("User not found")
                );
    }

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http, org.springframework.security.core.session.SessionRegistry sessions) throws Exception {
        return http
            .authorizeHttpRequests(a ->
                a
                    .requestMatchers(
                        org.springframework.http.HttpMethod.GET,
                        "/api/books/*/reviews"
                    )
                    .permitAll()
                    .requestMatchers(
                        "/api/auth/csrf",
                        "/api/auth/register",
                        "/api/auth/login",
                        "/api/auth/password/request",
                        "/api/auth/password/reset"
                    )
                    .permitAll()
                    .requestMatchers(
                        org.springframework.http.HttpMethod.GET,
                        "/books",
                        "/books/**",
                        "/",
                        "/index.html",
                        "/app.js",
                        "/password-recovery.js",
                        "/password-recovery.css",
                        "/api-client.js",
                        "/commerce.js",
                        "/java-library.js",
                        "/feedback.js",
                        "/admin-reviews.js",
                        "/announcement.js",
                        "/editorial.js",
                        "/editorial.css",
                        "/api/editorial/books",
                        "/api/editorial/books/*/*",
                        "/announcement.css",
                        "/api/announcement",
                        "/feedback.css",
                        "/api/site-feedback",
                        "/api/java-library",
                        "/api/java-library/*/cover",
                        "/api/java-library/*/download",
                        "/style.css",
                        "/catalog-covers.json",
                        "/covers/**",
                        "/favicon.ico"
                    )
                    .permitAll()
                    .requestMatchers("/api/admin/**")
                    .hasRole("ADMIN")
                    .requestMatchers(
                        org.springframework.http.HttpMethod.POST,
                        "/books",
                        "/books/**"
                    )
                    .hasRole("ADMIN")
                    .requestMatchers("/error")
                    .permitAll()
                    .anyRequest()
                    .authenticated()
            )
            .sessionManagement(s -> s.maximumSessions(-1).sessionRegistry(sessions)
                .expiredSessionStrategy(e -> e.getResponse().sendError(401)))
            .exceptionHandling(e ->
                e.authenticationEntryPoint((req, res, ex) -> res.sendError(401))
            )
            .formLogin(f ->
                f
                    .loginProcessingUrl("/api/auth/login")
                    .usernameParameter("email")
                    .successHandler((req, res, auth) -> res.setStatus(204))
                    .failureHandler((req, res, ex) ->
                        res.sendError(401, "Неверная почта или пароль")
                    )
            )
            .logout(l ->
                l
                    .logoutUrl("/api/auth/logout")
                    .logoutSuccessHandler((req, res, auth) ->
                        res.setStatus(204)
                    )
            )
            .build();
    }
}
