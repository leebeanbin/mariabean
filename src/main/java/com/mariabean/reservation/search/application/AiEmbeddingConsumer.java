package com.mariabean.reservation.search.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mariabean.reservation.search.application.dto.FacilityEmbeddingPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiEmbeddingConsumer {

    private final ObjectMapper objectMapper;
    private final EmbeddingModel embeddingModel;
    private final FacilityEmbeddingBatchService embeddingBatchService;

    @RetryableTopic(attempts = "3", backoff = @Backoff(delay = 2000, multiplier = 2.0))
    @KafkaListener(topics = "ai-embedding-tasks", groupId = "ai-embedding-group")
    public void handleEmbeddingTask(String payload) {
        try {
            FacilityEmbeddingPayload task = objectMapper.readValue(payload, FacilityEmbeddingPayload.class);
            float[] embedding = embeddingModel.embed(task.getText());
            embeddingBatchService.updateEmbedding(task.getFacilityId(), embedding);
            log.debug("[EmbeddingConsumer] 처리 완료: {}", task.getFacilityId());
        } catch (Exception e) {
            log.error("[EmbeddingConsumer] 처리 실패: {}", e.getMessage(), e);
            throw new RuntimeException("Embedding task failed", e);
        }
    }
}
