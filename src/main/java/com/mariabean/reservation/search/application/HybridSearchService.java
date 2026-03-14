package com.mariabean.reservation.search.application;

import com.mariabean.reservation.search.infrastructure.persistence.FacilitySearchDocument;
import com.mariabean.reservation.search.infrastructure.persistence.FacilitySearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class HybridSearchService {

    private final EmbeddingModel embeddingModel;
    private final ElasticsearchOperations elasticsearchOperations;
    private final FacilitySearchRepository facilitySearchRepository;

    /**
     * 하이브리드 검색: BM25 키워드 검색 + kNN 벡터 검색 → Reciprocal Rank Fusion
     */
    public List<FacilitySearchDocument> search(List<String> keywords, double lat, double lng, double radiusKm) {
        String queryText = String.join(" ", keywords);

        // 1. BM25 키워드 검색
        List<FacilitySearchDocument> bm25Results = searchByKeyword(queryText, lat, lng);

        // 2. kNN 벡터 검색 (임베딩 가능 시)
        List<FacilitySearchDocument> knnResults = searchByVector(queryText, lat, lng);

        // 3. Reciprocal Rank Fusion
        return fuseResults(bm25Results, knnResults);
    }

    private List<FacilitySearchDocument> searchByKeyword(String query, double lat, double lng) {
        try {
            Criteria criteria = new Criteria("name").matches(query)
                    .or(new Criteria("address").matches(query))
                    .or(new Criteria("category").matches(query));
            Query q = new CriteriaQuery(criteria);
            SearchHits<FacilitySearchDocument> hits = elasticsearchOperations.search(q, FacilitySearchDocument.class);
            return hits.getSearchHits().stream()
                    .map(SearchHit::getContent)
                    .limit(20)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("[HybridSearch] BM25 검색 실패: {}", e.getMessage());
            return List.of();
        }
    }

    private List<FacilitySearchDocument> searchByVector(String query, double lat, double lng) {
        try {
            float[] vector = embeddingModel.embed(query);
            // kNN 쿼리는 NativeQuery로 구성하되, Spring Data ES 버전 호환성 이슈가 있을 수 있음
            // fallback: BM25만 사용
            // TODO: ES 8.x kNN query 직접 구성
            return List.of();
        } catch (Exception e) {
            log.warn("[HybridSearch] kNN 검색 실패 (Ollama 미응답 등): {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Reciprocal Rank Fusion: 두 랭킹 리스트를 병합
     */
    private List<FacilitySearchDocument> fuseResults(
            List<FacilitySearchDocument> bm25,
            List<FacilitySearchDocument> knn) {

        Map<String, Double> scores = new LinkedHashMap<>();
        final int K = 60;

        for (int i = 0; i < bm25.size(); i++) {
            String id = bm25.get(i).getId();
            scores.merge(id, 1.0 / (K + i + 1), Double::sum);
        }
        for (int i = 0; i < knn.size(); i++) {
            String id = knn.get(i).getId();
            scores.merge(id, 1.0 / (K + i + 1), Double::sum);
        }

        // 점수 기준 정렬 후 FacilitySearchDocument 재조합
        Map<String, FacilitySearchDocument> allDocs = new HashMap<>();
        bm25.forEach(d -> allDocs.put(d.getId(), d));
        knn.forEach(d -> allDocs.put(d.getId(), d));

        return scores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(20)
                .map(e -> allDocs.get(e.getKey()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
}
