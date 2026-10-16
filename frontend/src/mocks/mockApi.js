// 가짜(mock) 서버예요. 진짜 Spring Boot 서버가 생기기 전까지 사용해요.
// - 판별 기록은 브라우저의 localStorage에 저장해요. (이 기기에서만 보여요)
// - 사진은 저장하지 않아요. 라벨·확률·시간·피드백만 저장해요.
// - 진짜 네트워크처럼 보이게 300ms 기다렸다가 응답해요.
//
// 개발 테스트용: 주소에 ?api=fail 을 붙이면 모든 요청이 실패해요. (실패 화면 확인용)

import { LABELS } from '../constants/violationTypes';
import { buildGuideResponse } from './guides';

const STORAGE_KEY = 'parking-guard:judgments';
const MAX_RECORDS = 200; // 너무 많이 쌓이지 않게 최근 200개만 보관해요.
const NETWORK_DELAY_MS = 300;

function delay(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

// 300ms 기다리고, ?api=fail 이면 실패를 흉내 내요.
async function fakeNetwork() {
  await delay(NETWORK_DELAY_MS);
  if (new URLSearchParams(window.location.search).get('api') === 'fail') {
    throw new Error('서버에 연결하지 못했어요. (mock 실패 테스트)');
  }
}

// 저장된 기록 전체를 읽어요. 문제가 생기면 빈 배열을 돌려줘요.
function readAll() {
  try {
    const saved = JSON.parse(localStorage.getItem(STORAGE_KEY));
    return Array.isArray(saved) ? saved : [];
  } catch (error) {
    console.warn('기록을 읽지 못했어요:', error);
    return [];
  }
}

// 기록 전체를 저장해요. 성공하면 true, 실패하면 false를 돌려줘요.
function writeAll(records) {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(records));
    return true;
  } catch (error) {
    console.warn('기록을 저장하지 못했어요:', error);
    return false;
  }
}

// 판별 결과를 저장하고, 유형별 안내(JudgmentResponse)를 돌려줘요.
export async function postJudgment({ label, confidence, probabilities, modelVersion }) {
  await fakeNetwork();

  if (!LABELS.includes(label)) {
    throw new Error(`알 수 없는 라벨이에요: ${label}`);
  }

  const records = readAll();
  const lastId = records.reduce((max, record) => Math.max(max, record.judgmentId), 0);
  const record = {
    judgmentId: lastId + 1,
    label,
    confidence,
    probabilities,
    modelVersion,
    createdAt: new Date().toISOString(),
    feedback: null,
  };

  const saved = writeAll([record, ...records].slice(0, MAX_RECORDS));
  if (!saved) {
    throw new Error('기록을 저장하지 못했어요.');
  }

  return { ...buildGuideResponse({ label, confidence }), judgmentId: record.judgmentId };
}

// 최근 기록을 최신순으로 limit개 돌려줘요.
export async function getJudgments({ limit = 20 } = {}) {
  await fakeNetwork();
  return readAll()
    .sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt))
    .slice(0, limit);
}

// "판별이 틀렸어요" 피드백을 저장해요.
export async function postFeedback(id, { correct, actualLabel }) {
  await fakeNetwork();

  const records = readAll();
  const target = records.find((record) => record.judgmentId === id);
  if (!target) {
    throw new Error('해당 기록을 찾지 못했어요.');
  }

  target.feedback = {
    correct,
    actualLabel: actualLabel ?? null,
    createdAt: new Date().toISOString(),
  };

  if (!writeAll(records)) {
    throw new Error('피드백을 저장하지 못했어요.');
  }
}

// 유형별 판별 횟수를 돌려줘요. (AI가 판별한 라벨 기준)
export async function getStats() {
  await fakeNetwork();

  const byType = {};
  LABELS.forEach((label) => {
    byType[label] = 0;
  });

  const records = readAll();
  records.forEach((record) => {
    if (record.label in byType) {
      byType[record.label] += 1;
    }
  });

  return { total: records.length, byType };
}
