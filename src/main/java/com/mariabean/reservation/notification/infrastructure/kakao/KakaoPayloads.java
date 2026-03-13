package com.mariabean.reservation.notification.infrastructure.kakao;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public final class KakaoPayloads {

    private KakaoPayloads() {
    }

    public record AlimTalkRequest(
            String senderKey,
            String templateCode,
            List<AlimTalkMessage> messages) {
    }

    public record AlimTalkMessage(
            String to,
            String content,
            Map<String, String> templateVariables) {
    }

    public record NcpAlimTalkRequest(
            String plusFriendId,
            String templateCode,
            List<NcpAlimTalkMessage> messages) {
    }

    public record NcpAlimTalkMessage(
            String to,
            String content) {
    }

    public record TalkMessageRequest(
            @JsonProperty("receiver_uuids") String receiverUuids,
            @JsonProperty("template_object") String templateObject) {
    }

    public record TalkTemplateObject(
            @JsonProperty("object_type") String objectType,
            String text) {
    }
}
