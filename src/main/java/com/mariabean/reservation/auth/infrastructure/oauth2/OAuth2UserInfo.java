package com.mariabean.reservation.auth.infrastructure.oauth2;

import lombok.Builder;
import lombok.Getter;

import java.util.Map;

@Getter
public class OAuth2UserInfo {

    private final Map<String, Object> attributes;
    private final String nameAttributeKey;
    private final String name;
    private final String email;
    private final String provider;
    private final String providerId;

    @Builder
    public OAuth2UserInfo(Map<String, Object> attributes, String nameAttributeKey,
            String name, String email, String provider, String providerId) {
        this.attributes = attributes;
        this.nameAttributeKey = nameAttributeKey;
        this.name = name;
        this.email = email;
        this.provider = provider;
        this.providerId = providerId;
    }

    public static OAuth2UserInfo of(String registrationId, String userNameAttributeName,
            Map<String, Object> attributes) {
        if ("kakao".equals(registrationId)) {
            return ofKakao("id", attributes);
        }
        return ofGoogle(userNameAttributeName, attributes);
    }

    private static OAuth2UserInfo ofGoogle(String userNameAttributeName, Map<String, Object> attributes) {
        return OAuth2UserInfo.builder()
                .name((String) attributes.get("name"))
                .email((String) attributes.get("email"))
                .provider("google")
                .providerId(String.valueOf(attributes.get(userNameAttributeName)))
                .attributes(attributes)
                .nameAttributeKey(userNameAttributeName)
                .build();
    }

    private static OAuth2UserInfo ofKakao(String userNameAttributeName, Map<String, Object> attributes) {
        Map<String, Object> kakaoAccount = getMapAttribute(attributes, "kakao_account");
        Map<String, Object> kakaoProfile = getMapAttribute(kakaoAccount, "profile");
        String providerId = String.valueOf(attributes.get(userNameAttributeName));
        String email = (String) kakaoAccount.get("email");
        String nickname = (String) kakaoProfile.get("nickname");

        // Kakao can return null email when account_email is not consented or unavailable.
        // We generate a stable pseudo-email to keep member identity and refresh-token flow working.
        if (email == null || email.isBlank()) {
            email = "kakao_" + providerId + "@noemail.local";
        }
        if (nickname == null || nickname.isBlank()) {
            nickname = "KakaoUser" + providerId;
        }

        return OAuth2UserInfo.builder()
                .name(nickname)
                .email(email)
                .provider("kakao")
                .providerId(providerId)
                .attributes(attributes)
                .nameAttributeKey(userNameAttributeName)
                .build();
    }

    private static Map<String, Object> getMapAttribute(Map<String, Object> attributes, String key) {
        Object value = attributes.get(key);
        if (value instanceof Map<?, ?> rawMap) {
            Map<String, Object> result = new java.util.HashMap<>();
            rawMap.forEach((k, v) -> {
                if (k instanceof String strKey) {
                    result.put(strKey, v);
                }
            });
            return result;
        }
        return java.util.Collections.emptyMap();
    }
}
