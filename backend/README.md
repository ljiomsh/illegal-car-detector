# 길터 — 불법 주정차 판별·신고 도우미 백엔드 (Spring Boot)

환일고 「환일야호」 팀 · 2026 AI동행 프로젝트 자율탐구 출품작

이 서버는 ① 사진을 비전 LLM(Anthropic · Claude Sonnet 5.5)에 보내 5가지 유형으로 판별하고
② 유형별 안내(위험한 이유·신고 요건·신고 방법)를 돌려주고 ③ 판별 기록과 피드백을 저장하고 ④ 통계를 보여 줘요.

> **개인정보 원칙**: 사진은 판별할 때만 Anthropic(Claude)으로 보내고, 저장하지도 로그에 남기지도 않아요.
> 위치(GPS), IP처럼 사람을 알아볼 수 있는 정보는 받지 않아요.
> 저장하는 것은 AI가 낸 유형·확률·모델 이름·시각·피드백뿐이에요.

- Spring Boot 4.1.1 · Java 21 · Gradle(Groovy)
- Spring Web MVC, Spring Data JPA, Validation, H2(개발용), PostgreSQL(배포용), Lombok, springdoc-openapi 3.1.1

---

## 1. 실행 방법 (Windows)

**JDK 21**이 필요해요. PowerShell에서 `java -version`을 입력해 21이 나오는지 확인하세요.
아래 명령은 모두 `backend` 폴더에서 실행해요.

```powershell
cd backend
.\gradlew.bat bootRun      # 서버 실행 → http://localhost:8080
.\gradlew.bat test         # 테스트만 실행
.\gradlew.bat build        # 테스트 + 실행 파일(jar) 만들기 → build\libs\
```

서버가 켜지면 확인해 보세요.
- http://localhost:8080/api/health → `{"status":"ok"}`
- http://localhost:8080/swagger-ui.html → API 문서. 화면에서 바로 요청을 보내 볼 수 있어요.
- 종료: 실행 중인 터미널에서 `Ctrl + C`

### 프론트와 같이 개발하기

터미널 두 개를 열어요.

```powershell
# 터미널 1
cd backend
.\gradlew.bat bootRun

# 터미널 2
cd frontend
npm run dev
```

프론트가 진짜 서버를 쓰게 하려면 `frontend/.env.development.local` 파일을 만들고 아래 한 줄을 써요.
(파일을 바꾼 뒤에는 `npm run dev`를 다시 실행해요. 이 파일은 git에 올라가지 않아요.)

```
VITE_USE_MOCK=false
```

`frontend/vite.config.js`에 프록시가 설정돼 있어서, 프론트(5173)의 `/api` 요청이 이 서버(8080)로 전달돼요.
그래서 CORS 설정은 필요 없어요.

### 배포용으로 하나로 합치기 (서버 하나가 화면과 API를 모두 제공)

```powershell
cd frontend
npm run build                         # .env.production 때문에 항상 진짜 서버를 쓰는 빌드가 돼요
Remove-Item -Recurse -Force ..\backend\src\main\resources\static -ErrorAction SilentlyContinue
Copy-Item -Recurse dist ..\backend\src\main\resources\static

cd ..\backend
.\gradlew.bat build
java -jar build\libs\illegalcardetector-0.0.1-SNAPSHOT.jar
```

- `src/main/resources/static/`은 빌드할 때마다 새로 복사하는 폴더라서 git에 올리지 않아요(`.gitignore`).
- `/check`, `/history`, `/about`에서 새로고침해도 화면이 나오도록 `SpaForwardController`가 index.html을 보내 줘요.
  React에 새 화면 주소를 추가하면 이 컨트롤러의 목록에도 추가해 주세요.

---

## 2. API 목록

| 메서드 | 주소 | 설명 | 성공 |
|---|---|---|---|
| GET | `/api/health` | 서버 상태 `{ "status": "ok" }` | 200 |
| POST | `/api/analyze` | 사진 → 비전 LLM(Claude) 판별 (multipart `image`) | 200 |
| POST | `/api/judgments` | 판별 결과 저장 + 유형별 안내 | 201 |
| GET | `/api/judgments?limit=20` | 최근 기록, 최신순 (limit 1~100, 기본 20) | 200 |
| POST | `/api/judgments/{id}/feedback` | 피드백 저장 (다시 보내면 덮어써요) | 204 |
| GET | `/api/guides` | 5개 유형 안내 전체 | 200 |
| GET | `/api/guides/{type}` | 유형 하나 안내 (예: `/api/guides/HYDRANT`) | 200 |
| GET | `/api/stats` | 통계 | 200 |

유형(type, label)은 `CROSSWALK`(횡단보도), `SIDEWALK`(인도), `HYDRANT`(소화전), `OTHER`(기타 불법 주정차), `NORMAL`(정상) 중 하나이고,
**대문자로 정확히** 써야 해요.

### POST /api/analyze — AI 사진 판별

사진을 받아 Claude API의 비전 LLM(Claude Sonnet 5.5)으로 판별해요. (공식 Java SDK `com.anthropic:anthropic-java` 사용) **사진은 저장하지 않고 로그에도 남기지 않아요.**

#### 판별 방식: AI는 "보이는 것"을 확인하고, 유형은 서버가 규칙으로 정해요

1. AI에게 판별 기준(`VisionPrompts`)을 system 메시지로 주고, 사진을 보내요.
   기준의 틀은 같고, 횡단보도·인도를 설명하는 문장만 모델에 맞게 달라요. (`SYSTEM_FOR_CLAUDE`, `SYSTEM_FOR_NVIDIA`)
2. AI는 사진에서 확인한 것을 `checks`에 **정해진 값으로만** 적어요.

   | 키 | 값 | 뜻 |
   |---|---|---|
   | `vehicle` | `parked` · `moving` · `none` · `unclear` | 세워 둔 차 / 달리는 차 / 차 없음 / 판단 불가 |
   | `crosswalk` | `on` · `near` · `none` | 차 아래에 줄무늬 / 줄 밖이지만 약 10m 안 / 안 보이거나 10m보다 멀리 |
   | `sidewalk` | `on` · `none` | 바퀴가 연석 위 보도(색칠된 보도 포함)에 올라감 / 바퀴가 모두 차도 (울타리·볼라드 너머 차도도 none) |
   | `hydrant` | `near` · `far` · `none` | 같은 쪽 길가 약 5m 안 / 멀거나 길·울타리 건너편 / 안 보임 |
   | `noParking` | `yellow_line` · `intersection` · `bus_stop` · `school_zone` · `fire_lane` · `sign` · `none` | 노란 선, 교차로 모퉁이 5m, 버스 정류소 10m, 어린이보호구역, 소방차 전용구역, 금지 표지판 |

3. 서버(`AnalysisParser`)가 `checks`로 유형을 정해요. **위에서부터 처음 맞는 것**이에요.
   횡단보도 `on` → `CROSSWALK`, 소화전 `near` → `HYDRANT`, 인도 `on` → `SIDEWALK`,
   횡단보도 `near`(10m 안, 도로교통법 제32조)이거나 금지 표시 있음 → `OTHER`, 그 밖 → `NORMAL`
   - 소화전을 인도보다 먼저 봐요. 보도에서 찍은 소화전 사진을 AI가 "바퀴가 보도 위"로 자주 잘못 봐서예요.
4. 확신도를 보정해요.
   - AI가 고른 label이 규칙과 다르면 규칙의 유형으로 바꾸고 0.6(보통) 이하로 낮춰요.
   - 차가 없거나(`none`) 판단할 수 없으면(`unclear`) 0.3 이하로 낮춰요. → 화면이 "다시 찍기"를 안내해요.
   - 차가 달리는 중(`moving`)이거나 `checks`가 없거나 이상하면 0.6(보통) 이하로 낮춰요.

#### 설정 (`src/main/resources/application.yaml`)

```yaml
ai:
  provider: claude        # claude(기본) 또는 nvidia

claude:                   # ai.provider가 claude일 때
  model: claude-sonnet-5-5
  effort: low             # 답하기 전에 생각하는 깊이: low, medium, high (높을수록 느리고 비싸요)
  timeout: 30s            # 한 번 호출에서 기다리는 최대 시간. 넘으면 한 번 더 시도해요

nvidia:                   # ai.provider가 nvidia일 때 (예전에 쓰던 모델)
  base-url: https://integrate.api.nvidia.com/v1
  model: nvidia/nemotron-3.5-super-vl-120b-a12b
  read-timeout: 60s
  reasoning: false
```
- 응답의 `modelVersion`은 모델 이름에서 회사 이름(`/` 앞)을 빼고 20자까지만 쓴 값이에요. (예: `claude-sonnet-5-5`, `nemotron-3.5-super-v`)
- 모델을 바꾸면 `frontend/src/constants/aiInfo.js`의 화면 안내 문구도 같이 바꿔 주세요.
- Claude는 쓴 만큼 돈을 내요. 사진 한 장에 약 0.01달러(10~15원)예요. (입력 약 3,000토큰 + 답 약 300토큰, Sonnet 5.5 기준)

#### 모델 바꾸기 전후 정확도 (2026-11-14, 웹에서 모은 사진 222장)

Claude로 바꾸기 전에 쓰던 모델(Nemotron 3.5 Super VL)과 Claude Sonnet 5.5를 **같은 사진**으로 쟀어요.

**어떻게 구했나요?**
1. **사진 모으기**: 이미지 검색(Bing)에서 "횡단보도 불법주차", "인도 위 주차 차량", "소화전 앞 주차 차량",
   "버스정류장 불법주차", "노상 공영주차장 주차 차량" 같은 검색어로 뉴스·블로그 사진 후보 약 1,800장을 받았어요.
   기준을 고칠 때 썼던 예전 사진 17장과 비슷한 사진, 서로 비슷한 사진은 자동으로 뺐어요.
2. **정답 매기기**: 후보를 사람이 눈으로 보고, 한 가지 유형이 또렷한 실제 사진만 골랐어요.
   안내 그림·여러 장을 붙인 사진·큰 자막이나 동그라미 표시가 있는 사진·애매한 사진은 뺐어요. → 222장
3. **두 세트로 나누기**: 유형마다 무작위로 반씩 나눴어요. (세트 A 110장, 세트 B 112장. 서로 겹치지 않아요)

   | | 횡단보도 | 인도 | 소화전 | 기타 | 정상 | 합계 |
   |---|---|---|---|---|---|---|
   | 세트 A | 20 | 25 | 22 | 21 | 22 | 110 |
   | 세트 B | 21 | 25 | 23 | 21 | 22 | 112 |

4. **재기**: `scripts/evaluate-ai.ps1`이 사진을 앱처럼 긴 변 1024px로 줄여 `/api/analyze`에 한 장씩 보내요.
   **맞힘 = AI가 낸 유형이 정답과 같고, 확신도가 '다시 찍기'(0.4 미만)가 아님.** 정확도 = 맞힌 수 ÷ 전체 수

**결과**

| | 세트 A (110장) | 세트 B (112장) | 합계 (222장) | 한 장당 시간 |
|---|---|---|---|---|
| Nemotron 3.5 Super VL (바꾸기 전) | 95장 · 86.4% | 90장 · 80.4% | 185장 · 83.3% | 평균 2.3초 (최대 19초) |
| Claude Sonnet 5.5, 기준 문장 그대로 | 91장 · 82.7% | 87장 · 77.7% | 178장 · 80.2% | 평균 3.1초 (최대 5초) |
| **Claude Sonnet 5.5, 기준 문장을 Claude에 맞춤 (지금)** | 97장 · 88.2% | **98장 · 87.5%** | **195장 · 87.8%** | 평균 3.1초 (최대 4초) |

- 모델만 바꿨을 때는 오히려 조금 낮았어요. 기준 문장이 Super VL의 버릇(횡단보도·인도를 너무 쉽게 "위"로 봄)에 맞춰
  엄격하게 적혀 있어서, Claude는 조금만 걸친 차를 "횡단보도 근처(기타)"로 봤어요.
- 그래서 **세트 A만 보고** Claude용 기준 문장을 고쳤고(`VisionPrompts.SYSTEM_FOR_CLAUDE`: 일부만 걸쳐도 "위"),
  **고칠 때 보지 않은 세트 B**로 다시 쟀어요. 세트 B가 가장 믿을 만한 숫자예요: **80.4% → 87.5%**

유형별 맞힌 수 (세트 A+B):

| 유형 | Super VL | Claude (지금) |
|---|---|---|
| 횡단보도 (41장) | 37 · 90% | 38 · 93% |
| 인도 (50장) | 44 · 88% | 38 · 76% |
| 소화전 (45장) | 39 · 87% | 42 · 93% |
| 기타 (42장) | 22 · 52% | 35 · 83% |
| 정상 (44장) | 43 · 98% | 42 · 95% |

- Claude는 **기타 불법 주정차**(노란 선·버스 정류소·어린이보호구역 등)를 훨씬 잘 찾아요. Super VL은 이런 사진의 절반 가까이를 놓쳤어요. (주로 "정상"이나 "인도"로 봄)
- Claude는 **인도 위 주차**에 약해요. 한쪽 바퀴만 걸친 차를 "정상"으로 보거나(5장), 옆에 보이는 횡단보도·금지 표시를 먼저 봐요(7장).
- **주의**: 웹에서 고른 또렷한 사진이라 실제로 찍은 사진보다 쉬울 수 있고, 정답은 한 사람이 눈으로 매겨서 애매한 사진이 조금 섞여 있어요.
  사진 수가 유형마다 40~50장이라 2~3장 차이는 우연일 수 있어요. 같은 사진도 잴 때마다 1~2장은 달라질 수 있어요.
- 사진과 결과 표(`평가사진/세트A`, `세트B`, `결과/*.csv`, 출처는 `웹수집_출처.csv`)는 저작권 때문에 git에 올리지 않아요.

#### 예전 모델 비교 (2026-11-05, NVIDIA 모델끼리, 같은 사진 8장)

시험 사진: 차가 없는 사진 5장(정답: 다시 찍기), 울타리 너머 도로변에 줄지어 선 차(정답: 인도 아님), 횡단보도 그림, 정지선 뒤 주행 차(정답: 정상)

| 방식 | 차 없는 사진 5장 | 도로변 줄 주차 | 횡단보도 그림 | 정지선 뒤 | 시간 |
|---|---|---|---|---|---|
| 예전 프롬프트 + Nano Omni | 5장 모두 "정상 100%" | 인도(틀림) | 맞음 | 맞음 | 2~4초 |
| 새 규칙 + Nano Omni | 3장 다시 찍기 | 인도(틀림) | 맞음 | 횡단보도(보통, 틀림) | 2~5초 |
| 새 규칙 + Nano Omni + 생각 | 5장 다시 찍기 | 소화전(틀림) | 맞음 | 횡단보도(보통, 틀림) | 6~22초 |
| **새 규칙 + Super VL (기본)** | 4장 다시 찍기, 1장 보통 | 인도(틀림) | 맞음 | 횡단보도(보통, 틀림) | **1~5초** |
| 새 규칙 + Super VL + 생각 | 4장 다시 찍기, 1장 보통 | 소화전(틀림) | 맞음 | 횡단보도(보통, 틀림) | 6~30초 |

- DeepSeek V4.1 Flash는 같은 사진에 41~59초가 걸려서 60초 제한에 자주 걸렸어요.

#### 기준을 고칠 때 쓴 사진 평가 (2026-11-05, Super VL, 17장)

정답을 매긴 실제 사진(횡단보도 4, 소화전 5, 인도 1, 기타 1, 정상 1, 차 없음 5)으로 기준을 고쳐 가며 쟀어요.

| 판별 기준 | 맞힌 수 | 바뀐 점 |
|---|---|---|
| 처음 규칙 (횡단보도 → 인도 → 소화전) | 13/17 | 소화전 사진 2장을 "인도"로 봄 |
| 인도를 엄격하게 + 소화전을 인도보다 먼저 | 12/17 | 소화전 5장 모두 맞힘, 진짜 인도 사진을 놓침 |
| 인도를 "한쪽 바퀴만 걸쳐도" + 횡단보도 10m 안 = 기타 | 13/17 (2번 같은 결과) | |
| **+ 색칠된 보도도 보도 (지금 기준)** | **14/17** | 초록색 보도 사진도 맞힘. 평균 4초 |

남은 오답: 사진 앞에 소화전도 보이는 횡단보도 사진(→ 소화전), 횡단보도 바로 앞 줄 주차(→ 횡단보도), 멀리 무언가 보이는 가로수길(→ 정상)

#### 평가 다시 하기

`평가사진/<정답>/` 폴더(`CROSSWALK`, `SIDEWALK`, `HYDRANT`, `OTHER`, `NORMAL`, 차 없음은 `NOCAR`)에 사진을 넣고,
백엔드를 켠 상태에서 실행해요. 사진은 앱처럼 긴 변 1024px로 줄여서 보내요. (`평가사진`은 git에 올라가지 않아요)
```powershell
cd backend
.\scripts\evaluate-ai.ps1 -Root ..\평가사진\세트B -Port 8080 -Out 결과.csv
```
- 표 아래에 유형별 맞힌 수와 전체 정확도가 나와요. `-Out`을 주면 사진별 결과를 CSV로도 저장해요.
- 다른 모델과 비교하려면 `--args="--ai.provider=nvidia --server.port=8090"`처럼 따로 켜고 `-Port 8090`으로 재요.

- 요청: `multipart/form-data`, 필드 이름 `image`. JPG·PNG·WEBP만, 최대 5MB (파일 앞부분을 직접 보고 형식을 확인해요)
- API 키(`ANTHROPIC_API_KEY`): **`backend/secrets.properties`** 파일(git에 올라가지 않음) 또는 환경변수에서 읽어요.
  둘 다 있으면 환경변수가 먼저예요. 실행 방법은 [루트 README](../README.md)를 보세요.
  ```properties
  # backend/secrets.properties (예시: secrets.properties.example)
  ANTHROPIC_API_KEY=sk-ant-여기에키
  ```
  또는 환경변수:
  ```bat
  :: cmd
  set ANTHROPIC_API_KEY=키값
  gradlew.bat bootRun
  ```
  ```powershell
  # PowerShell
  $env:ANTHROPIC_API_KEY="키값"
  .\gradlew.bat bootRun
  ```
- 확인: `curl.exe -F "image=@test.jpg" http://localhost:8080/api/analyze`

응답 200:
```json
{ "label": "CROSSWALK", "confidence": 0.86,
  "probabilities": { "CROSSWALK": 0.86, "SIDEWALK": 0.08, "HYDRANT": 0.02, "OTHER": 0.0, "NORMAL": 0.04 },
  "reason": "차량 앞부분이 횡단보도 흰 줄 위에 걸쳐 있어요.",
  "checks": { "vehicle": "parked", "crosswalk": "on", "sidewalk": "none", "hydrant": "none", "noParking": "none" },
  "modelVersion": "claude-sonnet-5-5" }
```
- AI 답에 `<think>…</think>`나 ```` ```json ```` 코드펜스가 붙어도 첫 번째 JSON 객체만 꺼내요.
- 확률은 5개를 모두 채우고(없으면 0) 합이 1이 되게 맞춰요. 합이 0이면 label에 1.0을 줘요.
- label의 확률이 가장 크지 않으면 가장 큰 값과 서로 바꿔요. (화면과 `/api/judgments`가 "label = 최댓값"을 전제로 해요)
- `confidence`는 label의 확률로 다시 계산해요.
- **Claude로 보낼 때**: 답의 모양(JSON 스키마)을 API에 함께 보내서 정해진 키·값으로만 답하게 해요. (구조화된 출력, `output_config.format`)
  생각 깊이는 `output_config.effort`(`claude.effort`)로 정해요. Claude Sonnet 5.5는 `temperature`를 받지 않아서 보내지 않아요.
  답을 읽기 전에 멈춘 이유(`stop_reason`)를 확인해서, 거절(`refusal`)이나 길이 초과면 502로 알려요.
- **NVIDIA로 보낼 때**: `chat_template_kwargs`로 생각 과정을 켜고 꺼요(`nvidia.reasoning`). `temperature` 0.1, `max_tokens` 1024(생각을 켜면 8192)예요.

| 상황 | 응답 |
|---|---|
| 사진이 아니거나 5MB 초과, `image` 필드 없음 | 400 `BAD_REQUEST` |
| AI 오류·시간 초과(`claude.timeout`)·거절·이상한 답·빈 답 | 502 `AI_UNAVAILABLE` "AI 분석에 실패했어요. 다시 시도해 주세요." |
| 고른 AI의 API 키(`ANTHROPIC_API_KEY`)가 없음 | 503 `AI_NOT_CONFIGURED` |

- 판별 기준 글: `analyze/VisionPrompts.java`, 호출 코드: `analyze/ClaudeVisionClient.java`(기본)·`NvidiaVisionClient.java`, 응답 해석: `analyze/AnalysisParser.java`
- AI 답을 보고 싶으면 `--logging.level.com.hwanil.illegalcardetector.analyze=DEBUG`로 켜요. (답의 앞 500자만 남겨요)

### POST /api/judgments

요청:
```json
{
  "label": "CROSSWALK",
  "confidence": 0.92,
  "probabilities": { "CROSSWALK": 0.92, "SIDEWALK": 0.05, "HYDRANT": 0.02, "OTHER": 0.0, "NORMAL": 0.01 },
  "modelVersion": "claude-sonnet-5-5"
}
```

검사 규칙 (어기면 400):
- `label`: 5개 유형 중 하나
- `confidence`: 0 이상 1 이하
- `probabilities`: 5개 키가 정확히 있어야 하고, 각 값은 0~1, 합계는 1(±0.02).
  `label`의 확률이 가장 커야 하고, `confidence`와 같아야 해요(±0.001).
- `modelVersion`: 없어도 돼요. 있으면 20자 이하, 영문·숫자·`.`·`-`만 쓸 수 있어요.

응답 201:
```json
{
  "judgmentId": 42,
  "type": "CROSSWALK",
  "title": "횡단보도 위 주정차",
  "violation": true,
  "confidenceLevel": "HIGH",
  "dangerReasons": ["보행자가 차를 피해 차도로 돌아서 걸어야 해서 사고가 날 수 있어요.", "..."],
  "checklist": [{ "id": "plate", "text": "사진 2장 모두 차량 번호판이 또렷하게 보이나요?" }, "..."],
  "reportSteps": ["휴대폰에서 안전신문고 앱을 열어요.", "..."],
  "reportUrl": "https://www.safetyreport.go.kr"
}
```
- `confidenceLevel`: 0.7 이상 `HIGH`, 0.4 이상 `MEDIUM`, 그 아래 `LOW`.
  기준값은 `common/ConfidenceLevel.java`에만 있어요. DB에는 저장하지 않고 읽을 때마다 계산해요.
- LOW도 기록은 저장해요. (모델 개선 자료)

### GET /api/judgments?limit=20

```json
[
  {
    "judgmentId": 42,
    "label": "CROSSWALK",
    "confidence": 0.92,
    "confidenceLevel": "HIGH",
    "probabilities": { "CROSSWALK": 0.92, "SIDEWALK": 0.05, "HYDRANT": 0.02, "OTHER": 0.0, "NORMAL": 0.01 },
    "modelVersion": "claude-sonnet-5-5",
    "createdAt": "2026-10-16T09:12:00.123456Z",
    "feedback": { "correct": false, "actualLabel": "SIDEWALK", "createdAt": "2026-10-16T09:13:10.5Z" }
  }
]
```
- `feedback`은 피드백이 없으면 `null`이에요.
- 이 모양은 프론트 mock(`frontend/src/mocks/mockApi.js`)과 똑같아요. 기록 화면이 이 모양을 써요.

### POST /api/judgments/{id}/feedback

```json
{ "correct": false, "actualLabel": "SIDEWALK" }
```
- `correct`는 꼭 있어야 해요.
- `correct: false`일 때 `actualLabel`은 비워도 돼요(`null`). 프론트의 "잘 모르겠어요" 버튼이 `null`을 보내요.
- `correct: true`이면 `actualLabel`은 무시하고 `null`로 저장해요.
- 없는 id는 404예요.

### GET /api/stats

```json
{
  "total": 120,
  "byType": { "CROSSWALK": 30, "SIDEWALK": 40, "HYDRANT": 10, "OTHER": 5, "NORMAL": 35 },
  "feedbackCount": 25,
  "feedbackAccuracy": 0.84
}
```
- `byType`에는 0건인 유형도 항상 5개가 모두 들어 있어요.
- `feedbackAccuracy` = 맞았다는 피드백 수 ÷ 피드백 수. 피드백이 0건이면 `null`이에요.
- DB 집계 쿼리(GROUP BY, COUNT)로 계산해요.

### 에러 응답 (모든 4xx·5xx가 같은 모양)

```json
{
  "status": 400,
  "code": "VALIDATION_FAILED",
  "message": "입력값이 올바르지 않아요.",
  "fieldErrors": [{ "field": "confidence", "message": "0 이상 1 이하여야 해요." }]
}
```

| code | 상태 | 언제 |
|---|---|---|
| `VALIDATION_FAILED` | 400 | 검사 규칙 위반 (`fieldErrors`에 어느 값이 왜 틀렸는지) |
| `BAD_REQUEST` | 400 | JSON 형식이 깨졌거나, 없는 유형 이름 (예: `"crosswalk"`) |
| `NOT_FOUND` | 404 | 없는 기록 id, 없는 주소 |
| `METHOD_NOT_ALLOWED` | 405 | 지원하지 않는 요청 방식 (예: DELETE) |
| `UNSUPPORTED_MEDIA_TYPE` | 415 | JSON이 아닌 형식으로 보냈을 때 |
| `INTERNAL_ERROR` | 500 | 서버 오류. 자세한 내용은 서버 로그에만 남고 응답에는 나오지 않아요 |

### PowerShell에서 직접 요청 보내 보기

가장 쉬운 방법은 http://localhost:8080/swagger-ui.html 이에요.
`curl`을 쓸 때 PowerShell은 JSON 안의 큰따옴표를 망가뜨리니, JSON을 파일로 저장해서 보내세요.

```powershell
Set-Content -Encoding utf8 req.json '{"label":"CROSSWALK","confidence":0.92,"probabilities":{"CROSSWALK":0.92,"SIDEWALK":0.05,"HYDRANT":0.02,"OTHER":0.0,"NORMAL":0.01},"modelVersion":"claude-sonnet-5-5"}'
curl.exe -X POST http://localhost:8080/api/judgments -H "Content-Type: application/json" --data "@req.json"
curl.exe http://localhost:8080/api/stats
```

---

## 3. H2 콘솔 (DB 안을 직접 보기)

기본 실행(개발용)에서는 H2 데이터베이스를 **파일**(`backend/data/illegalcar.mv.db`)로 저장해요.
그래서 서버를 껐다 켜도 기록이 남아요. 기록을 모두 지우고 싶으면 서버를 끈 뒤 `backend/data` 폴더를 지우세요.

> ⚠ H2 콘솔은 **기본으로 꺼져 있어요.** Cloudflare 터널처럼 서버를 인터넷에 공개하면
> 누구나 DB 화면에 들어올 수 있어서 위험하기 때문이에요. **켤 때는 터널을 꼭 꺼 두세요.**

1. 서버를 H2 콘솔을 켠 채로 실행해요.
   ```powershell
   .\gradlew.bat bootRun --args="--spring.h2.console.enabled=true"
   ```
   http://localhost:8080/h2-console 에 접속해요.
2. 아래처럼 입력하고 Connect를 눌러요.
   - JDBC URL: `jdbc:h2:file:./data/illegalcar`
   - User Name: `sa`
   - Password: (비워 두기)
3. 예: `SELECT * FROM JUDGMENT ORDER BY ID DESC;`

`backend/data/`는 git에 올라가지 않아요(`.gitignore`).

---

## 4. PostgreSQL(Supabase) 프로필로 바꾸기

배포할 때는 Supabase 같은 PostgreSQL을 쓸 수 있어요. 접속 정보는 **환경변수로만** 넣어요.
`application.yaml`이나 다른 파일에 비밀번호를 쓰면 안 돼요. git에 올라가면 누구나 볼 수 있어요.

1. Supabase 대시보드의 **Connect** 메뉴에서 접속 정보(호스트, 포트, DB 이름, 사용자, 비밀번호)를 확인해요.
2. PowerShell에서 환경변수를 넣고 `postgres` 프로필로 실행해요.
   ```powershell
   $env:DB_URL = "jdbc:postgresql://<호스트>:5432/postgres?sslmode=require"
   $env:DB_USERNAME = "<사용자>"
   $env:DB_PASSWORD = "<비밀번호>"
   .\gradlew.bat bootRun --args="--spring.profiles.active=postgres"
   ```
   - 주소 앞에 꼭 `jdbc:postgresql://`을 붙여요.
   - jar로 실행할 때는 `java -jar build\libs\illegalcardetector-0.0.1-SNAPSHOT.jar --spring.profiles.active=postgres`
3. 처음 켜면 `judgment` 테이블이 자동으로 만들어져요(`ddl-auto: update`).
4. `postgres` 프로필에서는 H2 콘솔이 꺼져요.

> PostgreSQL 연결은 아직 실제 Supabase로 시험해 보지 않았어요. 처음 연결할 때 로그를 꼭 확인하세요.

---

## 5. 안내 문구 고치기 (`guides.json`) — 기획 담당용

안내 문구는 `backend/src/main/resources/guides.json`에 있어요. 서버가 켜질 때 한 번 읽어요.

```jsonc
{
  "_comment": ["설명 (서버는 읽지 않아요)"],
  "reportUrl": "https://www.safetyreport.go.kr",   // "안전신문고로 이동" 버튼 주소
  "guides": {
    "CROSSWALK": {
      "title": "횡단보도 위 주정차",                  // 결과 화면 제목
      "violation": true,                              // 불법이면 true, 정상 주차(NORMAL)만 false
      "dangerReasons": ["...", "..."],                // "왜 위험한가요?" 목록
      "checklist": [{ "id": "plate", "text": "..." }], // 신고 전 확인 목록 (id는 겹치지 않는 영어 이름)
      "reportSteps": ["...", "..."]                   // 신고 방법 단계
    },
    "SIDEWALK": { ... }, "HYDRANT": { ... }, "OTHER": { ... }, "NORMAL": { ... }
  }
}
```
(실제 파일에는 `//` 설명을 쓰면 안 돼요. JSON은 주석을 허용하지 않아요.)

**고치는 순서**
1. 따옴표 `"` 안의 글자만 바꿔요. 항목을 추가할 때는 앞 항목 뒤에 쉼표 `,`를 붙여요.
2. 저장한 뒤 서버를 다시 켜요(`Ctrl + C` → `.\gradlew.bat bootRun`).
3. **`frontend/src/mocks/guides.js`도 똑같이 고쳐요.** 서버가 안 될 때 화면이 이 파일의 문구를 대신 보여 줘요.
4. `.\gradlew.bat test`로 확인하면 더 안전해요.

**서버가 켜지지 않으면** 로그에 `guides.json에 문제가 있어요: ...`가 나와요. 무엇이 문제인지 적혀 있어요.
- 5개 유형(`CROSSWALK`, `SIDEWALK`, `HYDRANT`, `OTHER`, `NORMAL`)이 모두 있어야 해요. 이름에 오타가 있어도 안 돼요.
- `title`, `violation`, `dangerReasons`는 비우면 안 돼요.
- 위반 유형(`violation: true`)은 `checklist`와 `reportSteps`도 비우면 안 돼요.
- 쉼표나 따옴표가 빠지면 `guides.json을 읽지 못했어요`가 나와요.

> ⚠ 제출 전 안전신문고 공지·도로교통법으로 문구를 다시 확인하세요.

---

## 6. 폴더 구조

```
src/main/java/com/hwanil/illegalcardetector/
├─ IllegalcardetectorApplication.java
├─ common/
│  ├─ ViolationType.java            # 5가지 유형
│  ├─ ConfidenceLevel.java          # HIGH/MEDIUM/LOW 기준 (0.7, 0.4)
│  ├─ ErrorResponse.java            # 에러 응답 모양
│  ├─ NotFoundException.java        # 404용 예외
│  └─ GlobalExceptionHandler.java   # 모든 예외 → ErrorResponse
├─ config/SpaForwardController.java # /check 등 새로고침 → index.html
├─ guide/                           # guides.json 로딩·검사, /api/guides
├─ judgment/
│  ├─ Judgment.java                 # DB 테이블 judgment
│  ├─ JudgmentRepository.java       # DB 조회 (집계 쿼리 포함)
│  ├─ LabelCount.java               # 유형별 개수 집계 결과
│  ├─ JudgmentService.java
│  ├─ JudgmentController.java
│  └─ dto/                          # 요청·응답 모양 (record)
│     ├─ JudgmentRequest.java, ValidProbabilities.java, ProbabilitiesValidator.java (확률 검사)
│     └─ JudgmentResponse.java, JudgmentSummary.java, FeedbackRequest.java
├─ analyze/                         # /api/analyze: 사진 → 비전 LLM(Claude) 판별
│  ├─ AnalyzeController.java        # 파일 검사 (형식·크기)
│  ├─ AnalyzeService.java           # 판별 흐름, ai.provider에 맞는 AI 고르기
│  ├─ VisionClient.java             # AI 창구의 공통 모양 (인터페이스)
│  ├─ VisionPrompts.java            # 판별 기준 글 (모델별 문장 포함)
│  ├─ ClaudeVisionClient.java       # Claude API 호출 (기본), 답의 JSON 스키마
│  ├─ NvidiaVisionClient.java       # NVIDIA API 호출 (ai.provider: nvidia)
│  └─ AnalysisParser.java           # AI 답에서 JSON 꺼내기·확률 보정
├─ stats/                           # /api/stats
└─ health/HealthController.java     # /api/health
src/main/resources/
├─ application.yaml                 # 기본(H2 파일) + postgres 프로필
└─ guides.json                      # 유형별 안내 문구
```

### 테스트 (`.\gradlew.bat test`)
- `ConfidenceLevelTest`: 0.4, 0.7 경계값
- `JudgmentControllerTest`(@WebMvcTest)
  - 정상 저장 201
  - 검증 실패 400: 확신도 범위, 확률 키 누락, 합계 오류, 최댓값·확신도 불일치, 잘못된 label, modelVersion 형식, 깨진 JSON, limit 범위
  - 피드백 204·404, 500일 때 내부 메시지 숨김
- `StatsQueryTest`(@DataJpaTest): 0건 유형 포함 집계, 피드백 정확도, 피드백 덮어쓰기
- `GuideServiceTest`: 5개 유형 로딩, 유형이 빠지거나 필수 값이 비면 시작 실패
- `AnalyzeControllerTest`(@WebMvcTest): 정상 판별 200, 사진이 아니면 400, AI 실패 502, 키 없음 503
- `AnalysisParserTest`: 생각 과정·코드펜스 제거, 확률 정규화, checks 규칙(우선순위, label이 어긋나면 규칙 유형·보통, 차 없으면 0.3 이하, 달리는 차·checks 없음은 0.6 이하)
- `VisionClientTest`: 모델 이름 → 판별 기록용 `modelVersion` (회사 이름 빼기, 20자 제한)
- `AnalyzeServiceTest`: `ai.provider`에 맞는 AI 고르기, 키가 없으면 503
- `ClaudeVisionClientTest`: 키 유무, 생각 깊이 값, 답의 JSON 스키마에 checks·유형이 모두 있는지
- `VisionPromptsTest`: 모델별 판별 기준에 빈칸이 남지 않았는지
- `IllegalcardetectorApplicationTests`: 앱 전체 실행 (테스트용 메모리 DB 사용)
