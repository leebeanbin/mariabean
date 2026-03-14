package com.mariabean.reservation.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mariabean.reservation.IntegrationTestBase;
import com.mariabean.reservation.reservation.application.dto.ReservationCreateRequest;
import com.mariabean.reservation.support.WithMockUserPrincipal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-flow integration test using real Testcontainers infrastructure.
 * Validates: API → Service → Repository Adapter → Database round-trip.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Tag("integration")
class ReservationIntegrationTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    @WithMockUserPrincipal(memberId = 1L, email = "testuser@test.com", role = "ROLE_ADMIN")
    @DisplayName("[통합테스트] 예약 생성 → 조회 → 확정 → 취소 전체 흐름")
    void fullReservationLifecycle() throws Exception {
        // 1. Create Reservation
        ReservationCreateRequest createRequest = ReservationCreateRequest.builder()
                .resourceItemId("resource-integration-1")
                .facilityId("facility-integration-1")
                .startTime(LocalDateTime.of(2026, 6, 15, 14, 0))
                .endTime(LocalDateTime.of(2026, 6, 15, 15, 0))
                .build();

        String createResponse = mockMvc.perform(post("/api/v1/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.resourceItemId").value("resource-integration-1"))
                .andReturn().getResponse().getContentAsString();

        // Extract reservation ID
        Long reservationId = objectMapper.readTree(createResponse).path("data").path("id").asLong();

        // 2. Get Reservation
        mockMvc.perform(get("/api/v1/reservations/" + reservationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(reservationId));

        // 3. Confirm Reservation
        mockMvc.perform(post("/api/v1/reservations/" + reservationId + "/confirm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"));

        // 4. Cancel Reservation
        mockMvc.perform(post("/api/v1/reservations/" + reservationId + "/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }

    @Test
    @WithMockUserPrincipal(memberId = 1L, email = "testuser@test.com")
    @DisplayName("[통합테스트] 존재하지 않는 예약 조회 시 404 에러 응답")
    void getReservation_notFound_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/reservations/999999"))
                .andExpect(status().isNotFound());
    }
}
