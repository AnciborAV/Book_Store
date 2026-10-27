package ru.aav.auth;

/** Public account data. Password hashes and image bytes never enter this DTO. */
public record Profile(String email, String displayName, boolean hasAvatar) {
    public static Profile from(StoreUser user) {
        return new Profile(
            user.getEmail(),
            user.getDisplayName(),
            user.getAvatar() != null
        );
    }
}
