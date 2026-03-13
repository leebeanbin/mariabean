package com.mariabean.reservation.auth.application;

import com.mariabean.reservation.auth.domain.UserPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class SecurityUtils {

    private SecurityUtils() {
    }

    public static Long getCurrentMemberId() {
        UserPrincipal principal = getPrincipal();
        return principal.getMemberId();
    }

    public static String getCurrentUserEmail() {
        UserPrincipal principal = getPrincipal();
        return principal.getEmail();
    }

    private static UserPrincipal getPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal)) {
            throw new IllegalStateException("No authenticated user found in SecurityContext");
        }
        return (UserPrincipal) authentication.getPrincipal();
    }
}
