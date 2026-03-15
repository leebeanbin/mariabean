# MariBean Backend — API Reference

> Base URL: `http://localhost:8080`
> All responses: `{ success, code, message, data: T }`
> Auth: `Authorization: Bearer {accessToken}` (JWT)

---

## 공통 응답 구조

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "OK",
  "data": { ... }
}
```

## 페이지네이션 응답 구조

```json
{
  "content": [...],
  "totalElements": 100,
  "totalPages": 5,
  "number": 0,
  "size": 20,
  "first": true,
  "last": false,
  "empty": false
}
```

---

## 인증 (Auth) — `/api/v1/auth`

| Method | Path | Auth | 설명 |
|--------|------|------|------|
| POST | `/refresh` | 공개 | Access Token 재발급 |
| POST | `/logout` | 인증 필요 | Refresh Token 삭제 (Redis) |

### POST `/api/v1/auth/refresh`

```
Query: refreshToken=<string>
Response: { accessToken: string }
```

### POST `/api/v1/auth/logout`

```
Header: Authorization: Bearer {accessToken}
Response: null
```

---

## 시설 (Facility) — `/api/v1/facilities`

| Method | Path | Auth | 설명 |
|--------|------|------|------|
| POST | `/` | 인증 | 시설 등록 |
| GET | `/{facilityId}` | 공개 | 시설 상세 조회 (Redis 캐시) |
| GET | `/` | 공개 | 카테고리별 목록 |
| PUT | `/{facilityId}` | 인증(소유자) | 시설 수정 |
| PATCH | `/{facilityId}/medical` | 인증(소유자) | 진료과·운영시간 수정 (병원 전용) |
| DELETE | `/{facilityId}` | 인증(소유자) | Soft Delete |
| GET | `/places/search` | 공개 | 장소 통합 검색 (Kakao+Google) |
| GET | `/places/popular` | 공개 | 인기 검색어 |
| GET | `/places/click` | 공개 | 검색 클릭 기록 |
| GET | `/places/{placeId}` | 공개 | 장소 상세 |

### POST `/api/v1/facilities`

```json
Request:
{
  "name": "강남세브란스병원",
  "category": "HOSPITAL",
  "description": "설명",
  "placeId": "kakao-12345",
  "latitude": 37.49,
  "longitude": 127.01,
  "address": "서울 강남구..."
}

Response: FacilityResponse
```

### GET `/api/v1/facilities`

```
Query: category=HOSPITAL&page=0&size=20
Response: Page<FacilityResponse>
```

**FacilityCategory:** `HOSPITAL | OFFICE | COMMUNITY | SPORTS | LIBRARY | OTHER`

### PATCH `/api/v1/facilities/{facilityId}/medical`

```json
Request:
{
  "specialties": ["01", "13"],
  "operatingHours": {
    "MON": { "open": "09:00", "close": "18:00" },
    "SAT": { "open": "09:00", "close": "13:00" },
    "SUN": null
  }
}
```

**HIRA 진료과 코드:** `01-내과 02-신경과 03-정신건강의학과 04-외과 05-정형외과 06-신경외과 07-흉부외과 08-성형외과 10-산부인과 11-소아청소년과 12-안과 13-이비인후과 14-피부과 15-비뇨의학과 20-재활의학과 22-가정의학과 23-응급의학과 26-치과 27-한방내과 28-한방외과`

### FacilityResponse

```json
{
  "id": "abc123",
  "name": "강남세브란스병원",
  "category": "HOSPITAL",
  "description": "...",
  "placeId": "kakao-12345",
  "latitude": 37.49,
  "longitude": 127.01,
  "address": "서울 강남구...",
  "metadata": { "operatingHours": { ... } },
  "specialties": ["01", "13"]
}
```

---

## 리소스 (ResourceItem) — `/api/v1/resources`

| Method | Path | Auth | 설명 |
|--------|------|------|------|
| POST | `/` | 인증 | 리소스 등록 |
| GET | `/{resourceId}` | 공개 | 단건 조회 |
| GET | `/facility/{facilityId}` | 공개 | 시설별 목록 |
| GET | `/facility/{facilityId}/floor/{floor}` | 공개 | 층별 목록 |
| PUT | `/{resourceId}` | ADMIN | 수정 |
| PATCH | `/{resourceId}/wait-time` | ADMIN | 대기시간 업데이트 |
| DELETE | `/{resourceId}` | ADMIN | 삭제 |

### POST `/api/v1/resources`

```json
Request:
{
  "facilityId": "abc123",
  "name": "A룸",
  "resourceType": "ROOM",
  "limitCapacity": 10,
  "floor": 2,
  "location": "2층 좌측"
}
```

### ResourceItemResponse

```json
{
  "id": "res-001",
  "facilityId": "abc123",
  "name": "A룸",
  "resourceType": "ROOM",
  "limitCapacity": 10,
  "floor": 2,
  "location": "2층 좌측",
  "estimatedWaitMinutes": null
}
```

---

## 예약 (Reservation) — `/api/v1/reservations`

| Method | Path | Auth | 설명 |
|--------|------|------|------|
| POST | `/` | 인증 | 예약 생성 (Redisson 분산락) |
| GET | `/{reservationId}` | 인증 | 상세 조회 |
| GET | `/{reservationId}/waiting` | 인증 | 대기 순번·예상 시간 |
| GET | `/my` | 인증 | 내 예약 목록 |
| GET | `/` | 인증 | 전체 목록 (어드민) |
| POST | `/{reservationId}/confirm` | ADMIN | 예약 확정 |
| POST | `/{reservationId}/reschedule` | ADMIN | 일정 변경 |
| POST | `/{reservationId}/cancel` | 인증 | 취소 |
| POST | `/resource/{resourceItemId}/call-next` | ADMIN | 다음 호출 |

### POST `/api/v1/reservations`

```json
Request:
{
  "resourceItemId": "res-001",
  "facilityId": "abc123",
  "startTime": "2026-03-15T10:00:00",
  "endTime": "2026-03-15T11:00:00",
  "seatLabel": "A-3"
}
```

### ReservationResponse

```json
{
  "id": 1,
  "memberId": 42,
  "resourceItemId": "res-001",
  "facilityId": "abc123",
  "seatLabel": "A-3",
  "startTime": "2026-03-15T10:00:00",
  "endTime": "2026-03-15T11:00:00",
  "status": "PENDING",
  "createdAt": "2026-03-12T15:00:00"
}
```

**ReservationStatus:** `PENDING | CONFIRMED | CANCELLED | EXPIRED`

### WaitingInfoResponse

```json
{
  "reservationId": 1,
  "queuePosition": 3,
  "estimatedWaitMinutes": 15,
  "totalActiveReservations": 10
}
```

---

## 공개 예약 (Public) — `/api/v1/public/reservations` *(인증 불필요)*

| Method | Path | Auth | 설명 |
|--------|------|------|------|
| GET | `/availability` | 공개 | 30분 단위 시간슬롯 조회 |

### GET `/api/v1/public/reservations/availability`

```
Query: resourceItemId=res-001&date=2026-03-15

Response:
{
  "resourceItemId": "res-001",
  "date": "2026-03-15",
  "slots": [
    { "startTime": "09:00", "endTime": "09:30", "available": true },
    { "startTime": "09:30", "endTime": "10:00", "available": false }
  ]
}
```

---

## 결제 (Payment) — `/api/v1/payments`

| Method | Path | Auth | 설명 |
|--------|------|------|------|
| POST | `/ready` | 인증 | 결제 준비 |
| POST | `/approve` | 인증 | 결제 승인 → Outbox 이벤트 |
| POST | `/{paymentId}/cancel` | 인증 | 취소 |
| GET | `/{paymentId}` | 인증 | 상세 조회 |
| GET | `/reservation/{reservationId}` | 인증 | 예약별 결제 조회 |

### POST `/api/v1/payments/ready`

```json
Request:
{
  "reservationId": 1,
  "provider": "KAKAO_PAY",
  "amount": 50000
}
```

### POST `/api/v1/payments/approve`

```json
Request:
{
  "paymentId": 10,
  "pgToken": "pg_token_from_kakao"
}
```

### PaymentResponse

```json
{
  "id": 10,
  "reservationId": 1,
  "provider": "KAKAO_PAY",
  "status": "APPROVED",
  "amount": 50000,
  "transactionId": "T123456",
  "createdAt": "2026-03-12T15:00:00",
  "approvedAt": "2026-03-12T15:01:00"
}
```

**PaymentStatus:** `READY | APPROVED | CANCELLED | FAILED | REFUNDED`
**PaymentProvider:** `KAKAO_PAY | TOSS_PAY`

---

## 검색 (Search) — `/api/v1/search`

| Method | Path | Auth | 설명 |
|--------|------|------|------|
| GET | `/hospitals/specialties` | 공개 | HIRA 진료과 20종 목록 |
| GET | `/hospitals/nearby` | 공개 | 병원 통합 검색 |
| GET | `/resources` | 공개 | 리소스 키워드 검색 (ES nori) |
| GET | `/facilities/nearby` | 공개 | 반경 시설 검색 |
| GET | `/facilities/box` | 공개 | 지도 Bounding Box 검색 |

### GET `/api/v1/search/hospitals/nearby`

```
Query:
  lat=37.5665
  lng=126.9780
  radiusKm=5           (기본 5.0)
  specialties=01,13    (HIRA 코드, 쉼표 구분 — query 없을 때)
  symptom=headache     (증상 ID → 서버에서 코드 변환 — query 없을 때)
  query=강남 정형외과   (자연어 — 있으면 specialties/symptom 무시)
  openNow=true
  page=0
  size=20

Response: Page<HospitalSearchResult>
```

### HospitalSearchResult

```json
{
  "facilityId": "abc123",
  "placeId": "kakao-12345",
  "name": "강남세브란스병원",
  "address": "서울 강남구...",
  "latitude": 37.49,
  "longitude": 127.01,
  "specialties": ["01", "13"],
  "openNow": true,
  "operatingHours": {
    "MON": { "open": "09:00", "close": "18:00" },
    "SUN": null
  },
  "source": "INTERNAL",
  "rank": 1
}
```

- `facilityId`: null이면 Kakao 외부 병원 (예약 불가)
- `openNow`: null이면 운영시간 미등록
- `source`: `INTERNAL` (내부 등록) | `KAKAO` (외부)
- 정렬: 내부 영업중 → 내부 기타 → Kakao

### 증상 ID 목록

| symptom | 매핑 HIRA 코드 |
|---------|--------------|
| headache | 02, 01 |
| fever | 01, 11 |
| cough | 01, 13 |
| stomachache | 01 |
| toothache | 26 |
| skin | 14 |
| eyes | 12, 13 |
| bone | 05, 20 |
| mental | 03 |
| womens | 10 |
| kids | 11 |
| heart | 23, 01 |

### GET `/api/v1/search/facilities/box`

```
Query:
  topLeftLat=37.6
  topLeftLng=126.9
  bottomRightLat=37.5
  bottomRightLng=127.0
  keyword=스터디룸  (선택)
  page=0&size=50
```

---

## 이메일 템플릿 (Admin) — `/api/v1/admin/email/templates`

| Method | Path | Auth | 설명 |
|--------|------|------|------|
| GET | `/` | ADMIN | 목록 조회 |
| GET | `/{id}` | ADMIN | 단건 조회 |
| POST | `/` | ADMIN | 생성 |
| PUT | `/{id}` | ADMIN | 수정 |
| DELETE | `/{id}` | ADMIN | 삭제 |

### EmailTemplateRequest / Response

```json
Request:
{
  "name": "예약 확정 알림",
  "subject": "[MariBean] 예약이 확정되었습니다",
  "body": "안녕하세요 {{name}}님, {{date}} 예약이 확정되었습니다.",
  "variables": ["name", "date"]
}

Response:
{
  "id": 1,
  "name": "예약 확정 알림",
  "subject": "...",
  "body": "...",
  "variables": ["name", "date"],
  "createdAt": "2026-03-12T15:00:00",
  "updatedAt": "2026-03-12T15:00:00"
}
```

---

## 이메일 발송 (Admin) — `/api/v1/admin/email`

| Method | Path | Auth | 설명 |
|--------|------|------|------|
| POST | `/send` | ADMIN | 즉시 발송 |
| POST | `/schedule` | ADMIN | 예약 발송 |
| GET | `/scheduled` | ADMIN | 예약 발송 목록 |

### POST `/api/v1/admin/email/send`

```json
{
  "templateId": 1,
  "recipientEmail": "user@example.com",
  "variables": { "name": "홍길동", "date": "2026-03-15" }
}
```

### POST `/api/v1/admin/email/schedule`

```json
{
  "templateId": 1,
  "recipientEmail": "user@example.com",
  "scheduledAt": "2026-03-15T09:00:00",
  "variables": { "name": "홍길동" }
}
```

### ScheduledEmailStatus: `PENDING | SENT | FAILED`

---

## 에러 코드

| code | HTTP | 설명 |
|------|------|------|
| TOKEN_EXPIRED | 401 | 토큰 만료 |
| UNAUTHORIZED | 403 | 권한 없음 |
| ENTITY_NOT_FOUND | 404 | 리소스 없음 |
| INVALID_INPUT_VALUE | 400 | 유효성 검증 실패 |
| DUPLICATE_RESERVATION | 409 | 중복 예약 |
| LOCK_ACQUISITION_FAILED | 409 | 분산락 획득 실패 (동시 예약 충돌) |

---

## 보안 구조

```
공개(permitAll):
  /api/v1/auth/**
  /api/v1/public/**
  GET /api/v1/facilities/**
  GET /api/v1/resources/**
  GET /api/v1/reservations/**
  GET /api/v1/search/**
  /swagger-ui/**, /v3/api-docs/**

인증 필요: 나머지 모든 POST/PUT/PATCH/DELETE

ADMIN 전용:
  POST /api/v1/reservations/{id}/confirm
  POST /api/v1/reservations/{id}/reschedule
  POST /api/v1/reservations/resource/{id}/call-next
  PUT/PATCH/DELETE /api/v1/resources/**
  /api/v1/admin/**
```

---

## Swagger UI

`http://localhost:8080/swagger-ui.html`
