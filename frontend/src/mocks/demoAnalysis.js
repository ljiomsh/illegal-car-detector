// 비상 시연 모드예요. AI를 부르지 않고 정해진 판별 결과를 만들어요.
// 발표 중 인터넷이나 AI가 안 될 때, 그리고 결과 화면을 개발·확인할 때 써요. (api/analyzePhoto.js)
//
// 주소 옵션 (?mock=...)
//   /check?mock=CROSSWALK  → 횡단보도가 0.9로 나와요 (SIDEWALK, HYDRANT, OTHER, NORMAL도 가능)
//   /check?mock=MEDIUM     → 최고 확률 0.55 (확신 보통)
//   /check?mock=LOW        → 최고 확률 0.35 (확신 낮음)
//   값이 없으면(?mock) 랜덤 결과가 나와요.

import { LABELS } from '../constants/violationTypes';

// 판별 기록에 함께 저장하는 모델 이름 (진짜 AI 결과와 구분돼요)
export const DEMO_MODEL_VERSION = 'demo';

const DEMO_REASON = '비상 시연 모드라서 AI가 사진을 보지 않고 정해진 결과를 보여 줘요.';

// 배열에서 하나를 무작위로 골라요.
function pickRandom(list) {
  return list[Math.floor(Math.random() * list.length)];
}

// 순서를 무작위로 섞은 새 배열을 돌려줘요.
function shuffle(list) {
  const copy = [...list];
  for (let i = copy.length - 1; i > 0; i -= 1) {
    const j = Math.floor(Math.random() * (i + 1));
    [copy[i], copy[j]] = [copy[j], copy[i]];
  }
  return copy;
}

// [라벨 순서, 확률 순서]를 받아서 { CROSSWALK: 0.9, ... } 모양으로 만들어요.
function toProbabilities(orderedLabels, values) {
  const probabilities = {};
  orderedLabels.forEach((label, i) => {
    probabilities[label] = values[i];
  });
  return probabilities;
}

// 주소창의 ?mock= 값을 읽어요. (예: 'CROSSWALK', 'MEDIUM', 'LOW', 없으면 null)
function getMockOption() {
  const value = new URLSearchParams(window.location.search).get('mock');
  return value ? value.toUpperCase() : null;
}

// 유형마다 하나씩 확률을 만들어요. 합계는 항상 1이에요.
function makeProbabilities(option) {
  // 특정 라벨을 강하게: 그 라벨 0.9
  if (LABELS.includes(option)) {
    const others = shuffle(LABELS.filter((label) => label !== option));
    return toProbabilities([option, ...others], [0.9, 0.04, 0.03, 0.02, 0.01]);
  }

  // 확신 보통: 1위 0.55, 2위 0.3 ("○○에도 해당될 수 있어요" 확인용)
  if (option === 'MEDIUM') {
    return toProbabilities(shuffle(LABELS), [0.55, 0.3, 0.08, 0.05, 0.02]);
  }

  // 확신 낮음: 1위 0.35
  if (option === 'LOW') {
    return toProbabilities(shuffle(LABELS), [0.35, 0.3, 0.15, 0.12, 0.08]);
  }

  // 옵션 없음: 한 라벨을 골라 0.3~0.95 사이 확률을 주고, 나머지를 랜덤하게 나눠요.
  const top = pickRandom(LABELS);
  const topValue = 0.3 + Math.random() * 0.65;
  const others = LABELS.filter((label) => label !== top);
  const weights = others.map(() => 0.2 + Math.random());
  const weightSum = weights.reduce((sum, w) => sum + w, 0);
  const restValues = weights.map((w) => ((1 - topValue) * w) / weightSum);
  // (나머지 중 하나가 더 커질 수도 있는데, 그래도 괜찮아요. 1위는 findTopLabel이 다시 찾아요.)
  return toProbabilities([top, ...others], [topValue, ...restValues]);
}

// 가장 확률이 높은 라벨을 찾아요.
function findTopLabel(probabilities) {
  return LABELS.reduce((best, label) =>
    probabilities[label] > probabilities[best] ? label : best,
  );
}

// 분석하는 것처럼 2~2.5초 기다린 뒤, 진짜 AI 결과와 같은 모양으로 돌려줘요.
// (분석 중 화면을 볼 수 있게 조금 기다려요. 취소하면 진짜 AI와 같은 CANCELLED 오류를 내요)
export async function analyzeDemo({ signal } = {}) {
  await new Promise((resolve, reject) => {
    const timer = setTimeout(resolve, 2000 + Math.random() * 500);
    signal?.addEventListener('abort', () => {
      clearTimeout(timer);
      const error = new Error('분석을 취소했어요.');
      error.code = 'CANCELLED';
      reject(error);
    });
  });
  const probabilities = makeProbabilities(getMockOption());
  const label = findTopLabel(probabilities);
  return {
    label,
    confidence: probabilities[label],
    probabilities,
    reason: DEMO_REASON,
    modelVersion: DEMO_MODEL_VERSION,
  };
}
