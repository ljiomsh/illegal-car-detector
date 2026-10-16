// 가짜(mock) AI 모델이에요. 진짜 Teachable Machine 모델이 준비되기 전까지 사용해요.
//
// 개발 테스트용 주소 옵션 (?mock=...)
//   /check?mock=CROSSWALK  → 횡단보도가 0.9로 나와요 (SIDEWALK, HYDRANT, NORMAL도 가능)
//   /check?mock=MEDIUM     → 최고 확률 0.55 (확신 보통)
//   /check?mock=LOW        → 최고 확률 0.35 (확신 낮음)
//   옵션이 없으면 랜덤 결과가 나와요.

import { LABELS } from '../constants/violationTypes';

export const MOCK_MODEL_VERSION = 'mock-0.1';

function delay(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

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

// 확률 4개를 만들어요. 합계는 항상 1이에요.
function makeProbabilities(option) {
  // 특정 라벨을 강하게: 그 라벨 0.9
  if (LABELS.includes(option)) {
    const others = shuffle(LABELS.filter((label) => label !== option));
    return toProbabilities([option, ...others], [0.9, 0.05, 0.03, 0.02]);
  }

  // 확신 보통: 1위 0.55, 2위 0.3 ("○○에도 해당될 수 있어요" 확인용)
  if (option === 'MEDIUM') {
    return toProbabilities(shuffle(LABELS), [0.55, 0.3, 0.1, 0.05]);
  }

  // 확신 낮음: 1위 0.35
  if (option === 'LOW') {
    return toProbabilities(shuffle(LABELS), [0.35, 0.3, 0.2, 0.15]);
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

// 모델 불러오기 흉내: 0.8초 뒤에 predict 함수를 가진 모델 객체를 돌려줘요.
export async function loadMockModel() {
  await delay(800);

  return {
    version: MOCK_MODEL_VERSION,
    // 진짜 모델과 모양을 맞추려고 사진(img 요소)을 받기만 하고 쓰지는 않아요.
    async predict(_imgElement) {
      await delay(600 + Math.random() * 400);
      const probabilities = makeProbabilities(getMockOption());
      const label = findTopLabel(probabilities);
      return {
        label,
        confidence: probabilities[label],
        probabilities,
      };
    },
  };
}
