package com.mariabean.reservation.search.application;

import com.mariabean.reservation.facility.domain.Facility;
import com.mariabean.reservation.facility.domain.FacilityRepository;
import com.mariabean.reservation.search.infrastructure.persistence.FacilitySearchDocument;
import com.mariabean.reservation.search.infrastructure.persistence.FacilitySearchRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FacilityEmbeddingBatchServiceTest {

    @InjectMocks
    private FacilityEmbeddingBatchService batchService;

    @Mock
    private FacilityRepository facilityRepository;

    @Mock
    private FacilitySearchRepository facilitySearchRepository;

    @Mock
    private EmbeddingModel embeddingModel;

    // ──────────────────────────────────────────────────────────────────────
    // helpers
    // ──────────────────────────────────────────────────────────────────────

    private Facility facility(String id) {
        return Facility.builder()
                .id(id)
                .name("테스트 병원")
                .category("HOSPITAL")
                .address("서울 강남구")
                .description("설명")
                .latitude(37.5)
                .longitude(127.0)
                .build();
    }

    private Page<Facility> singlePage(Facility f) {
        return new PageImpl<>(List.of(f), PageRequest.of(0, 50), 1);
    }

    // ──────────────────────────────────────────────────────────────────────
    // reindexAll()
    // ──────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("단일 페이지 처리 시 임베딩이 생성되고 ES에 저장된다")
    void reindexAll_singlePage_embeddingGeneratedAndSaved() {
        // given
        Facility f = facility("f-1");
        float[] fakeVec = new float[]{0.1f, 0.2f, 0.3f};

        given(facilityRepository.findAllActive(PageRequest.of(0, 50))).willReturn(singlePage(f));
        given(embeddingModel.embed(any(String.class))).willReturn(fakeVec);

        // when
        int total = batchService.reindexAll();

        // then
        assertThat(total).isEqualTo(1);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<FacilitySearchDocument>> captor =
                (ArgumentCaptor<List<FacilitySearchDocument>>) (ArgumentCaptor<?>) ArgumentCaptor.forClass(List.class);
        verify(facilitySearchRepository).saveAll(captor.capture());
        List<FacilitySearchDocument> saved = captor.getValue();
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getEmbedding()).isEqualTo(fakeVec);
    }

    @Test
    @DisplayName("임베딩 생성에 실패하면 빈 벡터로 문서가 저장된다")
    void reindexAll_embeddingFails_continuesWithEmptyVector() {
        // given
        Facility f = facility("f-2");

        given(facilityRepository.findAllActive(PageRequest.of(0, 50))).willReturn(singlePage(f));
        given(embeddingModel.embed(any(String.class))).willThrow(new RuntimeException("Ollama 연결 실패"));

        // when
        int total = batchService.reindexAll();

        // then – document is still saved with empty embedding (graceful fallback)
        assertThat(total).isEqualTo(1);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<FacilitySearchDocument>> captor =
                (ArgumentCaptor<List<FacilitySearchDocument>>) (ArgumentCaptor<?>) ArgumentCaptor.forClass(List.class);
        verify(facilitySearchRepository).saveAll(captor.capture());
        assertThat(captor.getValue().get(0).getEmbedding()).isEmpty();
    }

    @Test
    @DisplayName("처리할 시설이 없으면 reindexAll은 0을 반환한다")
    void reindexAll_emptyFacilityList_returns0() {
        // given
        given(facilityRepository.findAllActive(any(Pageable.class)))
                .willReturn(Page.empty());

        // when
        int total = batchService.reindexAll();

        // then
        assertThat(total).isEqualTo(0);
        verify(facilitySearchRepository, never()).saveAll(anyIterable());
    }

    // ──────────────────────────────────────────────────────────────────────
    // updateEmbedding()
    // ──────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("문서가 존재하면 임베딩을 업데이트하고 저장한다")
    void updateEmbedding_docExists_updatesAndSaves() {
        // given
        String facilityId = "f-update";
        float[] newVec = new float[]{0.5f, 0.6f};
        FacilitySearchDocument doc = FacilitySearchDocument.builder()
                .id(facilityId).name("업데이트 병원").build();

        given(facilitySearchRepository.findById(facilityId)).willReturn(Optional.of(doc));

        // when
        batchService.updateEmbedding(facilityId, newVec);

        // then
        verify(facilitySearchRepository).save(doc);
        assertThat(doc.getEmbedding()).isEqualTo(newVec);
    }

    @Test
    @DisplayName("문서가 없으면 updateEmbedding은 아무 작업도 수행하지 않는다")
    void updateEmbedding_docNotFound_noOp() {
        // given
        given(facilitySearchRepository.findById("f-missing")).willReturn(Optional.empty());

        // when
        batchService.updateEmbedding("f-missing", new float[]{0.1f});

        // then
        verify(facilitySearchRepository, never()).save(any());
    }
}
