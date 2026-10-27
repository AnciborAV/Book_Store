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
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
    @Bean UserDetailsService users(UserRepository repository) {
        return email -> repository.findByEmail(email.strip().toLowerCase(java.util.Locale.ROOT))
            .map(u -> User.withUsername(u.getEmail()).password(u.getPasswordHash()).roles("READER").build())
            .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }
    // The existing public /books REST API retains its original contract.
    @Bean SecurityFilterChain apiSecurity(HttpSecurity http) throws Exception {
        return http.securityMatcher("/api/**")
            .authorizeHttpRequests(a -> a.requestMatchers(org.springframework.http.HttpMethod.GET, "/api/books/*/reviews").permitAll()
                .requestMatchers("/api/auth/csrf", "/api/auth/register", "/api/auth/login").permitAll()
                .anyRequest().authenticated())
            .exceptionHandling(e -> e.authenticationEntryPoint((req, res, ex) -> res.sendError(401)))
            .formLogin(f -> f.loginProcessingUrl("/api/auth/login").usernameParameter("email")
                .successHandler((req, res, auth) -> res.setStatus(204))
                .failureHandler((req, res, ex) -> res.sendError(401, "Неверная почта или пароль")))
            .logout(l -> l.logoutUrl("/api/auth/logout").logoutSuccessHandler((req, res, auth) -> res.setStatus(204)))
            .build();
    }
}
