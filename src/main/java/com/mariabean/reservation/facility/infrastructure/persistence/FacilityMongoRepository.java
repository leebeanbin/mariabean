package com.mariabean.reservation.facility.infrastructure.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;
import java.util.Optional;

public interface FacilityMongoRepository extends MongoRepository<FacilityMongoEntity, String> {
    Optional<FacilityMongoEntity> findByPlaceId(String placeId);

    Optional<FacilityMongoEntity> findByPlaceIdAndDeletedAtIsNull(String placeId);

    List<FacilityMongoEntity> findByCategory(String category);

    List<FacilityMongoEntity> findByCategoryAndDeletedAtIsNull(String category);

    Page<FacilityMongoEntity> findByCategory(String category, Pageable pageable);

    Page<FacilityMongoEntity> findByCategoryAndDeletedAtIsNull(String category, Pageable pageable);

    Page<FacilityMongoEntity> findByDeletedAtIsNull(Pageable pageable);

    Page<FacilityMongoEntity> findByCategoryAndSpecialtiesInAndDeletedAtIsNull(
            String category, List<String> specialties, Pageable pageable);
}
