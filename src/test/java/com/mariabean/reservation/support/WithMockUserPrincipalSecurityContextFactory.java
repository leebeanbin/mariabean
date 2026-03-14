package com.mariabean.reservation.support;

import com.mariabean.reservation.auth.domain.UserPrincipal;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithSecurityContextFactory;

import java.util.Collections;

public class WithMockUserPrincipalSecurityContextFactory
                implements WithSecurityContextFactory<WithMockUserPrincipal> {

        @Override
        public SecurityContext createSecurityContext(WithMockUserPrincipal annotation) {
                UserPrincipal principal = new UserPrincipal(
                                annotation.memberId(),
                                annotation.email(),
                                Collections.singleton(new SimpleGrantedAuthority(annotation.role())));
                Authentication auth = new UsernamePasswordAuthenticationToken(
                                principal, null, principal.getAuthorities());
                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(auth);
                return context;
        }
}
