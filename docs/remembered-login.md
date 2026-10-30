# Remembered browser login

The sign-in form defaults to **Запомнить меня на 30 дней**. Uncheck it to keep a session-only login, for example on a shared device. After registration, the same choice applies to the automatic sign-in.

Spring Security's persistent remember-me flow stores opaque token series in `persistent_logins`. The browser cookie contains no password or email. Automatic login rotates the token. Database storage allows remembered access to survive an application restart, provided the deployment uses the same persistent database. The 30-day validity is renewed when automatic login succeeds.

The `bookstore-remember` cookie is HttpOnly, SameSite=Lax and Secure by default. This works for the public HTTPS site, including a TLS-terminating deployment proxy. For local HTTP development only, start with `--bookstore.remember-me.secure=false`; requests that are themselves HTTPS still receive Secure cookies.

Logout clears the browser cookie and revokes remembered tokens for the account. Password recovery deletes remembered tokens in the same transaction as the password update, then expires registered sessions. Sessions created by automatic login are explicitly registered so they are included in this revocation.

Normal CSRF protection remains enabled. Passwords are never stored in localStorage, sessionStorage or cookies. Existing users sign in once with the new option before the browser can remember them. Private browsing, a different browser, cleared site data or device policies can still require another sign-in.

Validation: RememberedLoginTest covers sessionless automatic login, cookie attributes, rotation, opt-out, invalid credentials, logout, expiry, malformed cookies, and password-reset revocation of cookies and restored sessions. PasswordRecoveryTest and SelectionTest check existing recovery and account-selection behavior.
