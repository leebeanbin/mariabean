package com.mariabean.reservation.search.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mariabean.reservation.event.outbox.application.OutboxService;
import com.mariabean.reservation.facility.domain.Facility;
import com.mariabean.reservation.facility.domain.FacilityRepository;
import com.mariabean.reservation.search.infrastructure.persistence.FacilitySearchDocument;
import com.mariabean.reservation.search.infrastructure.persistence.FacilitySearchRepository;
import com.mariabean.reservation.search.infrastructure.persistence.ResourceItemSearchRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.document.Document;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ElasticsearchSyncServiceTest {

    @Mock private ResourceItemSearchRepository resourceItemSearchRepository;
    @Mock private FacilitySearchRepository facilitySearchRepository;
    @Mock private FacilityRepository facilityRepository;
    @Mock private ElasticsearchOperations elasticsearchOperations;
    @Mock private IndexOperations indexOperations;
    @Mock private EmbeddingModel embeddingModel;
    @Mock private OutboxService outboxService;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks
    private ElasticsearchSyncService syncService;

    private Facility sampleFacility(String id) {
        return Facility.builder()
                .id(id).name("테스트 시설").category("HOSPITAL")
                .address("서울 강남구").latitude(37.5).longitude(127.0)
                .build();
    }

    @Test
    @DisplayName("facilities 인덱스가 없으면 생성 후 active 시설을 백필한다")
    void ensureFacilitiesIndexAndBackfill_createsAndBackfills() {
        Facility facility = sampleFacility("f-1");
        float[] fakeVec = new float[]{0.1f, 0.2f, 0.3f};
        given(embeddingModel.embed(any(String.class))).willReturn(fakeVec);
        given(elasticsearchOperations.indexOps(FacilitySearchDocument.class)).willReturn(indexOperations);
        given(indexOperations.exists()).willReturn(false);
        given(indexOperations.create()).willReturn(true);
        given(indexOperations.createMapping(FacilitySearchDocument.class)).willReturn(Document.create());
        given(indexOperations.putMapping(any(Document.class))).willReturn(true);
        given(facilityRepository.findAllActive(PageRequest.of(0, 200)))
                .willReturn(new PageImpl<>(List.of(facility), PageRequest.of(0, 200), 1));

        syncService.ensureFacilitiesIndexAndBackfill();

        verify(indexOperations).create();
        verify(facilitySearchRepository).save(any(FacilitySearchDocument.class));
    }

    @Test
    @DisplayName("syncFacility: 임베딩 성공 시 ES에 저장하고 Outbox 미등록")
    void syncFacility_withEmbedding_savesWithoutOutbox() {
        Facility facility = sampleFacility("f-2");
        float[] vec = new float[768];
        given(embeddingModel.embed(any(String.class))).willReturn(vec);

        syncService.syncFacility(facility);

        verify(facilitySearchRepository).save(any(FacilitySearchDocument.class));
        verify(outboxService, never()).saveEvent(any(), any(), any(), any());
    }

    @Test
    @DisplayName("syncFacility: 임베딩 실패 시 Outbox에 AI_EMBEDDING 이벤트 등록")
    void syncFacility_embeddingFails_schedulesOutbox() {
        Facility facility = sampleFacility("f-3");
        given(embeddingModel.embed(any(String.class))).willThrow(new RuntimeException("Ollama 연결 실패"));

        syncService.syncFacility(facility);

        // ES 저장은 빈 임베딩으로 진행
        verify(facilitySearchRepository).save(any(FacilitySearchDocument.class));
        // Outbox 비동기 재시도 등록
        verify(outboxService).saveEvent(eq("AI_EMBEDDING"), eq("f-3"), any(), any());
    }

    @Test
    @DisplayName("deleteFacility: ES에서 해당 문서를 삭제한다")
    void deleteFacility_removesFromIndex() {
        syncService.deleteFacility("f-del");
        verify(facilitySearchRepository).deleteById("f-del");
    }
}
