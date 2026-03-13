package com.mariabean.reservation.payment.application.pg;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PgReadyResult {

    private String tid;               // PG사 거래 고유번호 (승인 시 필요)
    private String redirectUrl;       // PC 결제 페이지 URL
    private String mobileRedirectUrl; // 모바일 결제 페이지 URL (KakaoPay) / failUrl (TossPay)
    private String clientKey;         // 토스페이먼츠 클라이언트 키 (선택)
}
