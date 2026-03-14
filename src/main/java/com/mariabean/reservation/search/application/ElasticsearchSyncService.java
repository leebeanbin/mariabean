package com.mariabean.reservation.search.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mariabean.reservation.event.outbox.application.OutboxService;
import com.mariabean.reservation.facility.domain.Facility;
import com.mariabean.reservation.facility.domain.FacilityRepository;
import com.mariabean.reservation.facility.domain.ResourceItem;
import com.mariabean.reservation.search.application.dto.FacilityEmbeddingPayload;
import com.mariabean.reservation.search.infrastructure.persistence.FacilitySearchDocument;
import com.mariabean.reservation.search.infrastructure.persistence.FacilitySearchRepository;
import com.mariabean.reservation.search.infrastructure.persistence.ResourceItemSearchDocument;
import com.mariabean.reservation.search.infrastructure.persistence.ResourceItemSearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.geo.GeoPoint;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ElasticsearchSyncService {

    private final ResourceItemSearchRepository searchRepository;
    private final FacilitySearchRepository facilitySearchRepository;
    private final FacilityRepository facilityRepository;
    private final ElasticsearchOperations elasticsearchOperations;
    private final EmbeddingModel embeddingModel;
    private final OutboxService outboxService;
    private final ObjectMapper objectMapper;

    public void syncResourceItem(ResourceItem item) {
        try {
            ResourceItemSearchDocument document = ResourceItemSearchDocument.builder()
                    .id(item.getId())
                    .facilityId(item.getFacilityId())
                    .name(item.getName())
                    .resourceType(item.getResourceType())
                    .limitCapacity(item.getLimitCapacity())
                    .floor(item.getFloor())
                    .location(item.getLocation())
                    .customAttributes(item.getCustomAttributes())
                    .build();

            searchRepository.save(document);
            log.debug("Synced ResourceItem [{}] to Elasticsearch", document.getId());
        } catch (Exception e) {
            log.warn("[ES Sync] ResourceItem [{}] 동기화 스킵: {}", item.getId(), e.getMessage());
        }
    }

    public void deleteResourceItem(String id) {
        try {
            searchRepository.deleteById(id);
            log.debug("Deleted ResourceItem [{}] from Elasticsearch", id);
        } catch (Exception e) {
            log.warn("[ES Sync] ResourceItem [{}] 삭제 스킵: {}", id, e.getMessage());
        }
    }

    public void syncFacility(Facility facility) {
        try {
            GeoPoint location = (facility.getLatitude() != null && facility.getLongitude() != null)
                    ? new GeoPoint(facility.getLatitude(), facility.getLongitude())
                    : null;

            // 1. 임베딩 생성 시도
            float[] embedding = generateEmbedding(facility);

            FacilitySearchDocument document = FacilitySearchDocument.builder()
                    .id(facility.getId())
                    .name(facility.getName())
                    .category(facility.getCategory())
                    .address(facility.getAddress())
                    .placeId(facility.getPlaceId())
                    .location(location)
                    .specialties(facility.getSpecialties())
                    .embedding(embedding)
                    .build();

            facilitySearchRepository.save(document);
            log.debug("Synced Facility [{}] to Elasticsearch", document.getId());

            // 2. 임베딩 생성 실패 시 Outbox로 비동기 재시도 등록
            if (embedding == null || embedding.length == 0) {
                scheduleEmbeddingRetry(facility);
            }
        } catch (Exception e) {
            log.warn("[ES Sync] Facility [{}] 동기화 스킵: {}", facility.getId(), e.getMessage());
        }
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
            log.warn("[ES Sync] 임베딩 생성 실패 facility={}: {}", facility.getId(), e.getMessage());
            return new float[0];
        }
    }

    private void scheduleEmbeddingRetry(Facility facility) {
        try {
            String text = String.join(" ",
                    nullSafe(facility.getName()),
                    nullSafe(facility.getCategory()),
                    nullSafe(facility.getDescription()),
                    String.join(" ", facility.getSpecialties()));
            FacilityEmbeddingPayload payload = new FacilityEmbeddingPayload(facility.getId(), text);
            outboxService.saveEvent("AI_EMBEDDING", facility.getId(), "FacilityEmbeddingTask", payload);
            log.info("[ES Sync] 임베딩 Outbox 등록: facilityId={}", facility.getId());
        } catch (Exception e) {
            log.warn("[ES Sync] Outbox 등록 실패 facility={}: {}", facility.getId(), e.getMessage());
        }
    }

    private String nullSafe(String s) {
        return s != null ? s : "";
    }

    public void deleteFacility(String id) {
        try {
            facilitySearchRepository.deleteById(id);
            log.debug("Deleted Facility [{}] from Elasticsearch", id);
        } catch (Exception e) {
            log.warn("[ES Sync] Facility [{}] 삭제 스킵: {}", id, e.getMessage());
        }
    }

    public boolean ensureFacilitiesIndexAndBackfill() {
        try {
            var indexOps = elasticsearchOperations.indexOps(FacilitySearchDocument.class);
            if (indexOps.exists()) {
                return true;
            }

            indexOps.create();
            indexOps.putMapping(indexOps.createMapping(FacilitySearchDocument.class));
            log.info("[ES Sync] facilities 인덱스 생성 완료. 백필을 시작합니다.");
            backfillFacilities();
            return true;
        } catch (Exception e) {
            log.warn("[ES Sync] facilities 인덱스 생성/백필 실패: {}", e.getMessage());
            return false;
        }
    }

    private void backfillFacilities() {
        int page = 0;
        int size = 200;

        while (true) {
            Page<Facility> facilities = facilityRepository.findAllActive(PageRequest.of(page, size));
            if (facilities.isEmpty()) {
                break;
            }

            facilities.getContent().forEach(this::syncFacility);
            if (facilities.isLast()) {
                break;
            }
            page++;
        }
        log.info("[ES Sync] facilities 백필 완료");
    }
}
