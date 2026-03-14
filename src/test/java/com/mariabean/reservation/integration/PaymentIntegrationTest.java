package com.mariabean.reservation.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mariabean.reservation.IntegrationTestBase;
import com.mariabean.reservation.payment.application.dto.PaymentApproveRequest;
import com.mariabean.reservation.support.WithMockUserPrincipal;
import com.mariabean.reservation.payment.application.dto.PaymentReadyRequest;
import com.mariabean.reservation.payment.domain.PaymentProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-flow integration test for Payment + Kafka pipeline.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Tag("integration")
class PaymentIntegrationTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    @WithMockUserPrincipal(memberId = 1L, email = "testuser@test.com")
    @DisplayName("[통합테스트] 결제 준비 → 승인 → Kafka 이벤트 발행 전체 흐름")
    void fullPaymentLifecycle() throws Exception {
        // 1. Ready Payment
        PaymentReadyRequest readyRequest = PaymentReadyRequest.builder()
                .reservationId(1L)
                .provider(PaymentProvider.KAKAO_PAY)
                .amount(BigDecimal.valueOf(50000))
                .build();

        String readyResponse = mockMvc.perform(post("/api/v1/payments/ready")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(readyRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("READY"))
                .andReturn().getResponse().getContentAsString();

        Long paymentId = objectMapper.readTree(readyResponse).path("data").path("id").asLong();

        // 2. Approve Payment (simulates PG callback)
        PaymentApproveRequest approveRequest = PaymentApproveRequest.builder()
                .paymentId(paymentId)
                .pgToken("test-pg-token")
                .build();

        mockMvc.perform(post("/api/v1/payments/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(approveRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.transactionId").isNotEmpty());

        // 3. Verify via GET
        mockMvc.perform(get("/api/v1/payments/" + paymentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));

        // 4. Cancel
        mockMvc.perform(post("/api/v1/payments/" + paymentId + "/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }
}
