package com.mariabean.reservation.search.infrastructure.persistence;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FacilitySearchRepository extends ElasticsearchRepository<FacilitySearchDocument, String> {

    List<FacilitySearchDocument> findByCategory(String category);
}
