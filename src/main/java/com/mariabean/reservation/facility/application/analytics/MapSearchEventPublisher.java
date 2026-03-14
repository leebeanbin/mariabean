package com.mariabean.reservation.facility.application.analytics;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class MapSearchEventPublisher {

    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public MapSearchEventPublisher(
            ObjectMapper objectMapper,
            @Autowired(required = false) KafkaTemplate<String, String> kafkaTemplate
    ) {
        this.objectMapper = objectMapper;
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(MapSearchEvent event) {
        if (kafkaTemplate == null || event == null) return;
        try {
            String payload = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(MapSearchEvent.TOPIC, event.getQuery(), payload);
        } catch (Exception e) {
            log.warn("[MapSearchEvent] publish skipped: {}", e.getMessage());
        }
    }

    public boolean isEnabled() {
        return kafkaTemplate != null;
    }
}

