// 사진을 판별해요: 사진을 줄여서 서버(/api/analyze)로 보내면, 서버가 비전 LLM에게 물어봐요.
//
// 사용법:
//   const result = await analyzePhoto(imgElement);
//   → { label, confidence, probabilities, reason, modelVersion }
//
// ★ 비상 시연 모드: 주소에 ?mock=CROSSWALK 처럼 mock 옵션이 있으면 AI를 부르지 않고
//   정해진 결과(mocks/demoAnalysis.js)를 보여 줘요. (발표 중 인터넷이나 AI가 안 될 때)

import { analyzeImage } from './client.js';
import { LABELS } from '../constants/violationTypes.js';
import { analyzeDemo } from '../mocks/demoAnalysis.js';
import { imageToJpegBlob } from '../utils/resizeImage.js';

// 서버로 보내는 사진 크기: 긴 변 1024px JPEG(품질 0.85)
const MAX_SIDE = 1024;
const JPEG_QUALITY = 0.85;

// 지금 주소가 비상 시연 모드인지
export function isDemoMode() {
  return new URLSearchParams(window.location.search).has('mock');
}

export async function analyzePhoto(imgElement) {
  if (isDemoMode()) {
    return analyzeDemo();
  }

  const blob = await imageToJpegBlob(imgElement, MAX_SIDE, JPEG_QUALITY);
  const result = await analyzeImage(blob);
  if (!LABELS.includes(result?.label)) {
    throw new Error('AI 응답이 올바르지 않아요.');
  }
  return {
    label: result.label,
    confidence: result.confidence,
    probabilities: result.probabilities,
    reason: result.reason ?? null,
    modelVersion: result.modelVersion,
  };
}
