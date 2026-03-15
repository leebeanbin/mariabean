# MariBean — 예약 시스템 API

Spring Boot 기반의 공간·의료 예약 플랫폼 백엔드입니다.
다중 데이터 저장소, 분산락, 이벤트 기반 아키텍처(Transactional Outbox), 병원 지능형 검색(HIRA 표준 진료과 + Kakao Local)을 포함합니다.

---

## 기술 스택

| 구분 | 기술 |
|------|------|
| Language | Java 17 (Azul Zulu) |
| Framework | Spring Boot 3.3.x |
| Auth | Spring Security + OAuth2 (Google, Kakao) + JWT |
| RDB | PostgreSQL 16 + Spring Data JPA |
| Document DB | MongoDB 7.0 + Spring Data MongoDB |
| Cache / Lock | Redis 7 + Redisson (분산락) + Spring Cache |
| Search | Elasticsearch 8.x + analysis-nori (한국어 형태소) |
| Message Queue | Kafka (Confluent Platform 7.6) |
| 지도/장소 | Google Places API + Kakao Local REST API |
| 알림 | Gmail API (OAuth2 발송) |
| Rate Limiting | Bucket4j |
| API Docs | Springdoc OpenAPI (Swagger UI `/swagger-ui.html`) |
| Build | Gradle |

---

## 아키텍처 (Domain-Driven Package Structure)

```
com.mariabean.reservation
├── global/          # 전역 설정·예외·보안·응답 래퍼
├── auth/            # JWT, OAuth2 (Google/Kakao), SecurityUtils
├── member/          # 회원 도메인
├── facility/        # 시설·리소스·지도 도메인
│   ├── api/         # FacilityController, ResourceItemController
│   ├── application/ # FacilityService, ResourceItemService, MapService
│   │               # SymptomSpecialtyMapping (증상→HIRA코드)
│   ├── domain/      # Facility (specialties, operatingHours), ResourceItem
│   └── infrastructure/
│       ├── config/  # HiraSpecialtyConfig (@ConfigurationProperties)
│       ├── external/map/  # KakaoLocalSearchClient, GoogleMapClient
│       └── persistence/   # FacilityMongoEntity, FacilityPersistenceAdapter
├── reservation/     # 예약 도메인 (분산락, 만료 스케줄러)
├── payment/         # 결제 도메인 (KakaoPay, TossPay PG)
├── search/          # 검색 도메인
│   ├── api/         # SearchController
│   ├── application/ # SearchService, HospitalSearchService
│   │               # ElasticsearchSyncService
│   └── infrastructure/ # FacilitySearchDocument (specialties 포함)
├── notification/    # 알림 도메인
│   └── infrastructure/
│       └── gmail/   # GmailEmailSender, GmailTokenStore (Redis)
├── email/           # 이메일 템플릿·예약 발송 도메인
└── event/           # Kafka + Transactional Outbox Pattern
```

### 핵심 설계 원칙

| 원칙 | 내용 |
|------|------|
| Adapter Pattern | Repository는 interface, 영속 계층이 PersistenceAdapter로 구현 |
| Soft Delete | `deletedAt` + 쿼리 레벨 필터 |
| Audit Trail | `BaseTimeEntity / BaseMongoTimeEntity` — createdAt/updatedAt 자동 관리 |
| 예외 위임 | `getById()` 패턴으로 서비스 레이어 `orElseThrow` 제거 |

---

## 주요 기능

### 1. 병원 지능형 검색 (HIRA 표준)
- **HIRA 진료과 코드 20종** (`application.yml` → `HiraSpecialtyConfig`)
- **증상 → 진료과 코드 매핑** (`SymptomSpecialtyMapping`): 두통→`02,01`, 여성건강→`10` 등
- **ES 진료과 필터 검색**: `specialties: List<String>` 필드 + geo_distance 복합 쿼리
- **자연어 쿼리 검색**: "강남 정형외과" → ES nori match(fuzzy) + Kakao Local HP8 카테고리 직접 검색
- **Kakao Local 통합**: 미등록 외부 병원까지 결과 합산, placeId 기준 중복 제거 (내부 우선)
- **영업중(openNow) 필터**: MongoDB `metadata.operatingHours`를 KST 현재 시각과 비교
- **Redis 캐시** TTL 60s (`hospitalSearch` 캐시 키)

### 2. 동시성 제어 (Redisson 분산락)
- `reservation:lock:{resourceItemId}` 키로 중복 예약 방지

### 3. Transactional Outbox Pattern
- 결제·예약 이벤트를 DB와 동일 트랜잭션으로 `OutboxEvent` 테이블에 저장
- `OutboxEventPublisherScheduler` (5초 주기) → Kafka 발행

### 4. Gmail 알림 시스템
- Google OAuth2 Access Token → `GmailTokenStore` (Redis 저장)
- `GmailEmailSender` + `ScheduledEmailProcessor` (60초 주기)
- 알림 채널 전환: `NOTIFICATION_CHANNEL=gmail|kakao|log`

### 5. 성능 최적화
- 시설 단건 조회 `@Cacheable` (Redis)
- ES Bounding Box 공간 검색 (지도 화면 내 시설)

### 6. Rate Limiting
- Google Places API 엔드포인트: Bucket4j 1분/IP당 10회

---

## API 엔드포인트

### Auth — `/api/v1/auth`
| Method | Path | 설명 |
|--------|------|------|
| POST | `/refresh` | Access Token 재발급 |
| POST | `/logout` | Refresh Token 삭제 |

### Facility — `/api/v1/facilities`
| Method | Path | 설명 |
|--------|------|------|
| POST | `/` | 시설 등록 (Google Places 자동 보완) |
| GET | `/{id}` | 시설 상세 (캐시) |
| GET | `/?category=&page=&size=` | 카테고리별 목록 |
| PUT | `/{id}` | 시설 수정 |
| PATCH | `/{id}/medical` | 진료과·운영시간 수정 (병원 전용) |
| DELETE | `/{id}` | 시설 삭제 (Soft Delete) |
| GET | `/places/search?query=` | 장소 텍스트 검색 (Kakao+Google) |
| GET | `/places/{placeId}` | 장소 상세 |
| GET | `/places/popular` | 인기 검색어 |

### ResourceItem — `/api/v1/resources`
| Method | Path | 설명 |
|--------|------|------|
| POST | `/` | 리소스 등록 |
| GET | `/facility/{facilityId}` | 시설별 리소스 목록 |
| GET | `/{resourceId}` | 단건 조회 |
| PUT | `/{resourceId}` | 수정 |
| DELETE | `/{resourceId}` | 삭제 |

### Reservation — `/api/v1/reservations`
| Method | Path | 설명 |
|--------|------|------|
| POST | `/` | 예약 생성 (분산락) |
| GET | `/my` | 내 예약 목록 |
| GET | `/{id}` | 상세 조회 |
| POST | `/{id}/cancel` | 취소 |
| POST | `/{id}/confirm` | 확정 (ADMIN) |
| GET | `/public/availability` | 공개 시간슬롯 조회 (인증 불필요) |

### Payment — `/api/v1/payments`
| Method | Path | 설명 |
|--------|------|------|
| POST | `/ready` | 결제 준비 |
| POST | `/approve` | 결제 승인 → Outbox 이벤트 |
| POST | `/{id}/cancel` | 취소 |

### Search — `/api/v1/search`
| Method | Path | 설명 |
|--------|------|------|
| GET | `/resources?keyword=` | 리소스 키워드 검색 (ES nori) |
| GET | `/facilities/nearby?lat=&lng=&radiusKm=` | 반경 시설 검색 |
| GET | `/facilities/box?topLeftLat=...` | 지도 Bounding Box 검색 |
| GET | `/hospitals/nearby?lat=&lng=&radiusKm=&specialties=&symptom=&query=&openNow=` | 병원 통합 검색 |
| GET | `/hospitals/specialties` | HIRA 진료과 20종 목록 |

### Email — `/api/v1/email`
| Method | Path | 설명 |
|--------|------|------|
| GET/POST | `/templates` | 이메일 템플릿 CRUD |
| POST | `/send` | 즉시 발송 |
| POST | `/schedule` | 예약 발송 |

---

## 병원 검색 API 상세

```
GET /api/v1/search/hospitals/nearby
  ?lat=37.5665
  &lng=126.9780
  &radiusKm=5
  &specialties=01,13          ← HIRA 코드 (쉼표 구분)
  &symptom=headache           ← 증상 ID (서버에서 코드 변환)
  &query=강남+정형외과        ← 자연어 (query 있으면 나머지 무시)
  &openNow=true
  &page=0&size=20
```

**응답 필드**
```json
{
  "facilityId": "...",        // null = 외부 병원
  "placeId": "kakao-12345",
  "name": "강남세브란스병원",
  "address": "서울 강남구...",
  "latitude": 37.49,
  "longitude": 127.01,
  "specialties": ["05"],
  "openNow": true,            // null = 운영시간 미등록
  "operatingHours": {"MON": {"open":"09:00","close":"18:00"}, ...},
  "source": "INTERNAL",       // "INTERNAL" | "KAKAO"
  "rank": 1
}
```

**우선순위**: 내부 영업중 → 내부 기타 → Kakao 외부

---

## 환경 변수

| 변수 | 설명 | 기본값 |
|------|------|--------|
| `postgreUrl` / `postgreUser` / `postgrePwd` | PostgreSQL | - |
| `mongoUri` | MongoDB URI | `mongodb://localhost:27017/reservation` |
| `redisHost` / `redisPort` | Redis | `localhost / 6379` |
| `elasticsearchUri` | Elasticsearch | `http://localhost:9200` |
| `kafkaBootstrapServers` | Kafka | `localhost:9092` |
| `jwtSecret` | JWT 서명 키 (최소 32바이트) | - |
| `googleClientId` / `googleClientSecret` | Google OAuth2 (scope: gmail.send 포함) | - |
| `kakaoClientId` / `kakaoClientSecret` | Kakao OAuth2 | - |
| `googleMapsApiKey` | Google Places 보조 검색 | 빈값 허용 |
| `kakaoRestApiKey` | Kakao Local REST API | 빈값 허용 |
| `NOTIFICATION_CHANNEL` | 알림 채널: `gmail \| kakao \| log` | `gmail` |
| `EMAIL_ADMIN_MEMBER_ID` | Gmail 발송에 쓸 관리자 memberId | `0` |
| `FRONTEND_URL` | OAuth 리다이렉트 주소 | `http://localhost:3000` |
| `kakaopayEnabled` / `kakaopaySecretKey` | KakaoPay | `false` |
| `tosspayEnabled` / `tosspaySecretKey` | TossPay | `false` |

---

## 로컬 실행

### Docker 인프라

```bash
docker compose up -d          # PostgreSQL, MongoDB, Redis, Elasticsearch
docker compose --profile kafka up -d kafka   # Kafka (선택)
```

### 백엔드 실행 예시

```bash
export postgreUrl=jdbc:postgresql://localhost:5433/reservation_db
export postgreUser=leebeanbin
export postgrePwd=
export mongoUri=mongodb://localhost:27018/reservation
export redisHost=localhost
export redisPort=6380
export elasticsearchUri=http://localhost:9200
export jwtSecret=change_this_to_at_least_32_bytes_secure_secret

./gradlew bootRun
# → http://localhost:8080
# → http://localhost:8080/swagger-ui.html
```

### 테스트

```bash
./gradlew test              # 단위 테스트
./gradlew integrationTest   # Testcontainers 통합 테스트 (Docker 필요)
```

| 테스트 | 커버리지 |
|--------|----------|
| `ReservationServiceTest` | 생성(분산락) / 확정 / 취소 / 예외 |
| `ReservationConcurrencyTest` | 동시 예약 경합 시나리오 |
| `PaymentServiceTest` | 준비 / 승인 / 취소 / Outbox 이벤트 |
| `FacilityServiceTest` | 등록 도메인 매핑 |
| `ResourceItemServiceTest` | 등록 및 ES 동기화 |
| `PaymentIntegrationTest` | 결제 통합 플로우 |
| `ReservationIntegrationTest` | 예약 통합 플로우 |

---

## Kakao 연동 체크리스트

1. Kakao Developers 콘솔 → 내 애플리케이션 → 플랫폼에 백엔드/프론트 도메인 등록
2. Redirect URI: `{BACKEND_URL}/login/oauth2/code/kakao`
3. 동의 항목: `profile_nickname`, `account_email`
4. 프론트 로그인 진입점: `{API_URL}/oauth2/authorization/kakao`

## Gmail 알림 체크리스트

1. Google Cloud Console → OAuth 동의 화면 → scope에 `gmail.send` 추가
2. 관리자 계정으로 Google 로그인 → Access Token 발급 → Redis 저장 자동화
3. `EMAIL_ADMIN_MEMBER_ID` = 발급된 관리자 memberId
4. `NOTIFICATION_CHANNEL=gmail`로 설정
