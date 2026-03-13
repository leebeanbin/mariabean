package com.mariabean.reservation.search.infrastructure.persistence;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ResourceItemSearchRepository extends ElasticsearchRepository<ResourceItemSearchDocument, String> {
    
    // Core search methods
    List<ResourceItemSearchDocument> findByNameContaining(String name);
    List<ResourceItemSearchDocument> findByFacilityIdAndResourceType(String facilityId, String resourceType);
}
