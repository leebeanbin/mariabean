package com.mariabean.reservation.facility.infrastructure.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface ResourceItemMongoRepository extends MongoRepository<ResourceItemMongoEntity, String> {
    List<ResourceItemMongoEntity> findByFacilityId(String facilityId);

    List<ResourceItemMongoEntity> findByFacilityIdAndDeletedAtIsNull(String facilityId);

    Page<ResourceItemMongoEntity> findByFacilityIdAndDeletedAtIsNull(String facilityId, Pageable pageable);

    List<ResourceItemMongoEntity> findByFacilityIdAndResourceType(String facilityId, String resourceType);

    List<ResourceItemMongoEntity> findByFacilityIdAndResourceTypeAndDeletedAtIsNull(String facilityId,
            String resourceType);

    List<ResourceItemMongoEntity> findByFacilityIdAndFloor(String facilityId, Integer floor);

    List<ResourceItemMongoEntity> findByFacilityIdAndFloorAndDeletedAtIsNull(String facilityId, Integer floor);

    Page<ResourceItemMongoEntity> findByFacilityIdAndFloorAndDeletedAtIsNull(String facilityId, Integer floor,
            Pageable pageable);
}
