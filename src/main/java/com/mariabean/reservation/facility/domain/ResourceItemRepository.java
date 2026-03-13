package com.mariabean.reservation.facility.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface ResourceItemRepository {
    ResourceItem save(ResourceItem resourceItem);

    Optional<ResourceItem> findById(String id);

    ResourceItem getById(String id);

    List<ResourceItem> findByFacilityId(String facilityId);

    Page<ResourceItem> findByFacilityId(String facilityId, Pageable pageable);

    List<ResourceItem> findByFacilityIdAndResourceType(String facilityId, String resourceType);

    List<ResourceItem> findByFacilityIdAndFloor(String facilityId, Integer floor);

    Page<ResourceItem> findByFacilityIdAndFloor(String facilityId, Integer floor, Pageable pageable);

    void deleteById(String id);
}
