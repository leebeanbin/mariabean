package com.mariabean.reservation.search.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.EmbeddingModel;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AiEmbeddingConsumerTest {

    // Use a real ObjectMapper for payload parsing — inject manually below.
    // Mockito @InjectMocks is used for EmbeddingModel + FacilityEmbeddingBatchService mocks.

    @Mock
    private EmbeddingModel embeddingModel;

    @Mock
    private FacilityEmbeddingBatchService embeddingBatchService;

    // ──────────────────────────────────────────────────────────────────────
    // Tests using real ObjectMapper
    // ──────────────────────────────────────────────────────────────────────

    private AiEmbeddingConsumer consumerWithRealMapper() {
        return new AiEmbeddingConsumer(new ObjectMapper(), embeddingModel, embeddingBatchService);
    }

    @Test
    @DisplayName("유효한 payload가 전달되면 임베딩을 생성하고 ES 업데이트를 호출한다")
    void handleEmbeddingTask_validPayload_generatesAndUpdatesEmbedding() {
        // given
        String payload = """
                {"facilityId":"f-123","text":"강남 정형외과 척추 관절"}
                """;
        float[] embedding = new float[]{0.1f, 0.2f, 0.3f};
        given(embeddingModel.embed("강남 정형외과 척추 관절")).willReturn(embedding);

        AiEmbeddingConsumer consumer = consumerWithRealMapper();

        // when
        consumer.handleEmbeddingTask(payload);

        // then
        verify(embeddingModel).embed("강남 정형외과 척추 관절");
        verify(embeddingBatchService).updateEmbedding("f-123", embedding);
    }

    @Test
    @DisplayName("JSON이 유효하지 않으면 RuntimeException이 발생해 Kafka가 재시도할 수 있다")
    void handleEmbeddingTask_invalidJson_throwsRuntimeException() {
        // given
        String badPayload = "not-json-{{{{";
        AiEmbeddingConsumer consumer = consumerWithRealMapper();

        // when / then
        assertThatThrownBy(() -> consumer.handleEmbeddingTask(badPayload))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Embedding task failed");
    }
}
