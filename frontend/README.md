# 길터 — 불법 주정차 판별·신고 도우미 (프론트엔드)

환일고 「환일야호」 팀 · 2026 AI동행 프로젝트 자율탐구 출품작

주차된 차와 주변이 함께 나온 사진을 올리면 AI가 아래 5가지 중 하나로 판별하고,
위험한 이유 · 신고 요건 체크리스트 · 안전신문고 신고 방법을 안내하는 웹 서비스예요.

| 라벨 | 의미 |
|---|---|
| `CROSSWALK` | 🚸 횡단보도 위 주정차 |
| `SIDEWALK` | 🚶 인도 위 주정차 |
| `HYDRANT` | 🧯 소화전 주변 주정차 |
| `OTHER` | 🚫 기타 불법 주정차 (교차로 모퉁이, 버스 정류소, 어린이보호구역 정문 앞 등) |
| `NORMAL` | ✅ 정상 주차 |

> 사진 판별은 **서버(`../backend`)의 비전 LLM(Anthropic · Claude Sonnet 5.5)** 이 해요.
> 화면은 사진을 줄여서 `/api/analyze`로 보내고, 받은 결과로 안내 화면을 보여 줘요.

---

## 1. 실행 방법

Node.js 20 이상이 필요해요.

```bash
npm install      # 처음 한 번만
npm run dev      # 개발 서버 실행 → 터미널에 나온 주소(http://localhost:5173)로 접속
npm run lint     # 코드 검사 (oxlint)
npm run build    # 배포용 파일 만들기 → dist/ 폴더
npm run preview  # 만든 배포용 파일을 미리 보기
```

사진 판별에는 **백엔드가 꼭 켜져 있어야 해요.** 백엔드는 `ANTHROPIC_API_KEY`를 넣고 켜요.
(자세한 순서는 [루트 README](../README.md))
`vite.config.js`의 프록시가 `/api` 요청을 `http://localhost:8080`으로 넘겨줘요.

### 판별 기록을 어디에 저장할지

판별 기록·통계는 기본으로 **가짜 서버(mock, 브라우저 localStorage)** 에 저장해요.
진짜 서버에 저장하려면 `frontend/.env.development.local` 파일을 만들고 아래 한 줄을 써요.
이 파일은 git에 올라가지 않아요. 바꾼 뒤에는 `npm run dev`를 다시 실행하세요.

```
VITE_USE_MOCK=false
```

- `npm run build`(배포용)는 `.env.production` 때문에 **항상 진짜 서버**를 써요.
  배포할 때 환경변수를 깜빡해서 기록이 브라우저에만 저장되는 일을 막으려는 설정이에요.

### 휴대폰에서 보기

휴대폰에서 보고 싶으면 `npm run dev -- --host`로 실행한 뒤, 같은 와이파이에 연결된 휴대폰에서
터미널에 나온 `Network` 주소로 접속하세요.

### "사진 찍기" 카메라 동작

- **https 주소나 `localhost`** 에서는 브라우저 안에 카메라 화면이 떠서 바로 찍을 수 있어요. (PC 웹캠도 돼요)
- **`http://192.168.x.x` 같은 주소**에서는 브라우저가 보안 때문에 카메라 화면을 막아요.
  이때는 자동으로 휴대폰의 기본 카메라 앱이 열려요. (PC에서는 파일 선택 창이 열려요)
- 카메라 권한을 거부했거나 카메라가 없으면 "기기 카메라 앱으로 찍기" 버튼이 나와요.
- 휴대폰에서 브라우저 카메라 화면까지 시험하려면 https로 배포한 주소(예: Vercel, Netlify)에서 열어 보세요.

### 비상 시연 모드 (주소 옵션)

주소 뒤에 `?mock=...`을 붙이면 **AI를 부르지 않고** 정해진 결과를 보여 줘요.
발표 중 인터넷이나 AI가 안 될 때, 그리고 결과 화면을 확인할 때 써요. (`src/mocks/demoAnalysis.js`)
화면에 "비상 시연 모드예요"가 표시되고, 기록에는 모델 이름이 `demo`로 남아요.

| 주소 | 결과 |
|---|---|
| `/check?mock` | 랜덤 결과 |
| `/check?mock=CROSSWALK` | 횡단보도 0.9 (`SIDEWALK`, `HYDRANT`, `OTHER`, `NORMAL`도 가능) |
| `/check?mock=MEDIUM` | 최고 확률 0.55 → 주황색 "확신하지 못해요" 경고 + 2위 후보 표시 |
| `/check?mock=LOW` | 최고 확률 0.35 → 결과 대신 "다시 찍기" 안내 |
| `/check?mock=HYDRANT&api=fail` | 서버 저장이 실패해도 결과가 잘 나오는지 확인 (가짜 서버일 때만) |

가짜 서버의 기록은 브라우저 localStorage의 `parking-guard:judgments`에 저장돼요.
지우고 싶으면 개발자 도구(F12) → Application → Local Storage에서 삭제하세요.

---

## 2. 폴더 구조

```
src/
├─ main.jsx                 # 앱 시작점 (글꼴·스타일 불러오기, 라우터 감싸기)
├─ App.jsx                  # 공통 레이아웃: 위 헤더 + 아래 탭(홈/판별/기록/소개) + 페이지 주소 연결
├─ pages/
│  ├─ HomePage.jsx          # /         소개, 시작 버튼, 5가지 유형 카드
│  ├─ CheckPage.jsx         # /check    판별 (select → preview → analyzing → result → guide)
│  ├─ HistoryPage.jsx       # /history  유형별 통계 + 최근 기록
│  └─ AboutPage.jsx         # /about    서비스·팀 소개, AI의 한계, AI 모델 정보
├─ components/
│  ├─ PhotoPicker.jsx       # 사진 찍기 / 갤러리 버튼 + 촬영 팁
│  ├─ CameraCapture.jsx     # 브라우저 안 카메라 화면 + "촬영하기"
│  ├─ PhotoTips.jsx         # 촬영 요령 목록 (사진 고르기·다시 찍기 안내에서 같이 사용)
│  ├─ PhotoPreview.jsx      # 사진 미리보기 (분석 중에는 어둡게 + 훑는 선)
│  ├─ AnalyzingProgress.jsx # 분석 중 3단계 진행 상황 + 지난 시간 + 팁 + 취소
│  ├─ AiChecks.jsx          # "AI가 확인한 것" (주차된 차, 횡단보도, 인도, 소화전, 다른 금지 표시)
│  ├─ ResultCard.jsx        # 결론(유형·확신도·썸네일) + 확신 보통 경고 + AI 판단 근거, 위험한 이유 카드
│  ├─ Icon.jsx              # 선 아이콘 (이모지 대신 사용)
│  ├─ ProbabilityBars.jsx   # 5가지 유형 확률 막대
│  ├─ Checklist.jsx         # 신고 요건 체크박스
│  ├─ ReportGuide.jsx       # 신고 단계 + 안전신문고 링크
│  ├─ FeedbackButton.jsx    # "판별이 틀렸어요"
│  └─ Disclaimer.jsx        # "AI 참고용 판단" + 외부 AI 안내
├─ api/
│  ├─ analyzePhoto.js       # ★ AI 사진 판별 (사진 줄이기 → /api/analyze, ?mock이면 비상 시연 모드)
│  └─ client.js             # ★ 서버 통신 (판별·기록·피드백·통계)
├─ mocks/
│  ├─ demoAnalysis.js       # 비상 시연 모드의 정해진 판별 결과
│  ├─ mockApi.js            # 가짜 서버 (localStorage에 저장)
│  └─ guides.js             # 유형별 안내 문구, 촬영 팁 (서버 실패 시 fallback으로도 사용)
├─ constants/
│  ├─ aiInfo.js             # AI 모델 이름, 개인정보·외부 AI 안내 문구
│  ├─ aiChecks.js           # AI 확인 항목(checks)의 이름표와 판별 순서
│  └─ violationTypes.js     # 라벨 → 한글 이름·아이콘·색상, 확신도 기준(0.7 / 0.4)
├─ utils/
│  ├─ resizeImage.js        # 사진 긴 변을 1024px로 줄이기
│  ├─ dateGroups.js         # 기록을 날짜별(오늘·어제·11월 2일)로 묶기
│  └─ timeAgo.js            # "3분 전" 같은 시간 표시
└─ styles/
   ├─ tokens.css            # 색상·간격·글자 크기 변수
   ├─ global.css            # 전체 기본 스타일
   └─ buttons.module.css    # 공통 버튼 모양
```

- 스타일은 각 컴포넌트 옆의 `*.module.css`(CSS Modules)에 있고, 색·간격은 `tokens.css` 변수를 써요.
- 아이콘은 `components/Icon.jsx`의 선 아이콘을 써요. (`<Icon name="camera" />`) 유형별 아이콘 이름과
  기록 막대 색은 `constants/violationTypes.js`의 `icon`, `chartColor`에 있어요.

---

## 3. AI 판별과 서버 연결

화면 코드는 **`analyzePhoto()`** 와 **`api/client.js`의 함수들**만 사용해요.

### 3-1. AI 사진 판별 — `src/api/analyzePhoto.js`

```js
const result = await analyzePhoto(imgElement);
// → {
//   label: 'CROSSWALK' | 'SIDEWALK' | 'HYDRANT' | 'OTHER' | 'NORMAL',
//   confidence: 0~1 (label의 확률 = 가장 높은 확률),
//   probabilities: { CROSSWALK, SIDEWALK, HYDRANT, OTHER, NORMAL },  // 합계 1
//   reason: 'AI가 말한 판단 근거' | null,
//   checks: { vehicle, crosswalk, sidewalk, hydrant, noParking } | null,  // AI가 확인한 것
//   modelVersion: 'claude-sonnet-5-5'                         // 비상 시연 모드는 'demo'
// }
```

1. 사진을 긴 변 1024px JPEG(품질 0.85)로 줄여요.
2. `POST /api/analyze`(multipart, 필드 이름 `image`)로 보내고 **최대 70초** 기다려요.
3. 서버가 비전 LLM에게 판별 기준을 알려 주고 물어본 뒤, 확률을 검사·보정해서 돌려줘요.
4. 실패하면 서버가 준 안내(400·502·503)나 "AI 분석에 실패했어요"를 보여 주고 **다시 시도** 버튼이 나와요.

- 모델 이름을 화면에 보여 주는 문구는 `src/constants/aiInfo.js`에 있어요.
  백엔드 `application.yaml`의 `ai.provider`나 `claude.model`을 바꾸면 여기도 같이 바꿔 주세요.

### 3-2. 서버 (Spring Boot) — `src/api/client.js`

어떤 서버에 기록할지는 코드가 아니라 **환경변수**로 정해요. (사진 판별은 항상 진짜 서버)
- `src/api/client.js`: `const USE_MOCK = import.meta.env.VITE_USE_MOCK !== 'false';`
- 개발: `.env.development.local`에 `VITE_USE_MOCK=false` (위 "판별 기록을 어디에 저장할지" 참고)
- 배포 빌드: `.env.production`에 이미 `VITE_USE_MOCK=false`가 들어 있어요.
- 프록시: `vite.config.js`에 `server.proxy: { '/api': 'http://localhost:8080' }`가 들어 있어요.
- 서버가 에러를 보내면 `{ status, code, message, fieldErrors }`의 `message`가 Error 메시지가 돼요.

서버 API (자세한 예시는 `../backend/README.md`)

| 함수 | 요청 | 응답 |
|---|---|---|
| `analyzeImage(blob)` | `POST /api/analyze` (multipart `image`) | `{ label, confidence, probabilities, reason, modelVersion }` |
| `postJudgment(body)` | `POST /api/judgments`<br>`{ label, confidence, probabilities, modelVersion }` | `JudgmentResponse` |
| `getJudgments({ limit })` | `GET /api/judgments?limit=20` | `Judgment[]` (최신순) |
| `postFeedback(id, body)` | `POST /api/judgments/{id}/feedback`<br>`{ correct, actualLabel }` | 내용 없음 (204 가능) |
| `getStats()` | `GET /api/stats` | `{ total, byType: { CROSSWALK: n, SIDEWALK: n, HYDRANT: n, OTHER: n, NORMAL: n } }` |

```jsonc
// JudgmentResponse
{
  "judgmentId": 42,
  "type": "CROSSWALK",
  "title": "횡단보도 위 주정차",
  "violation": true,
  "confidenceLevel": "HIGH",          // ≥0.7 HIGH, ≥0.4 MEDIUM, 그 아래 LOW
  "dangerReasons": ["..."],
  "checklist": [{ "id": "plate", "text": "..." }],
  "reportSteps": ["..."],
  "reportUrl": "https://www.safetyreport.go.kr"
}

// Judgment (기록 목록 한 칸)
{
  "judgmentId": 42,
  "label": "CROSSWALK",
  "confidence": 0.9,
  "probabilities": { "CROSSWALK": 0.9, "SIDEWALK": 0.05, "HYDRANT": 0.03, "OTHER": 0.0, "NORMAL": 0.02 },
  "modelVersion": "claude-sonnet-5-5",
  "createdAt": "2026-10-15T09:30:00Z",
  "feedback": null                    // 또는 { "correct": false, "actualLabel": "SIDEWALK", "createdAt": "..." }
}
```

- 안내 문구의 원본은 `src/mocks/guides.js`예요. 서버 요청이 실패하면 화면은 이 파일의 문구를
  대신 보여 줘요(fallback). 그래서 이 파일은 지우지 마세요.
- 확신도 기준(0.7 / 0.4)은 `src/constants/violationTypes.js`에 있어요. 서버와 같은 값으로 맞춰 주세요.

---

## 4. 알아 두면 좋은 점

- **사진은 분석할 때만 외부 AI(Anthropic의 Claude)로 보내지고, 우리 서버에는 저장하지 않아요.**
  판별 기록에는 라벨·확률·모델 이름만 남아요. 번호판·얼굴이 보이지 않는 사진을 쓰도록 안내해요.
- 확신도가 **LOW(0.4 미만)인 결과는 서버로 보내지 않아서 기록에 남지 않아요.** (서버는 LOW도 받으면 저장할 수 있어요.) 결과를 보여 주지 않고 다시 찍게 하므로,
  같은 차를 여러 번 찍었을 때 통계가 부풀려지지 않게 하려는 거예요.
- 안내 문구(`src/mocks/guides.js`)는 **제출 전에 안전신문고 공지와 도로교통법으로 다시 확인**해 주세요.
  지금 문구는 지자체 안내(구리시·김해시·연수구 등)를 참고했어요. 주민신고제는 **안전신문고 앱 카메라로
  같은 위치·같은 방향에서 1분 이상 간격으로 찍은 사진**만 인정되고, 인도는 연석으로 구분된 곳·지역별 시간
  제한이 있어서 체크리스트와 신고 단계에 반영했어요.
- 이 서비스의 결과는 AI의 참고용 판단이며 법적 효력이 없어요.
