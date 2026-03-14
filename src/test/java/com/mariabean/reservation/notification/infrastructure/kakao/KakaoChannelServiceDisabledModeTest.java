package com.mariabean.reservation.notification.infrastructure.kakao;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mariabean.reservation.notification.application.NotificationRecipient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class KakaoChannelServiceDisabledModeTest {

    @Test
    @DisplayName("알림톡 채널 비활성화 시 false를 반환한다")
    void alimTalk_disabled_returnsFalse() {
        KakaoMessagingProperties properties = new KakaoMessagingProperties();
        KakaoAlimTalkNotificationService service = new KakaoAlimTalkNotificationService(
                properties, new ObjectMapper(), new KakaoMessageTemplateFactory());
        NotificationRecipient recipient = new NotificationRecipient(1L, "kim", "01012341234", "kakao_1");

        boolean sent = service.sendPaymentConfirmation(recipient, 1L, BigDecimal.TEN, "KAKAO_PAY");

        assertThat(sent).isFalse();
    }

    @Test
    @DisplayName("talk_message 채널 비활성화 시 false를 반환한다")
    void talkMessage_disabled_returnsFalse() {
        KakaoMessagingProperties properties = new KakaoMessagingProperties();
        KakaoTalkMessageNotificationService service = new KakaoTalkMessageNotificationService(
                properties, new ObjectMapper(), new KakaoMessageTemplateFactory());
        NotificationRecipient recipient = new NotificationRecipient(2L, "lee", "01056785678", "kakao_2");

        boolean sent = service.sendReservationCancellation(recipient, 2L);

        assertThat(sent).isFalse();
    }
}
