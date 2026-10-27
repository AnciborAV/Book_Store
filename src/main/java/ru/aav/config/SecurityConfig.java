package ru.aav.config;

import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import ru.aav.auth.AccountService;
import ru.aav.auth.UserRepository;

/** Сессионная авторизация: публичное чтение каталога, изменения только после входа. */
@Configuration
public class SecurityConfig {
    @Bean
    PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean
    UserDetailsService userDetailsService(UserRepository users) {
        return email -> users.findByEmail(AccountService.normalizeEmail(email))
                .map(u -> User.withUsername(u.getEmail()).password(u.getPasswordHash()).roles("READER").build())
                .orElseThrow(() -> new UsernameNotFoundException("Пользователь не найден"));
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // CSRF остаётся включённым, включая регистрацию, вход и выход.
        return http.authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/index.html", "/style.css", "/app.js", "/favicon.ico", "/error").permitAll()
                        .requestMatchers("/api/auth/csrf", "/api/auth/register").permitAll()
                        .requestMatchers(HttpMethod.GET, "/books", "/books/**").permitAll()
                        .anyRequest().authenticated())
                .formLogin(login -> login.loginProcessingUrl("/api/auth/login")
                        .usernameParameter("email")
                        .successHandler((request, response, auth) -> response.setStatus(204))
                        .failureHandler((request, response, error) -> response.setStatus(401)).permitAll())
                .logout(logout -> logout.logoutUrl("/api/auth/logout")
                        .logoutSuccessHandler((request, response, auth) -> response.setStatus(204)))
                .exceptionHandling(errors -> errors.authenticationEntryPoint((request, response, error) -> response.setStatus(401)))
                .build();
    }
}
