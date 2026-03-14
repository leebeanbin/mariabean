package com.mariabean.reservation.event.outbox.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mariabean.reservation.event.outbox.domain.OutboxEvent;
import com.mariabean.reservation.event.outbox.domain.OutboxEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OutboxEventPublisherSchedulerTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;
    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;
    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private OutboxEventPublisherScheduler scheduler;

    @Test
    @DisplayName("RESERVATION aggregate 이벤트는 reservation-events 토픽으로 발행된다")
    void publishPendingEvents_reservationTopic() throws Exception {
        OutboxEvent event = OutboxEvent.builder()
                .aggregateType("RESERVATION")
                .aggregateId("99")
                .eventType("ReservationCancelledEvent")
                .payload("{\"eventType\":\"CANCELLED\"}")
                .build();
        given(outboxEventRepository.findByStatusOrderByCreatedAtAsc(OutboxEvent.OutboxStatus.PENDING))
                .willReturn(List.of(event));
        given(objectMapper.readTree(anyString())).willReturn(new ObjectMapper().readTree("{\"ok\":true}"));
        given(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .willReturn(CompletableFuture.completedFuture(null));

        scheduler.publishPendingEvents();

        verify(kafkaTemplate).send("reservation-events", "99", "{\"eventType\":\"CANCELLED\"}");
        assertThat(event.getStatus()).isEqualTo(OutboxEvent.OutboxStatus.PUBLISHED);
    }

    @Test
    @DisplayName("발행 실패 시 OutboxEvent 상태를 FAILED로 변경한다")
    void publishPendingEvents_markFailed() throws Exception {
        OutboxEvent event = OutboxEvent.builder()
                .aggregateType("PAYMENT")
                .aggregateId("100")
                .eventType("PaymentApprovedEvent")
                .payload("{\"paymentId\":100}")
                .build();
        CompletableFuture<SendResult<String, String>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("kafka down"));

        given(outboxEventRepository.findByStatusOrderByCreatedAtAsc(OutboxEvent.OutboxStatus.PENDING))
                .willReturn(List.of(event));
        given(objectMapper.readTree(anyString())).willReturn(new ObjectMapper().readTree("{\"ok\":true}"));
        given(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .willReturn(failedFuture);

        scheduler.publishPendingEvents();

        assertThat(event.getStatus()).isEqualTo(OutboxEvent.OutboxStatus.FAILED);
    }
}
