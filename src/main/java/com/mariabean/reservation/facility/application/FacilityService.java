package com.mariabean.reservation.facility.application;

import com.mariabean.reservation.facility.application.dto.FacilityCreateRequest;
import com.mariabean.reservation.facility.application.dto.FacilityMedicalUpdateRequest;
import com.mariabean.reservation.facility.application.dto.FacilityUpdateRequest;
import com.mariabean.reservation.facility.application.dto.PlaceSearchResult;
import com.mariabean.reservation.facility.infrastructure.config.HiraSpecialtyConfig;
import com.mariabean.reservation.search.application.ElasticsearchSyncService;
import com.mariabean.reservation.global.exception.BusinessException;
import com.mariabean.reservation.global.exception.ErrorCode;
import com.mariabean.reservation.facility.domain.Facility;
import com.mariabean.reservation.facility.domain.FacilityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FacilityService {

    private final FacilityRepository facilityRepository;
    private final MapService mapService;
    private final ElasticsearchSyncService elasticsearchSyncService;
    private final HiraSpecialtyConfig hiraSpecialtyConfig;

    /**
     * Facility 등록.
     * - placeId가 있으면 Google Places API로 이름/주소/좌표를 자동 보완한다.
     * - placeId가 없으면 요청에 담긴 값을 그대로 사용한다 (수동 입력).
     * - 저장 후 Elasticsearch에 동기화한다.
     */
    @Transactional
    public Facility registerFacility(FacilityCreateRequest request, Long ownerId) {
        String name = request.getName();
        String address = request.getAddress();
        Double latitude = request.getLatitude();
        Double longitude = request.getLongitude();
        String placeId = request.getPlaceId();

        boolean hasCoordinates = latitude != null && longitude != null && latitude != 0 && longitude != 0;
        if (placeId != null && !placeId.isBlank() && !hasCoordinates) {
            PlaceSearchResult details = mapService.getPlaceDetails(placeId);
            if (details != null) {
                name = details.getName() != null ? details.getName() : name;
                address = details.getAddress() != null ? details.getAddress() : address;
                latitude = details.getLatitude() != null ? details.getLatitude() : latitude;
                longitude = details.getLongitude() != null ? details.getLongitude() : longitude;
                log.info("[FacilityService] Auto-filled from Google Places: placeId={}, name={}", placeId, name);
            } else {
                log.debug("[FacilityService] Google Places returned null for placeId={}. Using frontend-provided values.", placeId);
            }
        }

        Facility facility = Facility.builder()
                .name(name)
                .category(request.getCategory())
                .description(request.getDescription())
                .ownerMemberId(ownerId)
                .placeId(placeId)
                .latitude(latitude)
                .longitude(longitude)
                .address(address)
                .metadata(request.getMetadata())
                .build();

        Facility saved = facilityRepository.save(facility);

        try {
            elasticsearchSyncService.syncFacility(saved);
        } catch (Exception e) {
            log.warn("[FacilityService] ES sync failed for facility [{}]: {}", saved.getId(), e.getMessage());
        }

        return saved;
    }

    @Cacheable(value = "facilities", key = "#facilityId")
    @Transactional(readOnly = true)
    public Facility getFacility(String facilityId) {
        return facilityRepository.getById(facilityId);
    }

    @Transactional(readOnly = true)
    public List<Facility> getFacilitiesByCategory(String category) {
        return facilityRepository.findByCategory(category);
    }

    @Transactional(readOnly = true)
    public Page<Facility> getFacilitiesByCategory(String category, Pageable pageable) {
        if (category == null || category.isBlank()) {
            return facilityRepository.findAllActive(pageable);
        }
        return facilityRepository.findByCategory(category, pageable);
    }

    @CacheEvict(value = "facilities", key = "#facilityId")
    @Transactional
    public Facility updateFacility(String facilityId, FacilityUpdateRequest request, Long currentMemberId) {
        Facility facility = facilityRepository.getById(facilityId);

        if (!facility.getOwnerMemberId().equals(currentMemberId)) {
            throw new BusinessException(ErrorCode.RESERVATION_OWNERSHIP_DENIED);
        }

        facility.updateDetails(request.getName(), request.getDescription(), request.getMetadata());
        Facility saved = facilityRepository.save(facility);

        try {
            elasticsearchSyncService.syncFacility(saved);
        } catch (Exception e) {
            log.warn("[FacilityService] ES sync failed for facility [{}]: {}", saved.getId(), e.getMessage());
        }

        return saved;
    }

    @CacheEvict(value = "facilities", key = "#facilityId")
    @Transactional
    public Facility updateFacilityMedical(String facilityId, FacilityMedicalUpdateRequest request, Long currentMemberId) {
        Facility facility = facilityRepository.getById(facilityId);

        if (!facility.getOwnerMemberId().equals(currentMemberId)) {
            throw new BusinessException(ErrorCode.RESERVATION_OWNERSHIP_DENIED);
        }

        // HIRA 코드 유효성 검증
        if (request.getSpecialties() != null) {
            request.getSpecialties().forEach(code -> {
                if (!hiraSpecialtyConfig.isValidCode(code)) {
                    throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
                }
            });
        }

        facility.updateMedicalInfo(request.getSpecialties(), request.getOperatingHours());
        Facility saved = facilityRepository.save(facility);

        try {
            elasticsearchSyncService.syncFacility(saved);
        } catch (Exception e) {
            log.warn("[FacilityService] ES sync failed for facility [{}]: {}", saved.getId(), e.getMessage());
        }

        return saved;
    }

    @Transactional(readOnly = true)
    public boolean isOwner(String facilityId, Long memberId) {
        return facilityRepository.getById(facilityId).getOwnerMemberId().equals(memberId);
    }

    @CacheEvict(value = "facilities", key = "#facilityId")
    @Transactional
    public void deleteFacility(String facilityId, Long currentMemberId) {
        Facility facility = facilityRepository.getById(facilityId);

        if (!facility.getOwnerMemberId().equals(currentMemberId)) {
            throw new BusinessException(ErrorCode.RESERVATION_OWNERSHIP_DENIED);
        }

        facilityRepository.deleteById(facilityId);
    }
}
