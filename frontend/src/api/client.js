// 서버와 이야기하는 함수들을 모아 둔 곳이에요.
// 화면(페이지·컴포넌트)은 항상 이 파일의 함수만 불러요.
//
// ★ 가짜 서버(mocks/mockApi.js)와 진짜 서버(Spring Boot) 중 무엇을 쓸지는 환경변수로 정해요.
//   - 기본: 가짜 서버 (mock)
//   - frontend/.env.development.local 파일에 VITE_USE_MOCK=false 를 쓰면 진짜 서버를 써요.
//     (파일을 바꾼 뒤에는 npm run dev를 다시 실행하세요)
//
// 데이터 모양
//   JudgmentResponse (postJudgment의 결과)
//     { judgmentId, type, title, violation, confidenceLevel: 'HIGH'|'MEDIUM'|'LOW',
//       dangerReasons: string[], checklist: { id, text }[], reportSteps: string[], reportUrl }
//   Judgment (getJudgments의 목록 한 칸)
//     { judgmentId, label, confidence, probabilities, modelVersion,
//       createdAt: ISO 문자열, feedback: null | { correct, actualLabel, createdAt } }
//     (진짜 서버는 confidenceLevel도 함께 보내요)
//   서버 에러 응답
//     { status, code: 'VALIDATION_FAILED' | 'NOT_FOUND' | ..., message, fieldErrors: [{ field, message }] }

import * as mockApi from '../mocks/mockApi';

const USE_MOCK = import.meta.env.VITE_USE_MOCK !== 'false';

// 진짜 서버 주소. 개발할 때는 vite.config.js의 proxy로 Spring Boot(예: 8080)에 연결하면 돼요.
const API_BASE = '/api';

// ───────────── 진짜 서버용 fetch 구현 (USE_MOCK = false일 때 사용) ─────────────

// 서버가 보낸 에러 내용({ status, code, message, fieldErrors })을 최대한 읽어서 Error로 만들어요.
async function toError(response) {
  let body = null;
  try {
    body = await response.json();
  } catch {
    // JSON이 아닌 응답 (예: 서버가 꺼져서 프록시가 보낸 에러)
  }
  const error = new Error(body?.message ?? `서버 요청이 실패했어요. (${response.status})`);
  error.status = response.status;
  error.code = body?.code ?? null;
  error.fieldErrors = body?.fieldErrors ?? [];
  return error;
}

async function request(path, options = {}) {
  const response = await fetch(`${API_BASE}${path}`, {
    ...options,
    headers: { 'Content-Type': 'application/json', ...options.headers },
  });

  if (!response.ok) {
    throw await toError(response);
  }

  // 204 No Content처럼 내용이 없는 응답
  const text = await response.text();
  return text ? JSON.parse(text) : null;
}

function realPostJudgment(body) {
  return request('/judgments', { method: 'POST', body: JSON.stringify(body) });
}

function realGetJudgments({ limit = 20 } = {}) {
  return request(`/judgments?limit=${encodeURIComponent(limit)}`);
}

async function realPostFeedback(id, { correct, actualLabel }) {
  await request(`/judgments/${encodeURIComponent(id)}/feedback`, {
    method: 'POST',
    body: JSON.stringify({ correct, actualLabel }),
  });
}

function realGetStats() {
  return request('/stats');
}

// ───────────── 화면에서 쓰는 함수 ─────────────

// 외부 AI가 오래 걸릴 수 있어서 70초까지 기다려요. (서버는 NVIDIA를 60초까지 기다려요)
const ANALYZE_TIMEOUT_MS = 70_000;

// 사진을 서버(/api/analyze)로 보내 외부 AI 판별 결과를 받아요. (VITE_MODEL_MODE=api일 때 useModel이 써요)
// → { label, confidence, probabilities, reason, modelVersion }
export async function analyzeImage(imageBlob) {
  const formData = new FormData();
  formData.append('image', imageBlob, 'photo.jpg');

  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), ANALYZE_TIMEOUT_MS);
  try {
    // FormData를 보낼 때는 Content-Type을 직접 쓰지 않아요. (브라우저가 알맞게 넣어 줘요)
    const response = await fetch(`${API_BASE}/analyze`, {
      method: 'POST',
      body: formData,
      signal: controller.signal,
    });
    if (!response.ok) {
      throw await toError(response);
    }
    return await response.json();
  } catch (error) {
    if (error.name === 'AbortError') {
      const timeoutError = new Error('AI 분석 시간이 너무 오래 걸려요.');
      timeoutError.code = 'TIMEOUT';
      throw timeoutError;
    }
    throw error;
  } finally {
    clearTimeout(timer);
  }
}

// 판별 결과를 서버에 저장하고, 유형별 안내를 받아요.
// ⚠ 사진은 보내지 않아요. (label, confidence, probabilities, modelVersion만)
export function postJudgment({ label, confidence, probabilities, modelVersion }) {
  const body = { label, confidence, probabilities, modelVersion };
  return USE_MOCK ? mockApi.postJudgment(body) : realPostJudgment(body);
}

// 최근 판별 기록을 최신순으로 가져와요.
export function getJudgments({ limit = 20 } = {}) {
  return USE_MOCK ? mockApi.getJudgments({ limit }) : realGetJudgments({ limit });
}

// "판별이 틀렸어요" 피드백을 보내요.
export function postFeedback(id, { correct, actualLabel }) {
  return USE_MOCK
    ? mockApi.postFeedback(id, { correct, actualLabel })
    : realPostFeedback(id, { correct, actualLabel });
}

// 유형별 판별 횟수를 가져와요. → { total, byType: { CROSSWALK: n, ... } }
export function getStats() {
  return USE_MOCK ? mockApi.getStats() : realGetStats();
}
