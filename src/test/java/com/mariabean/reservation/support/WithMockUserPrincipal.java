package com.mariabean.reservation.support;

import org.springframework.security.test.context.support.WithSecurityContext;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Test annotation that sets up a {@link com.mariabean.reservation.auth.infrastructure.security.UserPrincipal}
 * in the SecurityContext, compatible with {@link com.mariabean.reservation.auth.application.SecurityUtils}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@WithSecurityContext(factory = WithMockUserPrincipalSecurityContextFactory.class)
public @interface WithMockUserPrincipal {
    long memberId() default 1L;
    String email() default "test@test.com";
    String role() default "ROLE_USER";
}
