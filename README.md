# MariBean — AI 기반 공간·의료 예약 플랫폼

Spring Boot 3 + Next.js 16 풀스택 예약 플랫폼.
Ollama 로컬 LLM(qwen2.5:7b · llava:7b · nomic-embed-text)이 Tavily 웹 검색 결과를 종합해
**인용 출처가 붙은 AI 요약**과 함께 위치 기반 시설·병원을 추천합니다.

---

## 기술 스택

| 구분 | 기술 |
|------|------|
| Language | Java 17 (Azul Zulu) |
| Framework | Spring Boot 3.3.5 |
| AI / LLM | Spring AI 1.0.0 + Ollama (qwen2.5:7b · llava:7b · nomic-embed-text) |
| 웹 검색 | Tavily API (필수) |
| Auth | Spring Security + OAuth2 (Google, Kakao) + JWT |
| RDB | PostgreSQL 16 + Spring Data JPA + QueryDSL |
| Document DB | MongoDB 7.0 |
| Cache / Lock | Redis 7 + Redisson (분산락) |
| Search | Elasticsearch 8.x + nori 형태소 + kNN 벡터 검색 |
| Message Queue | Kafka + Transactional Outbox Pattern |
| 알림 | Gmail API (OAuth2) |
| 지도 | Kakao Local API + Google Places API |
| Rate Limiting | Bucket4j |
| API Docs | Springdoc OpenAPI (Swagger UI) |
| Frontend | Next.js 16 (App Router) + React 19 + TypeScript + Tailwind CSS 4 |

---

## 아키텍처

```
사용자 쿼리 (텍스트 or 이미지)
        │
        ▼
┌────────────────────────────────────────────────┐
│            AIResearchOrchestrator              │
│                                                │
│  1. 쿼리 분석 (qwen2.5:7b + Redis 이력)        │
│  2. 병렬 실행 ─────────────────────────────    │
│     ├─ HybridSearchService (ES kNN + BM25)     │
│     └─ TavilyWebSearchService (웹 검색)        │
│  3. 사진 Enrichment (Kakao + Google Places)    │
│  4. 개인 메모 조회 (Redis / PostgreSQL)        │
│  5. AI 종합 요약 + 인용 생성 (qwen2.5:7b)     │
│  6. 랭킹 (score + 메모 boost + 트렌딩 boost)  │
│  7. Redis 캐시 90s + Kafka 이벤트 발행        │
└────────────────────────────────────────────────┘
        │
        ▼
AIResearchResult {
  query, aiSummary, citations[],    ← Perplexity 스타일
  results[]: {
    name, photos[], rating,          ← Notion 카드
    webSnippet, webUrl,
    userMemo, memoHighlighted,       ← 개인 메모 (Antigravity)
    score, highlighted
  }
}
```

### 패키지 구조 (DDD · Hexagonal)

```
com.mariabean.reservation
├── global/        # 전역 설정·예외·보안·응답 래퍼
├── auth/          # JWT, OAuth2 (Google/Kakao)
├── member/        # 회원 도메인
├── facility/      # 시설·리소스·지도 도메인
│   ├── api/
│   ├── application/   # FacilityService, MapService, SymptomSpecialtyMapping
│   ├── domain/
│   └── infrastructure/
│       ├── external/map/  # KakaoLocalSearchClient, GoogleMapClient
│       └── persistence/
├── reservation/   # 예약 (Redisson 분산락, 만료 스케줄러)
├── payment/       # 결제 (KakaoPay, TossPay)
├── search/        # AI 검색 도메인
│   ├── api/           # AIResearchController, VisionSearchController, UserPlaceMemoController
│   ├── application/   # AIResearchOrchestrator, HybridSearchService, TavilyWebSearchService
│   │                  # AISummaryService, VisionLocationAnalyzerService, AISearchRanker
│   │                  # SearchQueryAnalyzerService, UserPlaceMemoService
│   ├── domain/        # UserPlaceMemo
│   └── infrastructure/
│       └── persistence/  # FacilitySearchDocument (embedding 768-dim)
├── notification/  # Gmail 알림
├── email/         # 이메일 템플릿·예약 발송
└── event/         # Kafka + Transactional Outbox
```

---

## 주요 기능

### 1. AI 리서치 검색 (Perplexity 스타일)
- **ES Hybrid Search**: BM25(키워드) + kNN(nomic-embed-text 768-dim 벡터) → Reciprocal Rank Fusion
- **Tavily 웹 검색**: 모든 위치 기반 검색에 실시간 웹 결과 포함 (필수)
- **qwen2.5:7b 종합 요약**: 내부 DB + 웹 결과를 종합해 `[1]` 형식의 인용 번호가 붙은 요약 생성
- **Redis 캐시** 90s, 검색 이력 기반 쿼리 개인화

### 2. Vision 검색 (이미지 → 장소)
- `llava:7b`가 업로드 이미지 또는 HTTPS URL을 분석 → 한국어 검색어 + 랜드마크 추출
- SSRF 방지: HTTPS 전용, 사설 IP 차단, DNS rebinding 방지, 이미지 확장자 허용 목록

### 3. 개인 메모 + 재랭킹 (Antigravity 스타일)
- 검색 결과 각 장소에 개인 메모 작성 → PostgreSQL 저장 + Redis 캐시 (TTL 10분)
- `finalScore = aiScore + (boostScore / 5.0) × 0.3` → 메모 장소 자동 상위 노출
- 클릭 피드백 → `user:pref:{memberId}` Redis ZSet → 최대 +0.15 boost

### 4. 병원 지능형 검색
- HIRA 표준 진료과 코드 20종 + 증상 → 진료과 코드 매핑 (`두통` → `02,01`)
- Kakao Local 통합: 미등록 외부 병원까지 합산, placeId 기준 중복 제거
- `openNow` 필터: MongoDB `operatingHours` KST 비교

### 5. 동시성 · 신뢰성
- Redisson 분산락: `reservation:lock:{resourceItemId}` 중복 예약 방지
- Transactional Outbox: 결제·예약·임베딩 작업을 동일 트랜잭션으로 저장 → Kafka 발행
- `@RetryableTopic` (3회) AI 임베딩 컨슈머

### 6. Gmail 알림
- Google OAuth2 Access Token → Redis 저장 → `GmailEmailSender`
- `ScheduledEmailProcessor` 60초 주기 예약 발송

---

## API 엔드포인트

### AI 검색 — `/api/v1/search`
| Method | Path | 설명 |
|--------|------|------|
| GET | `/research?query=&lat=&lng=` | AI 리서치 검색 (ES kNN + Tavily + qwen2.5) |
| GET | `/research/stream` | SSE 스트리밍 (initial → enriched → summary) |
| POST | `/research/click` | 클릭 피드백 (Redis 선호도 학습) |
| POST | `/vision` | 이미지 파일 업로드 → 장소 분석 |
| POST | `/vision/url` | 이미지 URL → 장소 분석 |
| POST | `/memo` | 개인 메모 저장 |
| PUT | `/memo/{id}` | 메모 수정 |
| DELETE | `/memo/{id}` | 메모 삭제 |
| GET | `/memo?placeId=` | 장소별 메모 조회 |
| GET | `/hospitals/nearby` | 병원 통합 검색 (HIRA + Kakao) |
| GET | `/hospitals/specialties` | HIRA 진료과 20종 목록 |
| GET | `/facilities/nearby` | 반경 시설 검색 |
| GET | `/facilities/box` | 지도 Bounding Box 검색 |

**AI 검색 응답 예시**
```json
{
  "query": "강남 내과",
  "aiSummary": "강남구 내과는 총 12개가 검색되었습니다. 강남내과의원[1]은 평점 4.5로...",
  "citations": [{"number": 1, "title": "강남내과의원", "url": "https://..."}],
  "results": [{
    "name": "강남내과의원",
    "photos": ["https://..."],
    "rating": 4.5,
    "tags": ["주차가능", "당일예약"],
    "webSnippet": "강남역 2번 출구 도보 3분...",
    "userMemo": "친절하고 대기 짧음",
    "memoHighlighted": true,
    "score": 0.91,
    "distanceMeters": 320,
    "openNow": true
  }]
}
```

### 기타 엔드포인트
| 도메인 | 주요 경로 |
|--------|---------|
| Auth | `POST /api/v1/auth/refresh`, `POST /api/v1/auth/logout` |
| Facility | `CRUD /api/v1/facilities`, `GET /places/search?query=` |
| Resource | `CRUD /api/v1/resources` |
| Reservation | `POST /`, `GET /my`, `POST /{id}/cancel`, `GET /public/availability` |
| Payment | `POST /ready`, `POST /approve`, `POST /{id}/cancel` |
| Email | `CRUD /api/v1/email/templates`, `POST /send`, `POST /schedule` |
| Admin | `GET /api/v1/admin/search/reindex` (ES 임베딩 일괄 생성) |

---

## 로컬 실행

### 1. 인프라 기동 (Docker)

```bash
# 핵심 인프라 + Ollama (모델 자동 다운로드: ~15GB, 수십 분 소요)
docker compose up -d postgres mongo redis elasticsearch ollama

# 모델 다운로드 완료 확인
docker logs -f reservation-ollama-init
# "[ollama-init] 모델 준비 완료" 출력 후 다음 단계

# Kafka 포함 시 (선택)
docker compose --profile kafka up -d kafka
```

**Ollama 모델 구성**
| 역할 | 모델 |
|------|------|
| 텍스트 분석·요약 | `qwen2.5:7b` |
| 임베딩 (768-dim) | `nomic-embed-text` |
| 이미지 분석 | `llava:7b` |

### 2. IntelliJ 환경 변수

Run Configuration → Environment Variables에 아래 설정:

```
# 인프라
postgreUrl=jdbc:postgresql://localhost:5433/reservation_db
postgreUser=leebeanbin
postgrePwd=
mongoUri=mongodb://localhost:27018/reservation
redisHost=localhost
redisPort=6380
elasticsearchUri=http://localhost:9200
kafkaBootstrapServers=localhost:9092

# AI
ollamaBaseUrl=http://localhost:11434
TAVILY_API_KEY=tvly-...           ← 필수: tavily.com에서 발급

# Auth
jwtSecret=change_this_to_at_least_32_bytes_secure_secret
googleClientId=...
googleClientSecret=...
kakaoClientId=...
kakaoClientSecret=...

# 지도
kakaoRestApiKey=...
googleMapsApiKey=...

# Gmail 알림
NOTIFICATION_CHANNEL=gmail
EMAIL_ADMIN_MEMBER_ID=1

# 결제 (선택)
kakaopayEnabled=false
tosspayEnabled=false
```

### 3. 앱 실행 순서

```bash
# 1. IntelliJ에서 ReservationApplication 실행
# 2. Swagger: http://localhost:8080/swagger-ui.html
# 3. Health: http://localhost:8080/actuator/health

# 4. ES 임베딩 일괄 생성 (최초 1회, 로그인 후)
curl -X GET http://localhost:8080/api/v1/admin/search/reindex \
  -H "Authorization: Bearer {admin-jwt}"

# 5. Gmail 토큰 저장 (관리자 계정 로그인 후 토큰 획득)
POST /api/v1/admin/gmail/token
{"accessToken":"...", "refreshToken":"..."}
```

### 4. 테스트

```bash
./gradlew test              # 단위 테스트 (102개, Docker 불필요)
./gradlew integrationTest   # Testcontainers 통합 테스트 (Docker 필요)
```

---

## 환경 변수 요약

| 변수 | 필수 | 설명 |
|------|------|------|
| `postgreUrl` / `postgreUser` / `postgrePwd` | ✅ | PostgreSQL |
| `mongoUri` | ✅ | MongoDB |
| `redisHost` / `redisPort` | ✅ | Redis |
| `elasticsearchUri` | ✅ | Elasticsearch |
| `jwtSecret` | ✅ | JWT 서명 키 (32바이트 이상) |
| `TAVILY_API_KEY` | ✅ | Tavily 웹 검색 (tavily.com) |
| `googleClientId` / `googleClientSecret` | ✅ | Google OAuth2 (gmail.send 스코프 포함) |
| `kakaoClientId` / `kakaoClientSecret` | ✅ | Kakao OAuth2 |
| `ollamaBaseUrl` | ✅ | Ollama 서버 주소 |
| `kakaoRestApiKey` | ⚠️ | Kakao Local 검색 (없으면 외부 병원 검색 불가) |
| `googleMapsApiKey` | ⚠️ | Google Places 사진 보완 (없으면 Tavily 이미지 사용) |
| `NOTIFICATION_CHANNEL` | - | `gmail`(기본) \| `kakao` \| `log` |
| `EMAIL_ADMIN_MEMBER_ID` | - | Gmail 발송용 관리자 memberId |
| `kafkaBootstrapServers` | - | Kafka (없으면 Outbox 이벤트 미발행) |
| `kakaopaySecretKey` / `tosspaySecretKey` | - | 결제 PG |

---

## CI

GitHub Actions (`.github/workflows/ci.yml`):
- PR / push to `main` → `./gradlew test` (단위 테스트)
- 테스트 리포트 artifact 업로드
