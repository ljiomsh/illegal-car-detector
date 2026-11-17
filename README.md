# 길터 (illegal-car-detector) — 불법 주정차 판별·신고 도우미

환일고 「환일야호」 팀 · 2026 AI동행 프로젝트 자율탐구 출품작

| 폴더 | 내용 | 설명서 |
|---|---|---|
| `frontend/` | React + Vite 화면 | [frontend/README.md](frontend/README.md) |
| `backend/` | Spring Boot 서버 (AI 사진 판별, 판별 기록, 통계) | [backend/README.md](backend/README.md) |

## AI 판별 방식

사진을 서버(`/api/analyze`)로 보내면, 서버가 **사진을 볼 수 있는 대규모 언어 모델(비전 LLM)** 에 판별을 맡겨요.
- 모델: Anthropic의 **Claude Sonnet 5.5** (`claude-sonnet-5-5`) (`backend/src/main/resources/application.yaml`의 `claude.model`에서 바꿀 수 있어요)
  - `ai.provider`를 `nvidia`로 바꾸면 예전에 쓰던 NVIDIA Build의 Nemotron 3.5 Super VL로 돌아가요.
  - 웹에서 모은 사진 222장으로 쟀을 때 Claude 87.8%, Super VL 83.3%였어요. (자세한 내용: [backend/README.md](backend/README.md))
- 판별 기준: 기획서의 라벨링 기준서(횡단보도 위 / 인도 위 / 소화전 주변 / 기타 불법 주정차 / 정상 주차)를 단계별 확인 항목으로 알려 줘요.
- AI는 **사진에서 보이는 것**(주차된 차가 있는지, 횡단보도 줄 위인지, 바퀴가 보도 위인지, 소화전 5m 안인지, 다른 금지 표시)을
  정해진 값으로 답하고, **유형은 서버가 우선순위 규칙으로 정해요.** 차가 없거나 판단이 어긋나면 확신도를 낮춰서 "다시 찍기"나 "보통"으로 보여 줘요.
- 화면의 결과 카드에 "AI가 확인한 것"이 나와서 왜 그렇게 판별했는지 알 수 있어요. (자세한 규칙: [backend/README.md](backend/README.md))
- 사진은 우리 서버에 저장하지 않아요. 판별 결과 숫자만 기록해요.

직접 사진을 모아 모델을 학습하는 방식도 검토했지만, 사진 수집에 시간이 많이 들어서 비전 LLM 방식으로 정했어요.

---

## 실행 방법 (Windows)

**API 키는 git에 올라가지 않는 곳(아래 두 방법 중 하나)에만 넣어요.**

### 1) 백엔드 켜기 (터미널 1)

**방법 A (추천) — 설정 파일**: `backend/secrets.properties`를 열고 `ANTHROPIC_API_KEY=` 뒤에 키를 붙여 써요.
```
ANTHROPIC_API_KEY=sk-ant-여기에키
```
- 키는 [platform.claude.com](https://platform.claude.com)에서 만들어요. (`ai.provider`를 `nvidia`로 쓸 때는 `NVIDIA_API_KEY=nvapi-...`)
- 이 파일은 `.gitignore`에 들어 있어서 git에 올라가지 않아요. 팀원은 `secrets.properties.example`을 복사해서 만들면 돼요.
- 그다음 `cd backend` → `gradlew.bat bootRun` (PowerShell은 `.\gradlew.bat bootRun`)

**방법 B — 환경변수** (파일보다 먼저 쓰여요)

cmd를 쓸 때:
```bat
cd backend
set ANTHROPIC_API_KEY=키값
gradlew.bat bootRun
```

PowerShell을 쓸 때:
```powershell
cd backend
$env:ANTHROPIC_API_KEY="키값"
.\gradlew.bat bootRun
```

- 환경변수는 그 터미널 창에서만 유효해요. 창을 닫으면 다시 넣어야 해요.
- 키 없이 켜면 로그에 `AI API 키가 없어요` 경고가 나오고, 사진 판별은 503(안내 화면)이 돼요.
- 확인: `curl.exe -F "image=@test.jpg" http://localhost:8080/api/analyze`

### 2) 프론트 켜기 (터미널 2)

`frontend/.env.development.local`에 아래 줄이 있어야 해요. (이미 만들어 두었어요. git에 올라가지 않아요)
```
VITE_USE_MOCK=false
```
- `VITE_USE_MOCK=false` → 판별 기록·통계를 진짜 서버에 저장 (없으면 브라우저 안의 가짜 서버에 저장)
- 사진 판별은 설정과 상관없이 언제나 서버의 AI가 해요.

```powershell
cd frontend
npm run dev
```
→ 터미널에 나온 주소(http://localhost:5173)로 접속해요.

### 3) 비상 시연 모드: 인터넷이나 AI가 안 될 때

주소 끝에 `?mock=...`을 붙이면 AI를 부르지 않고 정해진 결과를 보여 줘요.
화면에 "비상 시연 모드예요"라고 표시되고, 기록에는 모델 이름이 `demo`로 남아요.

| 주소 | 결과 |
|---|---|
| `http://localhost:5173/check?mock=CROSSWALK` | 횡단보도 (SIDEWALK, HYDRANT, OTHER, NORMAL도 가능) |
| `http://localhost:5173/check?mock=MEDIUM` | 확신 보통 화면 |
| `http://localhost:5173/check?mock=LOW` | 다시 찍기 안내 화면 |

### 알아 두기
- AI 분석은 보통 3~4초 걸리고, 최대 1분쯤 기다려요. (서버 30초씩 두 번 `claude.timeout`, 화면 70초 제한)
- AI가 실패하면 "AI 분석에 실패했어요. 다시 시도해 주세요."와 **다시 시도** 버튼이 나와요.
- 사진은 우리 서버에 저장하지 않고, 로그에도 남기지 않아요. 다만 분석을 위해 Anthropic(Claude)으로 보내지니
  **번호판·얼굴이 보이지 않는 사진**을 써 주세요.

---

## 인터넷에 공개하기 (Netlify + 내 PC 백엔드)

주소: **https://gilltoe.netlify.app**

- **화면**은 Netlify가 GitHub의 `main`을 받아 자동으로 빌드·배포해요. (설정: 루트의 `netlify.toml`)
- **사진 판별·기록(`/api`)** 은 내 PC의 백엔드가 해요. Netlify가 `/api` 요청을 cloudflared 터널 주소로 넘겨줘요.
  그래서 **발표·시연 동안 PC에서 백엔드와 터널이 켜져 있어야 해요.**

켜는 순서 (PowerShell, 저장소 루트에서):
1. 백엔드 켜기: `cd backend` → `.\gradlew.bat bootRun` (창은 그대로 두기)
2. 새 창에서 터널 켜기: `.\scripts\start-tunnel.ps1 -Push`
   - 터널 주소를 `netlify.toml`에 넣고 GitHub에 올려요. 약 1분 뒤 Netlify가 새로 배포해요.
   - 이 창을 닫으면 터널이 꺼져요. 터널을 다시 켜면 주소가 바뀌니, 그때마다 이 명령을 다시 실행해요.
3. 확인: https://gilltoe.netlify.app/api/health 가 `{"status":"ok"}`이면 연결된 거예요.

---

## 연결 확인 순서 (브라우저)

1. http://localhost:8080/api/health → `{"status":"ok"}` 이면 **백엔드** OK
2. http://localhost:5173/api/health → `{"status":"ok"}` 이면 **프론트 → 백엔드 프록시** OK
3. http://localhost:5173 → "사진으로 확인하기"에서 사진 판별 (보통 10~40초 걸려요)

## 자주 나는 오류와 해결법

| 증상 | 원인 | 해결 |
|---|---|---|
| 프론트 터미널에 `ECONNREFUSED`, 화면에 "AI 분석에 실패했어요" | 백엔드가 꺼져 있음 | `backend`에서 `.\gradlew.bat bootRun` 실행 후 1번 주소 확인 |
| `/api/...`가 **404** | 백엔드 주소가 틀렸거나 5173이 아닌 주소로 접속, 또는 프론트를 프록시 없이 실행 | 2번 주소가 되는지 확인. `frontend/vite.config.js`의 `proxy` 확인 후 `npm run dev` 재시작 |
| 브라우저 콘솔에 **CORS** 에러 | `http://localhost:8080/api/...`를 화면 코드에서 직접 부름 | 화면 코드는 항상 `/api/...` 상대 주소만 써요(`src/api/client.js`). 5173으로 접속하세요 |
| **503** "지금은 AI 분석을 쓸 수 없어요" | 서버에 Claude API 키가 없음 (시작 로그에 `AI API 키가 없어요`) | `backend/secrets.properties`에 키를 넣거나 `$env:ANTHROPIC_API_KEY="키값"` 후 **같은 창에서** 서버 재시작 |
| **502** "AI 분석에 실패했어요" | Claude 오류(키가 틀림=401, 요금 한도 등)·시간 초과·이상한 답 | 백엔드 로그의 `Claude API 오류: status=...` 확인 → 키 확인 후 다시 시도. 급하면 주소 끝에 `?mock=CROSSWALK` |
| **413** 또는 "5MB 이하만" | 사진이 너무 큼 | 화면은 사진을 1024px로 줄여 보내서 보통 안 나요. curl로 직접 보낼 때는 5MB 이하 사진을 쓰세요 |
| `.env.development.local`을 바꿨는데 그대로 | Vite는 시작할 때만 환경변수를 읽음 | `npm run dev`를 껐다가 다시 실행 |
