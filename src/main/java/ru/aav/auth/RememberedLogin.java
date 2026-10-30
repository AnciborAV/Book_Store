package ru.aav.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.RegisterSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.rememberme.PersistentTokenBasedRememberMeServices;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;

/** Opaque database-backed tokens; never a stored browser password. */
public final class RememberedLogin extends PersistentTokenBasedRememberMeServices {
    // This identifies the AuthenticationProvider, not a cookie signing secret.
    public static final String PROVIDER_KEY = "bookstore-persistent-login";
    public static final int LIFETIME_SECONDS = 30 * 24 * 60 * 60;
    private final boolean secureCookie;
    private final SessionRegistry sessions;

    public RememberedLogin(UserDetailsService users, PersistentTokenRepository tokens, SessionRegistry sessions, boolean secureCookie) {
        super(PROVIDER_KEY, users, tokens);
        this.sessions = sessions;
        this.secureCookie = secureCookie;
        setCookieName("bookstore-remember");
        setParameter("remember-me");
        setTokenValiditySeconds(LIFETIME_SECONDS);
    }

    @Override
    protected Authentication createSuccessfulAuthentication(HttpServletRequest request, UserDetails user) {
        var authentication = super.createSuccessfulAuthentication(request, user);
        // RememberMeAuthenticationFilter saves the context before SessionManagementFilter
        // can register it. Register restored sessions explicitly for password-reset revocation.
        new ChangeSessionIdAuthenticationStrategy().onAuthentication(authentication, request, null);
        new RegisterSessionAuthenticationStrategy(sessions).onAuthentication(authentication, request, null);
        return authentication;
    }

    @Override
    protected void setCookie(String[] tokens, int maxAge, HttpServletRequest request, HttpServletResponse response) {
        String path = request.getContextPath().isEmpty() ? "/" : request.getContextPath();
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from("bookstore-remember", encodeCookie(tokens))
            .httpOnly(true).secure(secureCookie || request.isSecure()).sameSite("Lax")
            .path(path).maxAge(maxAge).build().toString());
    }
}
