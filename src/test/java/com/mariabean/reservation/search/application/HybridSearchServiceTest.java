package com.mariabean.reservation.search.application;

import com.mariabean.reservation.search.infrastructure.persistence.FacilitySearchDocument;
import com.mariabean.reservation.search.infrastructure.persistence.FacilitySearchRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Query;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class HybridSearchServiceTest {

    @InjectMocks
    private HybridSearchService hybridSearchService;

    @Mock
    private EmbeddingModel embeddingModel;

    @Mock
    private ElasticsearchOperations elasticsearchOperations;

    @Mock
    private FacilitySearchRepository facilitySearchRepository;

    private FacilitySearchDocument doc(String id, String name) {
        return FacilitySearchDocument.builder()
                .id(id)
                .name(name)
                .category("HOSPITAL")
                .address("서울 강남구")
                .build();
    }

    @SuppressWarnings("unchecked")
    private SearchHits<FacilitySearchDocument> mockHits(List<FacilitySearchDocument> docs) {
        SearchHits<FacilitySearchDocument> hits = mock(SearchHits.class);
        List<SearchHit<FacilitySearchDocument>> hitList = docs.stream()
                .map(d -> {
                    SearchHit<FacilitySearchDocument> hit = mock(SearchHit.class);
                    given(hit.getContent()).willReturn(d);
                    return hit;
                })
                .toList();
        given(hits.getSearchHits()).willReturn(hitList);
        return hits;
    }

    @Test
    @DisplayName("BM25 검색이 2개 결과를 반환하면 RRF 융합 후 결과 목록을 반환한다")
    void search_bm25Returns2Results_fused() {
        // given
        FacilitySearchDocument doc1 = doc("f-1", "강남 정형외과");
        FacilitySearchDocument doc2 = doc("f-2", "강남 내과");

        given(elasticsearchOperations.search(any(Query.class), eq(FacilitySearchDocument.class)))
                .willReturn(mockHits(List.of(doc1, doc2)));
        given(embeddingModel.embed(anyString())).willReturn(new float[]{0.1f, 0.2f});

        // when
        List<FacilitySearchDocument> results = hybridSearchService.search(
                List.of("강남", "정형외과"), 37.5, 127.0, 5.0);

        // then
        assertThat(results).hasSize(2);
        assertThat(results).extracting(FacilitySearchDocument::getId)
                .containsExactlyInAnyOrder("f-1", "f-2");
    }

    @Test
    @DisplayName("Elasticsearch가 예외를 던지면 빈 목록을 반환한다")
    void search_esThrows_returnsEmptyList() {
        // given
        given(elasticsearchOperations.search(any(Query.class), eq(FacilitySearchDocument.class)))
                .willThrow(new RuntimeException("ES 연결 실패"));
        given(embeddingModel.embed(anyString())).willReturn(new float[]{});

        // when
        List<FacilitySearchDocument> results = hybridSearchService.search(
                List.of("내과"), 37.5, 127.0, 5.0);

        // then
        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("embeddingModel.embed()가 실패해도 BM25 결과는 정상 반환된다")
    void search_embeddingFails_continuesBm25Only() {
        // given
        FacilitySearchDocument doc = doc("f-3", "종로 안과");

        given(elasticsearchOperations.search(any(Query.class), eq(FacilitySearchDocument.class)))
                .willReturn(mockHits(List.of(doc)));
        given(embeddingModel.embed(anyString())).willThrow(new RuntimeException("Ollama 미응답"));

        // when
        List<FacilitySearchDocument> results = hybridSearchService.search(
                List.of("안과"), 37.5, 127.0, 5.0);

        // then
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getId()).isEqualTo("f-3");
    }

    @Test
    @DisplayName("키워드 목록이 비어 있으면 빈 결과를 우아하게 반환한다")
    void search_emptyKeywords_returnsEmpty() {
        // given
        given(elasticsearchOperations.search(any(Query.class), eq(FacilitySearchDocument.class)))
                .willReturn(mockHits(List.of()));
        given(embeddingModel.embed(anyString())).willReturn(new float[]{});

        // when
        List<FacilitySearchDocument> results = hybridSearchService.search(
                List.of(), 37.5, 127.0, 5.0);

        // then
        assertThat(results).isEmpty();
    }

}
