# 불법 주정차 판별·신고 도우미 — 백엔드 (Spring Boot)

환일고 「환일야호」 팀 · 2026 AI동행 프로젝트 자율탐구 출품작

사진 분석은 **브라우저 안에서** AI가 해요. 이 서버는 그 결과(숫자)만 받아서
① 유형별 안내(위험한 이유·신고 요건·신고 방법)를 돌려주고 ② 판별 기록과 피드백을 저장하고 ③ 통계를 보여 줘요.

> **개인정보 원칙**: 사진, 위치(GPS), IP처럼 사람을 알아볼 수 있는 정보는 받지도 저장하지도 않아요.
> 저장하는 것은 AI가 낸 유형·확률·모델 버전·시각·피드백뿐이에요.

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
| POST | `/api/analyze` | 사진 → 외부 AI(NVIDIA) 판별 (multipart `image`) | 200 |
| POST | `/api/judgments` | 판별 결과 저장 + 유형별 안내 | 201 |
| GET | `/api/judgments?limit=20` | 최근 기록, 최신순 (limit 1~100, 기본 20) | 200 |
| POST | `/api/judgments/{id}/feedback` | 피드백 저장 (다시 보내면 덮어써요) | 204 |
| GET | `/api/guides` | 4개 유형 안내 전체 | 200 |
| GET | `/api/guides/{type}` | 유형 하나 안내 (예: `/api/guides/HYDRANT`) | 200 |
| GET | `/api/stats` | 통계 | 200 |

유형(type, label)은 `CROSSWALK`(횡단보도), `SIDEWALK`(인도), `HYDRANT`(소화전), `NORMAL`(정상) 중 하나이고,
**대문자로 정확히** 써야 해요.

### POST /api/analyze — 외부 AI 사진 판별 (발표용 임시)

사진을 받아 NVIDIA Build API(`deepseek-ai/deepseek-v4.1-flash`)로 판별해요. **사진은 저장하지 않고 로그에도 남기지 않아요.**

- 요청: `multipart/form-data`, 필드 이름 `image`. JPG·PNG·WEBP만, 최대 5MB (파일 앞부분을 직접 보고 형식을 확인해요)
- API 키(`NVIDIA_API_KEY`): **`backend/secrets.properties`** 파일(git에 올라가지 않음) 또는 환경변수에서 읽어요.
  둘 다 있으면 환경변수가 먼저예요. 실행 방법은 [루트 README](../README.md)를 보세요.
  ```properties
  # backend/secrets.properties (예시: secrets.properties.example)
  NVIDIA_API_KEY=nvapi-여기에키
  ```
  또는 환경변수:
  ```bat
  :: cmd
  set NVIDIA_API_KEY=키값
  gradlew.bat bootRun
  ```
  ```powershell
  # PowerShell
  $env:NVIDIA_API_KEY="키값"
  .\gradlew.bat bootRun
  ```
- 확인: `curl.exe -F "image=@test.jpg" http://localhost:8080/api/analyze`

응답 200:
```json
{ "label": "CROSSWALK", "confidence": 0.86,
  "probabilities": { "CROSSWALK": 0.86, "SIDEWALK": 0.08, "HYDRANT": 0.02, "NORMAL": 0.04 },
  "reason": "차량 앞부분이 횡단보도 흰 줄 위에 걸쳐 있어요.",
  "modelVersion": "deepseek-v4.1-flash" }
```
- AI 답에 `<think>…</think>`나 ```` ```json ```` 코드펜스가 붙어도 첫 번째 JSON 객체만 꺼내요.
- 확률은 4개를 모두 채우고(없으면 0) 합이 1이 되게 맞춰요. 합이 0이면 label에 1.0을 줘요.
- label의 확률이 가장 크지 않으면 가장 큰 값과 서로 바꿔요. (화면과 `/api/judgments`가 "label = 최댓값"을 전제로 해요)
- `confidence`는 label의 확률로 다시 계산해요.
- 요청에 `chat_template_kwargs: {"thinking": false}`를 넣어 모델의 생각 과정을 꺼요. (공식 예제에는 없는 옵션)
  켜 두면 생각 글을 1만 자 넘게 써서 약 95초가 걸려 60초 제한에 걸렸고, 끄면 실측 11~33초였어요.

| 상황 | 응답 |
|---|---|
| 사진이 아니거나 5MB 초과, `image` 필드 없음 | 400 `BAD_REQUEST` |
| NVIDIA 오류·시간 초과(연결 10초, 응답 60초)·이상한 답·빈 답 | 502 `AI_UNAVAILABLE` "AI 분석에 실패했어요. 다시 시도해 주세요." |
| `NVIDIA_API_KEY`가 없음 | 503 `AI_NOT_CONFIGURED` |

- 판별 프롬프트와 호출 코드: `analyze/NvidiaVisionClient.java`, 응답 해석: `analyze/AnalysisParser.java`
- AI 답을 보고 싶으면 `--logging.level.com.hwanil.illegalcardetector.analyze=DEBUG`로 켜요. (답의 앞 500자만 남겨요)

### POST /api/judgments

요청:
```json
{
  "label": "CROSSWALK",
  "confidence": 0.92,
  "probabilities": { "CROSSWALK": 0.92, "SIDEWALK": 0.05, "HYDRANT": 0.02, "NORMAL": 0.01 },
  "modelVersion": "v1"
}
```

검사 규칙 (어기면 400):
- `label`: 4개 유형 중 하나
- `confidence`: 0 이상 1 이하
- `probabilities`: 4개 키가 정확히 있어야 하고, 각 값은 0~1, 합계는 1(±0.02).
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
    "probabilities": { "CROSSWALK": 0.92, "SIDEWALK": 0.05, "HYDRANT": 0.02, "NORMAL": 0.01 },
    "modelVersion": "v1",
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
  "byType": { "CROSSWALK": 30, "SIDEWALK": 40, "HYDRANT": 10, "NORMAL": 40 },
  "feedbackCount": 25,
  "feedbackAccuracy": 0.84
}
```
- `byType`에는 0건인 유형도 항상 4개가 모두 들어 있어요.
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
Set-Content -Encoding utf8 req.json '{"label":"CROSSWALK","confidence":0.92,"probabilities":{"CROSSWALK":0.92,"SIDEWALK":0.05,"HYDRANT":0.02,"NORMAL":0.01},"modelVersion":"v1"}'
curl.exe -X POST http://localhost:8080/api/judgments -H "Content-Type: application/json" --data "@req.json"
curl.exe http://localhost:8080/api/stats
```

---

## 3. H2 콘솔 (DB 안을 직접 보기)

기본 실행(개발용)에서는 H2 데이터베이스를 **파일**(`backend/data/illegalcar.mv.db`)로 저장해요.
그래서 서버를 껐다 켜도 기록이 남아요. 기록을 모두 지우고 싶으면 서버를 끈 뒤 `backend/data` 폴더를 지우세요.

1. 서버를 켠 상태에서 http://localhost:8080/h2-console 에 접속해요.
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
    "SIDEWALK": { ... }, "HYDRANT": { ... }, "NORMAL": { ... }
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
- 4개 유형(`CROSSWALK`, `SIDEWALK`, `HYDRANT`, `NORMAL`)이 모두 있어야 해요. 이름에 오타가 있어도 안 돼요.
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
│  ├─ ViolationType.java            # 4가지 유형
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
├─ analyze/                         # /api/analyze: 사진 → NVIDIA Build AI 판별 (발표용 임시)
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
- `GuideServiceTest`: 4개 유형 로딩, 유형이 빠지거나 필수 값이 비면 시작 실패
- `IllegalcardetectorApplicationTests`: 앱 전체 실행 (테스트용 메모리 DB 사용)
