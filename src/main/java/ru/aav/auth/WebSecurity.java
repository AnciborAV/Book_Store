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
    org.springframework.security.web.authentication.rememberme.PersistentTokenRepository rememberedTokens(javax.sql.DataSource dataSource) {
        var repository = new org.springframework.security.web.authentication.rememberme.JdbcTokenRepositoryImpl();
        repository.setDataSource(dataSource);
        return repository;
    }

    @Bean
    RememberedLogin rememberedLogin(UserDetailsService users,
        org.springframework.security.web.authentication.rememberme.PersistentTokenRepository tokens,
        org.springframework.security.core.session.SessionRegistry sessions,
        @org.springframework.beans.factory.annotation.Value("${bookstore.remember-me.secure:true}") boolean secure) {
        return new RememberedLogin(users, tokens, sessions, secure);
    }

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http, org.springframework.security.core.session.SessionRegistry sessions, RememberedLogin rememberedLogin) throws Exception {
        return http
            .headers(h -> h.frameOptions(f -> f.disable()).addHeaderWriter((req, res) ->
                res.setHeader("X-Frame-Options", java.util.Set.of("/ai-atlas.html", "/ai-architects.html", "/ai-history.html", "/ai-models.html", "/ai-russia.html").stream().anyMatch(path -> (req.getContextPath() + path).equals(req.getRequestURI())) ? "SAMEORIGIN" : "DENY")))
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
                        "/reader.html",
                        "/reader.js",
                        "/reader.css",
                        "/pdf-reading-code.html",
                        "/remember-login-code.html",
                        "/pdf-download-code.html",
                        "/pdf-reading-code.css",
                        "/pdfjs/5.6.205/**",
                        "/api/java-library/*/content",
                        "/feedback.js",
                        "/feedback-code.js",
                        "/admin-reviews.js",
                        "/announcement.js",
                        "/about.js",
                        "/about.css",
                        "/api/about",
                        "/api/about/images/*",
                        "/editorial.js",
                        "/editorial-prose.js",
                        "/text-formatting.js",
                        "/text-style-model.js",
                        "/text-formatting.css",
                        "/catalogue-admin.js",
                        "/catalogue-admin.css",
                        "/book-ordering.js",
                        "/book-ordering.css",
                        "/api/catalogue",
                        "/api/catalogue/books/*/*",
                        "/api/catalogue/covers/*",
                        "/editorial.css",
                        "/reading-text.css",
                        "/ai.css",
                        "/ai-navigation.js",
                        "/ai-architects.html",
                        "/ai-history.html",
                        "/ai-story.css",
                        "/ai-story.js",
                        "/ai-models.html",
                        "/ai-russia.html",
                        "/ai-russia.css",
                        "/ai-russia.js",
                        "/ai-models.css",
                        "/ai-models.js",
                        "/reading-atlas.html",
                        "/digital-atlas.html",
                        "/digital-atlas.css",
                        "/digital-atlas.js",
                        "/digital-atlas-core.js",
                        "/digital-atlas-data.js",
                        "/programming-atlas.html",
                        "/programming-atlas.js",
                        "/programming-atlas-core.js",
                        "/programming-atlas-data.js",
                        "/programming-atlas.css",
                        "/reading-atlas.js",
                        "/reading-atlas-data.js",
                        "/reading-atlas.css",
                        "/atlas-navigation.js",
                        "/atlas-navigation.css",
                        "/ai-atlas.html",
                        "/ai-atlas.css",
                        "/ai-atlas.js",
                        "/api/editorial/books",
                        "/api/editorial/books/*/*",
                        "/api/editorial/books/*/*/authors",
                        "/announcement.css",
                        "/api/announcement",
                        "/feedback.css",
                        "/api/site-feedback",
                        "/api/java-library",
                        "/api/java-library/metadata",
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
            .rememberMe(r -> r.key(RememberedLogin.PROVIDER_KEY).rememberMeServices(rememberedLogin))
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
