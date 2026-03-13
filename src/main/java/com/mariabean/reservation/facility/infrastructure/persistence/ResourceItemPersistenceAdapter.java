package com.mariabean.reservation.facility.infrastructure.persistence;

import com.mariabean.reservation.facility.domain.ResourceItem;
import com.mariabean.reservation.facility.domain.ResourceItemRepository;
import com.mariabean.reservation.global.exception.BusinessException;
import com.mariabean.reservation.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class ResourceItemPersistenceAdapter implements ResourceItemRepository {

    private final ResourceItemMongoRepository mongoRepository;
    private final FacilityMongoRepository facilityMongoRepository;

    @Override
    public ResourceItem save(ResourceItem resourceItem) {
        if (!facilityMongoRepository.existsById(resourceItem.getFacilityId())) {
            throw new BusinessException(ErrorCode.ENTITY_NOT_FOUND);
        }

        ResourceItemMongoEntity entity = ResourceItemMongoEntity.builder()
                .id(resourceItem.getId())
                .facilityId(resourceItem.getFacilityId())
                .name(resourceItem.getName())
                .resourceType(resourceItem.getResourceType())
                .limitCapacity(resourceItem.getLimitCapacity())
                .floor(resourceItem.getFloor())
                .location(resourceItem.getLocation())
                .estimatedWaitMinutes(resourceItem.getEstimatedWaitMinutes())
                .customAttributes(resourceItem.getCustomAttributes())
                .build();

        ResourceItemMongoEntity saved = mongoRepository.save(entity);
        return mapToDomain(saved);
    }

    @Override
    public Optional<ResourceItem> findById(String id) {
        return mongoRepository.findById(id).map(this::mapToDomain);
    }

    @Override
    public ResourceItem getById(String id) {
        return findById(id).orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));
    }

    @Override
    public List<ResourceItem> findByFacilityId(String facilityId) {
        return mongoRepository.findByFacilityIdAndDeletedAtIsNull(facilityId).stream()
                .map(this::mapToDomain).collect(Collectors.toList());
    }

    @Override
    public Page<ResourceItem> findByFacilityId(String facilityId, Pageable pageable) {
        return mongoRepository.findByFacilityIdAndDeletedAtIsNull(facilityId, pageable)
                .map(this::mapToDomain);
    }

    @Override
    public List<ResourceItem> findByFacilityIdAndResourceType(String facilityId, String resourceType) {
        return mongoRepository.findByFacilityIdAndResourceTypeAndDeletedAtIsNull(facilityId, resourceType).stream()
                .map(this::mapToDomain).collect(Collectors.toList());
    }

    @Override
    public List<ResourceItem> findByFacilityIdAndFloor(String facilityId, Integer floor) {
        return mongoRepository.findByFacilityIdAndFloorAndDeletedAtIsNull(facilityId, floor).stream()
                .map(this::mapToDomain).collect(Collectors.toList());
    }

    @Override
    public Page<ResourceItem> findByFacilityIdAndFloor(String facilityId, Integer floor, Pageable pageable) {
        return mongoRepository.findByFacilityIdAndFloorAndDeletedAtIsNull(facilityId, floor, pageable)
                .map(this::mapToDomain);
    }

    @Override
    public void deleteById(String id) {
        mongoRepository.findById(id).ifPresent(entity -> {
            entity.softDelete();
            mongoRepository.save(entity);
        });
    }

    private ResourceItem mapToDomain(ResourceItemMongoEntity entity) {
        return ResourceItem.builder()
                .id(entity.getId())
                .facilityId(entity.getFacilityId())
                .name(entity.getName())
                .resourceType(entity.getResourceType())
                .limitCapacity(entity.getLimitCapacity())
                .floor(entity.getFloor())
                .location(entity.getLocation())
                .estimatedWaitMinutes(entity.getEstimatedWaitMinutes())
                .customAttributes(entity.getCustomAttributes())
                .build();
    }
}
