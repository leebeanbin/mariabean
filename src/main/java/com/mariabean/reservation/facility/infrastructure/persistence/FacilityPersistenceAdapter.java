package com.mariabean.reservation.facility.infrastructure.persistence;

import com.mariabean.reservation.global.exception.BusinessException;
import com.mariabean.reservation.global.exception.ErrorCode;
import com.mariabean.reservation.facility.domain.Facility;
import com.mariabean.reservation.facility.domain.FacilityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

// MongoDB에는 @SQLRestriction이 없으므로 soft-delete 필터링은 query에서 직접 처리

@Repository
@RequiredArgsConstructor
public class FacilityPersistenceAdapter implements FacilityRepository {

    private final FacilityMongoRepository mongoRepository;

    @Override
    public Facility save(Facility facility) {
        if (facility.getPlaceId() != null && facility.getId() == null) {
            if (mongoRepository.findByPlaceId(facility.getPlaceId()).isPresent()) {
                throw new BusinessException(ErrorCode.PLACE_ALREADY_REGISTERED);
            }
        }
        FacilityMongoEntity entity = FacilityMongoEntity.builder()
                .id(facility.getId())
                .name(facility.getName())
                .category(facility.getCategory())
                .description(facility.getDescription())
                .ownerMemberId(facility.getOwnerMemberId())
                .placeId(facility.getPlaceId())
                .latitude(facility.getLatitude())
                .longitude(facility.getLongitude())
                .address(facility.getAddress())
                .metadata(facility.getMetadata())
                .specialties(facility.getSpecialties())
                .build();

        return mapToDomain(mongoRepository.save(entity));
    }

    @Override
    public Optional<Facility> findById(String id) {
        return mongoRepository.findById(id).map(this::mapToDomain);
    }

    @Override
    public Facility getById(String id) {
        return findById(id).orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));
    }

    @Override
    public Optional<Facility> findByPlaceId(String placeId) {
        return mongoRepository.findByPlaceIdAndDeletedAtIsNull(placeId).map(this::mapToDomain);
    }

    @Override
    public List<Facility> findByCategory(String category) {
        return mongoRepository.findByCategoryAndDeletedAtIsNull(category).stream()
                .map(this::mapToDomain).collect(Collectors.toList());
    }

    @Override
    public Page<Facility> findByCategory(String category, Pageable pageable) {
        return mongoRepository.findByCategoryAndDeletedAtIsNull(category, pageable)
                .map(this::mapToDomain);
    }

    @Override
    public Page<Facility> findAllActive(Pageable pageable) {
        return mongoRepository.findByDeletedAtIsNull(pageable)
                .map(this::mapToDomain);
    }

    @Override
    public Page<Facility> findByCategoryAndSpecialtiesIn(String category, List<String> codes, Pageable pageable) {
        return mongoRepository.findByCategoryAndSpecialtiesInAndDeletedAtIsNull(category, codes, pageable)
                .map(this::mapToDomain);
    }

    @Override
    public void deleteById(String id) {
        mongoRepository.findById(id).ifPresent(entity -> {
            entity.softDelete();
            mongoRepository.save(entity);
        });
    }

    private Facility mapToDomain(FacilityMongoEntity entity) {
        return Facility.builder()
                .id(entity.getId())
                .name(entity.getName())
                .category(entity.getCategory())
                .description(entity.getDescription())
                .ownerMemberId(entity.getOwnerMemberId())
                .placeId(entity.getPlaceId())
                .latitude(entity.getLatitude())
                .longitude(entity.getLongitude())
                .address(entity.getAddress())
                .metadata(entity.getMetadata())
                .specialties(entity.getSpecialties())
                .build();
    }
}
