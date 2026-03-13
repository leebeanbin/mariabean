package com.mariabean.reservation.facility.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface FacilityRepository {
    Facility save(Facility facility);

    Optional<Facility> findById(String id);

    List<Facility> findAllById(List<String> ids);

    Facility getById(String id);

    Optional<Facility> findByPlaceId(String placeId);

    List<Facility> findByCategory(String category);

    Page<Facility> findByCategory(String category, Pageable pageable);

    Page<Facility> findAllActive(Pageable pageable);

    Page<Facility> findByCategoryAndSpecialtiesIn(String category, List<String> codes, Pageable pageable);

    void deleteById(String id);
}
