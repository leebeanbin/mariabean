package com.mariabean.reservation.notification.infrastructure.kakao;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mariabean.reservation.notification.application.NotificationRecipient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KakaoMessageTemplateFactoryTest {

    private final KakaoMessageTemplateFactory factory = new KakaoMessageTemplateFactory();

    @Test
    @DisplayName("결제 알림 텍스트/변수와 알림톡 요청 DTO를 생성한다")
    void paymentTemplateAndAlimTalkPayload() {
        NotificationRecipient recipient = new NotificationRecipient(1L, "kim", "01012341234", "kakao_1");
        String text = factory.paymentConfirmationText(11L, BigDecimal.valueOf(12000), "KAKAO_PAY");
        Map<String, String> vars = factory.paymentConfirmationVariables(11L, BigDecimal.valueOf(12000), "KAKAO_PAY");

        KakaoPayloads.AlimTalkRequest req = factory.alimTalkRequest(
                recipient, "sender-key", "tpl-pay", text, vars);

        assertThat(req.senderKey()).isEqualTo("sender-key");
        assertThat(req.templateCode()).isEqualTo("tpl-pay");
        assertThat(req.messages()).hasSize(1);
        assertThat(req.messages().get(0).to()).isEqualTo("01012341234");
        assertThat(req.messages().get(0).templateVariables().get("reservationId")).isEqualTo("11");
        assertThat(req.messages().get(0).content()).contains("결제가 완료되었습니다");
    }

    @Test
    @DisplayName("예약 취소 텍스트와 talk_message 요청 DTO를 생성한다")
    void cancellationTemplateAndTalkPayload() {
        NotificationRecipient recipient = new NotificationRecipient(2L, "lee", "01056785678", "kakao_uuid_2");
        String text = factory.reservationCancellationText(55L);

        KakaoPayloads.TalkMessageRequest req = factory.talkMessageRequest(recipient, new ObjectMapper(), text);

        assertThat(req.receiverUuids()).isEqualTo("[\"kakao_uuid_2\"]");
        assertThat(req.templateObject()).contains("\"object_type\":\"text\"");
        assertThat(req.templateObject()).contains("예약이 취소되었습니다");
    }

    @Test
    @DisplayName("NCP SENS 알림톡 요청 DTO를 생성한다")
    void ncpAlimTalkPayload() {
        NotificationRecipient recipient = new NotificationRecipient(3L, "park", "01000001111", "kakao_3");
        KakaoPayloads.NcpAlimTalkRequest req = factory.ncpAlimTalkRequest(
                recipient, "@mariabean", "TPL001", "테스트 메시지");

        assertThat(req.plusFriendId()).isEqualTo("@mariabean");
        assertThat(req.templateCode()).isEqualTo("TPL001");
        assertThat(req.messages()).hasSize(1);
        assertThat(req.messages().get(0).to()).isEqualTo("01000001111");
        assertThat(req.messages().get(0).content()).isEqualTo("테스트 메시지");
    }
}
