package com.mariabean.reservation.facility.application;

import com.mariabean.reservation.facility.domain.ResourceItemRepository;
import com.mariabean.reservation.facility.application.dto.ResourceItemCreateRequest;
import com.mariabean.reservation.facility.application.dto.ResourceItemUpdateRequest;
import com.mariabean.reservation.search.application.ElasticsearchSyncService;
import com.mariabean.reservation.global.exception.BusinessException;
import com.mariabean.reservation.global.exception.ErrorCode;
import com.mariabean.reservation.facility.domain.ResourceItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResourceItemService {

    private final ResourceItemRepository resourceRepository;
    private final ElasticsearchSyncService esSyncService;

    @Transactional
    public ResourceItem registerResource(ResourceItemCreateRequest request) {
        ResourceItem item = ResourceItem.builder()
                .facilityId(request.getFacilityId())
                .name(request.getName())
                .resourceType(request.getResourceType())
                .limitCapacity(request.getLimitCapacity())
                .floor(request.getFloor())
                .location(request.getLocation())
                .customAttributes(request.getCustomAttributes())
                .build();

        ResourceItem saved = resourceRepository.save(item);
        esSyncService.syncResourceItem(saved);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<ResourceItem> getResourcesByFacility(String facilityId) {
        return resourceRepository.findByFacilityId(facilityId);
    }

    @Transactional(readOnly = true)
    public Page<ResourceItem> getResourcesByFacility(String facilityId, Pageable pageable) {
        return resourceRepository.findByFacilityId(facilityId, pageable);
    }

    @Transactional(readOnly = true)
    public List<ResourceItem> getResourcesByFacilityAndFloor(String facilityId, Integer floor) {
        return resourceRepository.findByFacilityIdAndFloor(facilityId, floor);
    }

    @Transactional(readOnly = true)
    public Page<ResourceItem> getResourcesByFacilityAndFloor(String facilityId, Integer floor, Pageable pageable) {
        return resourceRepository.findByFacilityIdAndFloor(facilityId, floor, pageable);
    }

    @Transactional
    public ResourceItem updateEstimatedWaitTime(String resourceId, Integer minutes) {
        ResourceItem item = resourceRepository.getById(resourceId);
        item.updateEstimatedWaitMinutes(minutes);
        return resourceRepository.save(item);
    }

    @Transactional
    public ResourceItem updateResource(String resourceId, ResourceItemUpdateRequest request) {
        ResourceItem item = resourceRepository.getById(resourceId);
        item.updateDetails(
                request.getName(),
                request.getResourceType(),
                request.getLimitCapacity(),
                request.getFloor(),
                request.getLocation(),
                request.getCustomAttributes());
        ResourceItem saved = resourceRepository.save(item);
        esSyncService.syncResourceItem(saved);
        return saved;
    }

    @Transactional(readOnly = true)
    public ResourceItem getResource(String resourceId) {
        return resourceRepository.getById(resourceId);
    }

    @Transactional
    public void deleteResource(String resourceId) {
        if (resourceRepository.findById(resourceId).isEmpty()) {
            throw new BusinessException(ErrorCode.ENTITY_NOT_FOUND);
        }
        resourceRepository.deleteById(resourceId);
    }
}
