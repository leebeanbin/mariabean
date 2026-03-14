package com.mariabean.reservation.search.application;

import com.mariabean.reservation.facility.domain.Facility;
import com.mariabean.reservation.facility.domain.FacilityRepository;
import com.mariabean.reservation.search.infrastructure.persistence.FacilitySearchDocument;
import com.mariabean.reservation.search.infrastructure.persistence.FacilitySearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FacilityEmbeddingBatchService {

    private final FacilityRepository facilityRepository;
    private final FacilitySearchRepository facilitySearchRepository;
    private final EmbeddingModel embeddingModel;

    private static final int BATCH_SIZE = 50;

    public int reindexAll() {
        int page = 0;
        int total = 0;

        while (true) {
            Page<Facility> facilities = facilityRepository.findAllActive(PageRequest.of(page, BATCH_SIZE));
            if (facilities.isEmpty()) break;

            List<FacilitySearchDocument> docs = facilities.getContent().stream()
                    .map(this::toDocumentWithEmbedding)
                    .collect(Collectors.toList());

            facilitySearchRepository.saveAll(docs);
            total += docs.size();
            log.info("[Embedding Batch] page={}, saved={}, total={}", page, docs.size(), total);

            if (facilities.isLast()) break;
            page++;
        }

        log.info("[Embedding Batch] 완료. 총 {} 건 처리", total);
        return total;
    }

    public void updateEmbedding(String facilityId, float[] embedding) {
        facilitySearchRepository.findById(facilityId).ifPresent(doc -> {
            doc.setEmbedding(embedding);
            facilitySearchRepository.save(doc);
            log.debug("[Embedding] 업데이트: {}", facilityId);
        });
    }

    private FacilitySearchDocument toDocumentWithEmbedding(Facility facility) {
        float[] embedding = generateEmbedding(facility);
        org.springframework.data.elasticsearch.core.geo.GeoPoint location =
                (facility.getLatitude() != null && facility.getLongitude() != null)
                        ? new org.springframework.data.elasticsearch.core.geo.GeoPoint(
                                facility.getLatitude(), facility.getLongitude())
                        : null;

        return FacilitySearchDocument.builder()
                .id(facility.getId())
                .name(facility.getName())
                .category(facility.getCategory())
                .address(facility.getAddress())
                .placeId(facility.getPlaceId())
                .location(location)
                .specialties(facility.getSpecialties())
                .embedding(embedding)
                .build();
    }

    private float[] generateEmbedding(Facility facility) {
        try {
            String text = String.join(" ",
                    nullSafe(facility.getName()),
                    nullSafe(facility.getCategory()),
                    nullSafe(facility.getDescription()),
                    String.join(" ", facility.getSpecialties()));
            return embeddingModel.embed(text);
        } catch (Exception e) {
            log.warn("[Embedding] 생성 실패 facility={}: {}", facility.getId(), e.getMessage());
            return new float[0];
        }
    }

    private String nullSafe(String s) {
        return s != null ? s : "";
    }
}
