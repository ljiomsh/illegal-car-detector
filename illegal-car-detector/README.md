# 불법 주정차 판별·신고 도우미 (프론트엔드)

환일고 「환일야호」 팀 · 2026 AI동행 프로젝트 자율탐구 출품작

주차된 차와 주변이 함께 나온 사진을 올리면 AI가 아래 4가지 중 하나로 판별하고,
위험한 이유 · 신고 요건 체크리스트 · 안전신문고 신고 방법을 안내하는 웹 서비스예요.

| 라벨 | 의미 |
|---|---|
| `CROSSWALK` | 🚸 횡단보도 위 주정차 |
| `SIDEWALK` | 🚶 인도 위 주정차 |
| `HYDRANT` | 🧯 소화전 주변 주정차 |
| `NORMAL` | ✅ 정상 주차 |

> 지금은 **AI 모델과 서버가 모두 가짜(mock)** 예요. 화면은 완성된 상태이고,
> 나중에 mock 부분만 바꾸면 진짜 모델·서버로 동작하게 만들어 두었어요. (아래 "mock을 실제로 바꾸는 방법" 참고)

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

휴대폰에서 보고 싶으면 `npm run dev -- --host`로 실행한 뒤, 같은 와이파이에 연결된 휴대폰에서
터미널에 나온 `Network` 주소로 접속하세요.

### "사진 찍기" 카메라 동작

- **https 주소나 `localhost`** 에서는 브라우저 안에 카메라 화면이 떠서 바로 찍을 수 있어요. (PC 웹캠도 돼요)
- **`http://192.168.x.x` 같은 주소**에서는 브라우저가 보안 때문에 카메라 화면을 막아요.
  이때는 자동으로 휴대폰의 기본 카메라 앱이 열려요. (PC에서는 파일 선택 창이 열려요)
- 카메라 권한을 거부했거나 카메라가 없으면 "기기 카메라 앱으로 찍기" 버튼이 나와요.
- 휴대폰에서 브라우저 카메라 화면까지 시험하려면 https로 배포한 주소(예: Vercel, Netlify)에서 열어 보세요.

### 개발 테스트용 주소 옵션

판별 결과 화면을 쉽게 확인하려고 주소 뒤에 옵션을 붙일 수 있어요. (mock일 때만 동작)

| 주소 | 결과 |
|---|---|
| `/check` | 랜덤 결과 |
| `/check?mock=CROSSWALK` | 횡단보도 0.9 (`SIDEWALK`, `HYDRANT`, `NORMAL`도 가능) |
| `/check?mock=MEDIUM` | 최고 확률 0.55 → 주황색 "확신하지 못해요" 경고 + 2위 후보 표시 |
| `/check?mock=LOW` | 최고 확률 0.35 → 결과 대신 "다시 찍기" 안내 |
| `/check?mock=HYDRANT&api=fail` | 서버 저장이 실패해도 결과가 잘 나오는지 확인 |

기록은 브라우저 localStorage의 `parking-guard:judgments`에 저장돼요.
지우고 싶으면 개발자 도구(F12) → Application → Local Storage에서 삭제하세요.

---

## 2. 폴더 구조

```
src/
├─ main.jsx                 # 앱 시작점 (글꼴·스타일 불러오기, 라우터 감싸기)
├─ App.jsx                  # 공통 레이아웃: 위 헤더 + 아래 탭(홈/판별/기록/소개) + 페이지 주소 연결
├─ pages/
│  ├─ HomePage.jsx          # /         소개, 시작 버튼, 4가지 유형 카드
│  ├─ CheckPage.jsx         # /check    판별 (select → preview → analyzing → result → guide)
│  ├─ HistoryPage.jsx       # /history  유형별 통계 + 최근 기록
│  └─ AboutPage.jsx         # /about    서비스·팀 소개, AI의 한계
├─ components/
│  ├─ PhotoPicker.jsx       # 사진 찍기 / 갤러리 버튼 + 촬영 팁
│  ├─ CameraCapture.jsx     # 브라우저 안 카메라 화면 + "촬영하기"
│  ├─ PhotoTips.jsx         # 촬영 요령 목록 (사진 고르기·다시 찍기 안내에서 같이 사용)
│  ├─ PhotoPreview.jsx      # 사진 미리보기 + "분석 중" 표시
│  ├─ ResultCard.jsx        # 유형 배지 + 확신도 + 위험한 이유
│  ├─ ProbabilityBars.jsx   # 4가지 유형 확률 막대
│  ├─ Checklist.jsx         # 신고 요건 체크박스
│  ├─ ReportGuide.jsx       # 신고 단계 + 안전신문고 링크
│  ├─ FeedbackButton.jsx    # "판별이 틀렸어요"
│  └─ Disclaimer.jsx        # "AI 참고용 판단" 안내
├─ hooks/useModel.js        # ★ AI 모델 (지금은 mock)
├─ api/client.js            # ★ 서버 통신 (지금은 mock)
├─ mocks/
│  ├─ mockModel.js          # 가짜 AI 모델
│  ├─ mockApi.js            # 가짜 서버 (localStorage에 저장)
│  └─ guides.js             # 유형별 안내 문구, 촬영 팁 (서버 실패 시 fallback으로도 사용)
├─ constants/violationTypes.js  # 라벨 → 한글 이름·아이콘·색상, 확신도 기준(0.7 / 0.4)
├─ utils/
│  ├─ resizeImage.js        # 사진 긴 변을 1024px로 줄이기
│  └─ timeAgo.js            # "3분 전" 같은 시간 표시
└─ styles/
   ├─ tokens.css            # 색상·간격·글자 크기 변수
   ├─ global.css            # 전체 기본 스타일
   └─ buttons.module.css    # 공통 버튼 모양
```

- 스타일은 각 컴포넌트 옆의 `*.module.css`(CSS Modules)에 있고, 색·간격은 `tokens.css` 변수를 써요.
- 아이콘(이모지)은 `constants/violationTypes.js`에서만 관리해요. SVG로 바꿀 때 여기만 고치면 돼요.

---

## 3. mock을 실제 모델·API로 바꾸는 방법

화면 코드는 **`useModel()`** 과 **`api/client.js`의 함수 4개**만 사용해요.
이 두 곳의 "모양(입력과 출력)"만 지키면 화면 코드는 하나도 안 고쳐도 돼요.

### 3-1. AI 모델 (Teachable Machine) — `src/hooks/useModel.js`

**지켜야 할 모양**

```js
const { status, predict } = useModel();
// status: 'loading' | 'ready' | 'error'
// predict(imgElement) → Promise<{
//   label: 'CROSSWALK' | 'SIDEWALK' | 'HYDRANT' | 'NORMAL',
//   confidence: 0~1 (가장 높은 확률),
//   probabilities: { CROSSWALK, SIDEWALK, HYDRANT, NORMAL }  // 합계 1
// }>
```

**바꾸는 순서**

1. Teachable Machine에서 이미지 모델을 학습할 때 **클래스 이름을 `CROSSWALK`, `SIDEWALK`, `HYDRANT`, `NORMAL`로** 정해요.
   (한글 이름으로 학습했다면 아래 코드에서 이름을 라벨로 바꿔 주는 표를 만들어야 해요.)
2. "모델 내보내기 → TensorFlow.js → 다운로드"로 받은 `model.json`, `metadata.json`, `weights.bin`을
   `public/model/` 폴더에 넣어요. (사진을 기기 안에서 분석하려면 서버가 아니라 이 방식이 맞아요.)
3. 패키지를 설치해요.
   ```bash
   npm install @tensorflow/tfjs @teachablemachine/image
   ```
   `@teachablemachine/image`는 오래된 tfjs 버전을 요구해서 설치 중 버전 충돌이 날 수 있어요.
   그럴 때는 Teachable Machine 내보내기 화면의 예시 코드에 적힌 버전을 그대로 설치하세요.
4. `src/hooks/useModel.js`에서 **`loadModel` 함수와 `MODEL_VERSION`만** 아래처럼 바꿔요.
   (`useModel` 함수 본문은 그대로 둬요.)
   ```js
   import * as tmImage from '@teachablemachine/image';

   export const MODEL_VERSION = 'tm-2026-11-01'; // 학습한 날짜 등으로 정해요

   async function loadModel() {
     const model = await tmImage.load('/model/model.json', '/model/metadata.json');
     return {
       async predict(imgElement) {
         const results = await model.predict(imgElement); // [{ className, probability }, ...]
         const probabilities = { CROSSWALK: 0, SIDEWALK: 0, HYDRANT: 0, NORMAL: 0 };
         results.forEach((r) => { probabilities[r.className] = r.probability; });
         const label = Object.keys(probabilities).reduce((a, b) =>
           probabilities[b] > probabilities[a] ? b : a);
         return { label, confidence: probabilities[label], probabilities };
       },
     };
   }
   ```
5. `import { loadMockModel, MOCK_MODEL_VERSION } from '../mocks/mockModel';` 줄을 지워요.
   이제 `?mock=` 옵션은 동작하지 않아요.

### 3-2. 서버 (Spring Boot) — `src/api/client.js`

1. `src/api/client.js` 맨 위의 **`const USE_MOCK = true;`를 `false`로** 바꿔요.
   그러면 이미 작성해 둔 `realPostJudgment` 등 fetch 함수가 쓰여요.
2. 개발할 때 Spring Boot(예: 8080 포트)로 요청이 가도록 `vite.config.js`에 proxy를 추가해요.
   ```js
   export default defineConfig({
     plugins: [react()],
     server: { proxy: { '/api': 'http://localhost:8080' } },
   });
   ```
3. 서버는 아래 API를 만들어 주면 돼요. (프론트가 이미 이 모양으로 요청해요)

| 함수 | 요청 | 응답 |
|---|---|---|
| `postJudgment(body)` | `POST /api/judgments`<br>`{ label, confidence, probabilities, modelVersion }` | `JudgmentResponse` |
| `getJudgments({ limit })` | `GET /api/judgments?limit=20` | `Judgment[]` (최신순) |
| `postFeedback(id, body)` | `POST /api/judgments/{id}/feedback`<br>`{ correct, actualLabel }` | 내용 없음 (204 가능) |
| `getStats()` | `GET /api/stats` | `{ total, byType: { CROSSWALK: n, SIDEWALK: n, HYDRANT: n, NORMAL: n } }` |

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
  "probabilities": { "CROSSWALK": 0.9, "SIDEWALK": 0.05, "HYDRANT": 0.03, "NORMAL": 0.02 },
  "modelVersion": "tm-2026-11-01",
  "createdAt": "2026-10-15T09:30:00Z",
  "feedback": null                    // 또는 { "correct": false, "actualLabel": "SIDEWALK", "createdAt": "..." }
}
```

- 안내 문구의 원본은 `src/mocks/guides.js`예요. 서버로 옮길 때 이 내용을 그대로 쓰면 되고,
  서버 요청이 실패하면 화면은 계속 이 파일의 문구를 대신 보여 줘요(fallback). 그래서 이 파일은 지우지 마세요.
- 확신도 기준(0.7 / 0.4)은 `src/constants/violationTypes.js`에 있어요. 서버와 같은 값으로 맞춰 주세요.

---

## 4. 알아 두면 좋은 점

- **사진은 서버로 보내지 않아요.** 서버에는 라벨·확률·모델 버전만 보내요.
- 확신도가 **LOW(0.4 미만)인 결과는 기록에 저장하지 않아요.** 결과를 보여 주지 않고 다시 찍게 하므로,
  같은 차를 여러 번 찍었을 때 통계가 부풀려지지 않게 하려는 거예요.
- 안내 문구(`src/mocks/guides.js`)는 **제출 전에 안전신문고 공지와 도로교통법으로 다시 확인**해 주세요.
  지금 문구는 지자체 안내(구리시·김해시·연수구 등)를 참고했어요. 주민신고제는 **안전신문고 앱 카메라로
  같은 위치·같은 방향에서 1분 이상 간격으로 찍은 사진**만 인정되고, 인도는 연석으로 구분된 곳·지역별 시간
  제한이 있어서 체크리스트와 신고 단계에 반영했어요.
- 이 서비스의 결과는 AI의 참고용 판단이며 법적 효력이 없어요.
