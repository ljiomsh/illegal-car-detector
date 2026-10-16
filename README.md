# illegal-car-detector — 불법 주정차 판별·신고 도우미

환일고 「환일야호」 팀 · 2026 AI동행 프로젝트 자율탐구 출품작

| 폴더 | 내용 | 설명서 |
|---|---|---|
| `frontend/` | React + Vite 화면 | [frontend/README.md](frontend/README.md) |
| `backend/` | Spring Boot 서버 (판별 기록, 통계, AI 사진 판별) | [backend/README.md](backend/README.md) |

---

## 발표용 실행 방법 (Windows) — 외부 AI(NVIDIA Build · DeepSeek V4.1 Flash) 판별

현재 버전은 사진을 서버(`/api/analyze`)로 보내고, 서버가 NVIDIA Build API로 판별을 맡겨요.
**API 키는 git에 올라가지 않는 곳(아래 두 방법 중 하나)에만 넣어요.**

### 1) 백엔드 켜기 (터미널 1)

**방법 A (추천) — 설정 파일**: `backend/secrets.properties`를 열고 `NVIDIA_API_KEY=` 뒤에 키를 붙여 써요.
```
NVIDIA_API_KEY=nvapi-여기에키
```
- 이 파일은 `.gitignore`에 들어 있어서 git에 올라가지 않아요. 팀원은 `secrets.properties.example`을 복사해서 만들면 돼요.
- 그다음 `cd backend` → `gradlew.bat bootRun` (PowerShell은 `.\gradlew.bat bootRun`)

**방법 B — 환경변수** (파일보다 먼저 쓰여요)

cmd를 쓸 때:
```bat
cd backend
set NVIDIA_API_KEY=키값
gradlew.bat bootRun
```

PowerShell을 쓸 때:
```powershell
cd backend
$env:NVIDIA_API_KEY="키값"
.\gradlew.bat bootRun
```

- 환경변수는 그 터미널 창에서만 유효해요. 창을 닫으면 다시 넣어야 해요.
- 키 없이 켜면 로그에 `NVIDIA_API_KEY가 없어요` 경고가 나오고, 사진 판별은 503(안내 화면)이 돼요.
- 확인: `curl.exe -F "image=@test.jpg" http://localhost:8080/api/analyze`

### 2) 프론트 켜기 (터미널 2)

`frontend/.env.development.local`에 아래 두 줄이 있어야 해요. (이미 만들어 두었어요. git에 올라가지 않아요)
```
VITE_MODEL_MODE=api
VITE_USE_MOCK=false
```
- `VITE_MODEL_MODE=api` → 사진 판별을 외부 AI로
- `VITE_USE_MOCK=false` → 판별 기록·통계를 진짜 서버에 저장

```powershell
cd frontend
npm run dev
```
→ 터미널에 나온 주소(http://localhost:5173)로 접속해요.

### 3) 비상용: 인터넷이나 AI가 안 될 때

주소 끝에 `?mock=...`을 붙이면 **설정과 상관없이** 가짜(mock) 판별을 써요. (외부 AI를 부르지 않아요)

| 주소 | 결과 |
|---|---|
| `http://localhost:5173/check?mock=CROSSWALK` | 횡단보도 (SIDEWALK, HYDRANT, NORMAL도 가능) |
| `http://localhost:5173/check?mock=MEDIUM` | 확신 보통 화면 |
| `http://localhost:5173/check?mock=LOW` | 다시 찍기 안내 화면 |

### 알아 두기
- 외부 AI 분석은 보통 수 초~수십 초 걸리고, 최대 1분까지 기다려요. (서버 60초, 화면 70초 제한)
- AI가 실패하면 "AI 분석에 실패했어요. 다시 시도해 주세요."와 **다시 시도** 버튼이 나와요.
- 사진은 우리 서버에 저장하지 않고, 로그에도 남기지 않아요. 다만 분석을 위해 NVIDIA로 보내지니
  **번호판·얼굴이 보이지 않는 사진**을 써 주세요.

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
| **503** "지금은 AI 분석을 쓸 수 없어요" | 서버에 NVIDIA API 키가 없음 (시작 로그에 `NVIDIA_API_KEY가 없어요`) | `backend/secrets.properties`에 키를 넣거나 `$env:NVIDIA_API_KEY="키값"` 후 **같은 창에서** 서버 재시작 |
| **502** "AI 분석에 실패했어요" | NVIDIA 오류(키가 틀림=401 등)·60초 초과·이상한 답 | 백엔드 로그의 `NVIDIA API 오류: status=...` 확인 → 키 확인 후 다시 시도. 급하면 주소 끝에 `?mock=CROSSWALK` |
| **413** 또는 "5MB 이하만" | 사진이 너무 큼 | 화면은 사진을 1024px로 줄여 보내서 보통 안 나요. curl로 직접 보낼 때는 5MB 이하 사진을 쓰세요 |
| `.env.development.local`을 바꿨는데 그대로 | Vite는 시작할 때만 환경변수를 읽음 | `npm run dev`를 껐다가 다시 실행 |
