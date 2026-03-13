package com.mariabean.reservation.auth.api;

import com.mariabean.reservation.member.domain.MemberRepository;
import com.mariabean.reservation.auth.domain.RefreshTokenRepository;
import com.mariabean.reservation.global.exception.BusinessException;
import com.mariabean.reservation.global.exception.ErrorCode;
import com.mariabean.reservation.global.response.CommonResponse;
import com.mariabean.reservation.auth.application.SecurityUtils;
import com.mariabean.reservation.member.domain.Member;
import com.mariabean.reservation.auth.domain.RefreshToken;
import com.mariabean.reservation.auth.application.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final JwtProvider jwtProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final MemberRepository memberRepository;

    @PostMapping("/refresh")
    public CommonResponse<Map<String, String>> refreshToken(@RequestParam("refreshToken") String refreshTokenString) {
        if (!jwtProvider.validateToken(refreshTokenString)) {
            throw new BusinessException(ErrorCode.TOKEN_EXPIRED);
        }

        String email = jwtProvider.getPayload(refreshTokenString);

        RefreshToken storedToken = refreshTokenRepository.findById(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));

        if (!storedToken.getToken().equals(refreshTokenString)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        Member member = memberRepository.getByEmail(email);

        String newAccessToken = jwtProvider.createAccessToken(email, member.getId(), member.getRole().getKey());
        return CommonResponse.success(Map.of("accessToken", newAccessToken));
    }

    @PostMapping("/logout")
    @PreAuthorize("isAuthenticated()")
    public CommonResponse<Void> logout() {
        String email = SecurityUtils.getCurrentUserEmail();
        refreshTokenRepository.deleteById(email);
        return CommonResponse.success();
    }
}
