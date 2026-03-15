# MariBean 테스트 시나리오

## 내가 할 수 없는 영역 (수동 확인 필요)

| 항목 | 이유 | 확인 방법 |
|------|------|-----------|
| OAuth2 로그인 (Google/Kakao) | 브라우저 리다이렉트 필요 | 브라우저에서 직접 로그인 |
| Ollama 모델 다운로드 완료 | 3~30GB, 수 분~수십 분 소요 | `docker logs -f reservation-ollama-init` |
| Tavily API 응답 | 외부 API — 키 유효성 확인 | IntelliJ 환경변수에 `TAVILY_API_KEY` 설정 후 `[6-3]` 실행 |
| Kakao/Google Map API | 외부 API 키 필요 | IntelliJ 환경변수에 설정 후 `[10-1]` 실행 |
| Gmail 발송 | OAuth2 토큰 + 관리자 계정 필요 | 관리자 계정으로 로그인 후 토큰 획득 |
| Spring Boot 실행 | IntelliJ에서 직접 실행 | Run > ReservationApplication |

---

## 인프라 기동 순서

```bash
# 1. 핵심 인프라 (Kafka 없이)
docker compose up -d postgres mongo redis elasticsearch ollama

# 2. Ollama 모델 다운로드 상태 확인 (완료까지 대기)
docker logs -f reservation-ollama-init
# "[ollama-init] 모델 준비 완료" 출력 확인

# 3. Kafka 포함 시 (선택)
docker compose --profile kafka up -d kafka

# 4. 전체 상태 확인
docker compose ps
```

### 헬스체크 기준

| 서비스 | 확인 명령 | 정상 상태 |
|--------|-----------|-----------|
| PostgreSQL | `docker exec reservation-postgres pg_isready` | `accepting connections` |
| MongoDB | `docker exec reservation-mongo mongosh --eval "db.adminCommand('ping')"` | `{ ok: 1 }` |
| Redis | `docker exec reservation-redis redis-cli ping` | `PONG` |
| Elasticsearch | `curl http://localhost:9200/_cluster/health` | `"status":"green"` or `"yellow"` |
| Ollama | `curl http://localhost:11434/api/tags` | models 목록에 qwen2.5:7b, nomic-embed-text, llava:7b |

---

## 자동화 테스트

### Unit Tests (Docker 불필요)

```bash
./gradlew test
```

**포함 테스트 (58개 메서드)**

| 클래스 | 설명 |
|--------|------|
| `ElasticsearchSyncServiceTest` | 임베딩 성공/실패/Outbox 등록 |
| `SearchQueryAnalyzerServiceTest` | LLM 키워드 추출 + fallback |
| `HybridSearchServiceTest` | BM25 + kNN RRF 병합 |
| `TavilyWebSearchServiceTest` | API 호출 + 빈 키 graceful |
| `AISummaryServiceTest` | 인용 포함 요약 생성 + fallback |
| `UserPlaceMemoServiceTest` | 메모 CRUD + Redis 캐시 |
| `AISearchRankerTest` | 메모·트렌딩·클릭 boost 계산 |
| `FacilityEmbeddingBatchServiceTest` | 배치 임베딩 + 부분 업데이트 |
| `AiEmbeddingConsumerTest` | Kafka consumer + @RetryableTopic |
| `AIResearchOrchestratorTest` | 병렬 실행 + 캐시 + 이벤트 발행 |
| `AIResearchControllerTest` | REST + SSE + 클릭 API |
| `UserPlaceMemoControllerTest` | 메모 CRUD REST |
| `UserPlaceMemoTest` | 도메인 모델 boost 범위 [0,5] |

### Integration Tests (Docker 필요 — Testcontainers 자동 기동)

```bash
./gradlew integrationTest
```

> Testcontainers가 PostgreSQL, MongoDB, Redis, Kafka, Elasticsearch를 자동으로 별도 컨테이너로 띄워 테스트합니다.
> Ollama는 Mock으로 대체 (`spring.ai.ollama.base-url=http://localhost:11434` — 실제 연결 없음).

---

## E2E 테스트 시나리오 (api-test.http 순서)

### 시나리오 1: 기본 예약 흐름

```
로그인 → 시설 조회[2-1] → 리소스 조회[3-2] → 가용시간 확인[4-1]
→ 예약 생성[4-2] → 결제 준비[5-1] → 결제 승인[5-3]
→ 예약 상태 CONFIRMED 확인[4-4]
```

**검증 포인트**
- 예약 생성 시 동일 시간대 중복 예약 → `SEAT_ALREADY_BOOKED (R001)`
- 결제 금액 조작 시 → `PAYMENT_AMOUNT_MISMATCH (P006)` (테스트 [5-2])
- 결제 중복 시 → `PAYMENT_ALREADY_EXISTS (P005)`

### 시나리오 2: AI 검색 흐름

```
기본 키워드 검색[6-1] → 병원 검색[6-2]
→ AI 리서치 검색[6-3] (Ollama 필요)
→ SSE 스트리밍 확인 (curl 명령)
→ Vision 이미지 검색[7-1] (llava:7b 필요)
→ 메모 저장[8-1] → 재검색 후 상위 노출 확인[8-2]
→ 클릭 피드백[9-1] → Redis ZSet 확인
```

**검증 포인트**
- Ollama 오프라인 시 → 키워드 기반 fallback 동작 확인
- Tavily 키 없을 때 → `WebSearchResult.empty()` graceful
- SSRF 테스트 → `[7-2]`, `[7-3]` 모두 400 응답 확인

### 시나리오 3: 지도 API 흐름

```
Kakao 장소 검색[10-1] (kakaoRestApiKey 필요)
→ 검색 결과에서 placeId 획득
→ 시설 생성[2-3]에 placeId 포함
→ 주변 검색[10-2]
→ 경로 안내[10-3] (KakaoMobility)
```

**검증 포인트**
- `kakaoRestApiKey` 미설정 → 빈 결과 (graceful, 500 아님)
- `googleMapsApiKey` 미설정 → Google Places 사진 fallback → Tavily 웹 이미지 사용

### 시나리오 4: 동시성 + Rate Limiting

```bash
# Redis 분산 락 테스트 — 동일 시간대 동시 예약 시도
for i in {1..5}; do
  curl -s -X POST http://localhost:8080/api/v1/reservations \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"resourceItemId":"'$RESOURCE_ID'","startTime":"2026-03-20T10:00:00","endTime":"2026-03-20T11:00:00"}' &
done
wait
# 결과: 1개만 성공(201), 나머지 409 SEAT_ALREADY_BOOKED
```

```bash
# Rate Limiting 테스트 — 검색 API 빠른 반복
for i in {1..20}; do
  curl -s -o /dev/null -w "%{http_code}\n" \
    "http://localhost:8080/api/v1/search?query=내과&lat=37.4983&lng=127.0279"
done
# 결과: 앞 N개 200, 이후 429 TooManyRequests
```

### 시나리오 5: Kafka + Outbox 이벤트 흐름

```
결제 승인[5-3] → Outbox 테이블 확인 → Kafka 발행(30s 스케줄러) → 알림 전송
```

**PostgreSQL 확인:**
```sql
-- Outbox 이벤트 발행 확인
SELECT aggregate_type, aggregate_id, event_type, status, created_at
FROM outbox_events
ORDER BY created_at DESC
LIMIT 10;

-- AI 임베딩 이벤트
SELECT * FROM outbox_events WHERE aggregate_type = 'AI_EMBEDDING';
```

**Redis 확인:**
```bash
# 검색 이력
redis-cli -p 6380 zrange "search:history:1" 0 -1 WITHSCORES

# 인기 키워드
redis-cli -p 6380 zrange "map:analytics:popular:$(date +%Y%m%d)" 0 9 WITHSCORES REV

# 클릭 선호도
redis-cli -p 6380 zscore "user:pref:1" "test-place-001"

# AI 검색 캐시
redis-cli -p 6380 keys "ai_search:*"
```

---

## Ollama 모델 확인 및 직접 테스트

```bash
# 모델 목록 확인
curl http://localhost:11434/api/tags | python3 -m json.tool | grep '"name"'

# qwen2.5:7b 텍스트 생성 테스트
curl -X POST http://localhost:11434/api/generate \
  -H "Content-Type: application/json" \
  -d '{"model":"qwen2.5:7b","prompt":"강남구 내과의원을 한 줄로 소개해줘","stream":false}' \
  | python3 -m json.tool | grep '"response"'

# nomic-embed-text 임베딩 테스트 (768차원 벡터)
curl -X POST http://localhost:11434/api/embeddings \
  -H "Content-Type: application/json" \
  -d '{"model":"nomic-embed-text","prompt":"강남 내과"}' \
  | python3 -c "import sys,json; d=json.load(sys.stdin); e=d['embedding']; print(f'dims={len(e)}, sample={e[:3]}')"
# 기대: dims=768, sample=[0.xxx, 0.xxx, 0.xxx]
```

---

## ES 인덱스 확인

```bash
# 인덱스 목록
curl http://localhost:9200/_cat/indices?v

# facilities 인덱스 매핑 확인 (embedding dense_vector 필드)
curl http://localhost:9200/facilities/_mapping | python3 -m json.tool | grep -A5 "embedding"

# 시설 문서 수
curl http://localhost:9200/facilities/_count

# kNN 테스트 쿼리 (임베딩 재색인 후)
curl -X POST http://localhost:9200/facilities/_search \
  -H "Content-Type: application/json" \
  -d '{
    "query": { "match": { "name": "내과" } },
    "_source": ["name","category","address"],
    "size": 3
  }' | python3 -m json.tool
```

---

## 자동화 체크리스트

```
인프라
[ ] docker compose ps — 5개 컨테이너 모두 healthy/running
[ ] Ollama 모델 3개 준비 (qwen2.5:7b, nomic-embed-text, llava:7b)
[ ] ES 클러스터 green/yellow
[ ] /actuator/health → UP

단위 테스트
[ ] ./gradlew test → BUILD SUCCESSFUL (58 tests)

통합 테스트
[ ] ./gradlew integrationTest → BUILD SUCCESSFUL

API 동작
[ ] [2-1] 시설 목록 200
[ ] [6-1] 키워드 검색 200
[ ] [6-3] AI 리서치 검색 200 (aiSummary 포함)
[ ] [7-1] Vision 검색 200 (suggestedQuery 한국어)
[ ] [7-2] SSRF 차단 400
[ ] [5-2] 금액 조작 차단 P006
[ ] [10-1] Kakao 장소 검색 200
[ ] [10-3] 경로 안내 200

보안
[ ] 미인증 /api/v1/search/memo POST → 401
[ ] /api/agent/chat 미인증 → 401
[ ] XSS: javascript: URL citation → 링크 미생성 (프론트 확인)
```
