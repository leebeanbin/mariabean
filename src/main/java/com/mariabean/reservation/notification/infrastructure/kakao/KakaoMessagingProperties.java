package com.mariabean.reservation.notification.infrastructure.kakao;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "kakao.messaging")
public class KakaoMessagingProperties {
    private final Channel alimtalk = new Channel();
    private final Channel talkMessage = new Channel();

    @Getter
    @Setter
    public static class Channel {
        private Provider provider = Provider.GENERIC;
        private boolean enabled = false;
        private String endpoint = "";
        private String token = "";
        private String apiKey = "";
        private String senderKey = "";
        private String templateCode = "";
    }

    public enum Provider {
        GENERIC,
        NCP_SENS
    }
}
