package com.mariabean.reservation.auth.infrastructure.oauth2;

import com.mariabean.reservation.member.domain.MemberRepository;
import com.mariabean.reservation.member.domain.Member;
import com.mariabean.reservation.member.domain.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final MemberRepository memberRepository;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate = new DefaultOAuth2UserService();
        OAuth2User oAuth2User = delegate.loadUser(userRequest);

        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        String userNameAttributeName = userRequest.getClientRegistration()
                .getProviderDetails().getUserInfoEndpoint().getUserNameAttributeName();

        OAuth2UserInfo userInfo = OAuth2UserInfo.of(registrationId, userNameAttributeName, oAuth2User.getAttributes());

        Member member = saveOrUpdate(userInfo);

        // memberId를 attributes에 추가하여 SuccessHandler에서 JWT 생성 시 활용
        java.util.Map<String, Object> attributes = new java.util.HashMap<>(userInfo.getAttributes());
        attributes.put("memberId", member.getId());

        return new DefaultOAuth2User(
                Collections.singleton(new SimpleGrantedAuthority(member.getRole().getKey())),
                attributes,
                userInfo.getNameAttributeKey());
    }

    private Member saveOrUpdate(OAuth2UserInfo userInfo) {
        Member member = memberRepository.findByEmail(userInfo.getEmail())
                .map(entity -> entity.update(userInfo.getName()))
                .orElse(Member.builder()
                        .name(userInfo.getName())
                        .email(userInfo.getEmail())
                        .role(Role.USER)
                        .provider(userInfo.getProvider())
                        .providerId(userInfo.getProviderId())
                        .build());
        return memberRepository.save(member);
    }
}
