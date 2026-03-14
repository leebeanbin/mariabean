package com.mariabean.reservation.facility.application;

import com.mariabean.reservation.facility.application.dto.ResourceItemCreateRequest;
import com.mariabean.reservation.facility.domain.ResourceItemRepository;
import com.mariabean.reservation.search.application.ElasticsearchSyncService;
import com.mariabean.reservation.facility.domain.ResourceItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ResourceItemServiceTest {

    @InjectMocks
    private ResourceItemService resourceItemService;

    @Mock
    private ResourceItemRepository resourceRepository;
    
    @Mock
    private ElasticsearchSyncService esSyncService;

    @Test
    @DisplayName("ResourceItem domain mapping and ES sync succeeds")
    void registerResource_success() {
        // given
        ResourceItemCreateRequest request = ResourceItemCreateRequest.builder()
            .facilityId("fac-1")
            .name("Room 101")
            .resourceType("ROOM")
            .limitCapacity(50)
            .build();
        
        ResourceItem expectedSaved = ResourceItem.builder()
                .id("res-1")
                .facilityId("fac-1")
                .name("Room 101")
                .resourceType("ROOM")
                .limitCapacity(50)
                .build();
                
        given(resourceRepository.save(any(ResourceItem.class))).willReturn(expectedSaved);

        // when
        ResourceItem result = resourceItemService.registerResource(request);

        // then
        assertThat(result.getId()).isEqualTo("res-1");
        verify(resourceRepository).save(any(ResourceItem.class));
        verify(esSyncService).syncResourceItem(any(ResourceItem.class));
    }
}
