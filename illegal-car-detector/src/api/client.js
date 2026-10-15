// 서버와 이야기하는 함수들을 모아 둔 곳이에요.
// 화면(페이지·컴포넌트)은 항상 이 파일의 함수만 불러요.
//
// ★ 지금은 USE_MOCK = true 라서 가짜 서버(mocks/mockApi.js)를 써요.
//   Spring Boot 서버가 준비되면 USE_MOCK을 false로 바꾸면 아래 fetch 함수들이 쓰여요.
//
// 데이터 모양
//   JudgmentResponse (postJudgment의 결과)
//     { judgmentId, type, title, violation, confidenceLevel: 'HIGH'|'MEDIUM'|'LOW',
//       dangerReasons: string[], checklist: { id, text }[], reportSteps: string[], reportUrl }
//   Judgment (getJudgments의 목록 한 칸)
//     { judgmentId, label, confidence, probabilities, modelVersion,
//       createdAt: ISO 문자열, feedback: null | { correct, actualLabel, createdAt } }

import * as mockApi from '../mocks/mockApi';

const USE_MOCK = true;

// 진짜 서버 주소. 개발할 때는 vite.config.js의 proxy로 Spring Boot(예: 8080)에 연결하면 돼요.
const API_BASE = '/api';

// ───────────── 진짜 서버용 fetch 구현 (USE_MOCK = false일 때 사용) ─────────────

async function request(path, options = {}) {
  const response = await fetch(`${API_BASE}${path}`, {
    ...options,
    headers: { 'Content-Type': 'application/json', ...options.headers },
  });

  if (!response.ok) {
    throw new Error(`서버 요청이 실패했어요. (${response.status})`);
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
