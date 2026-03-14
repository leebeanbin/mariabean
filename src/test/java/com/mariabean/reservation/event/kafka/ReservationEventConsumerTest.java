package com.mariabean.reservation.event.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mariabean.reservation.notification.application.NotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ReservationEventConsumerTest {

    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private ReservationEventConsumer consumer;

    @Test
    @DisplayName("CANCELLED 이벤트는 예약 취소 알림을 호출한다")
    void handleReservationEvent_cancelled() throws Exception {
        ReservationEvent event = ReservationEvent.builder()
                .reservationId(100L)
                .memberId(1L)
                .eventType(ReservationEvent.EVENT_CANCELLED)
                .build();
        given(objectMapper.readValue("msg", ReservationEvent.class)).willReturn(event);

        consumer.handleReservationEvent("msg");

        verify(notificationService).sendReservationCancellation(1L, 100L);
    }

    @Test
    @DisplayName("CANCELLED 외 이벤트는 알림을 호출하지 않는다")
    void handleReservationEvent_notCancelled() throws Exception {
        ReservationEvent event = ReservationEvent.builder()
                .reservationId(101L)
                .memberId(2L)
                .eventType(ReservationEvent.EVENT_CREATED)
                .build();
        given(objectMapper.readValue("msg2", ReservationEvent.class)).willReturn(event);

        consumer.handleReservationEvent("msg2");

        verifyNoInteractions(notificationService);
    }
}
