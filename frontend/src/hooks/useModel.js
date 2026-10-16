// AI 모델을 준비하고, 사진을 판별하는 predict 함수를 주는 hook이에요.
//
// 사용법:
//   const { status, predict } = useModel();
//   status: 'loading' | 'ready' | 'error'
//   predict(imgElement) → Promise<{ label, confidence, probabilities, reason?, modelVersion? }>
//
// 두 가지 방식(모드)이 있어요. (constants/aiMode.js)
//   - api 모드(VITE_MODEL_MODE=api): 사진을 서버(/api/analyze)로 보내 외부 AI(NVIDIA Build)가 판별해요.
//     불러올 모델이 없어서 status는 바로 'ready'예요.
//   - mock 모드(그 외): 가짜 모델(mocks/mockModel.js)이 결과를 만들어요.
//   ★ 주소에 ?mock=CROSSWALK 처럼 mock 옵션이 있으면 모드와 상관없이 mock을 써요.
//     (발표 중 인터넷이 안 될 때 비상용)

import { useCallback, useEffect, useRef, useState } from 'react';
import { analyzeImage } from '../api/client.js';
import { IS_API_MODE } from '../constants/aiMode.js';
import { LABELS } from '../constants/violationTypes.js';
import { loadMockModel, MOCK_MODEL_VERSION } from '../mocks/mockModel.js';
import { imageToJpegBlob } from '../utils/resizeImage.js';

// mock 모델일 때 서버에 판별 기록을 보내며 함께 보내는 모델 버전 이름
// (api 모드에서는 서버가 알려 준 modelVersion을 써요)
export const MODEL_VERSION = MOCK_MODEL_VERSION;

// 지금 화면에서 외부 AI를 쓸지 정해요.
function shouldUseApi() {
  const hasMockOption = new URLSearchParams(window.location.search).has('mock');
  return IS_API_MODE && !hasMockOption;
}

// 외부 AI 판별: 사진을 긴 변 1024px JPEG(품질 0.85)로 만들어 서버로 보내요.
async function predictWithApi(imgElement) {
  const blob = await imageToJpegBlob(imgElement, 1024, 0.85);
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

// mock 모델은 한 번만 불러오고 계속 재사용해요. (판별 화면에 다시 들어와도 또 불러오지 않게)
let modelPromise = null;

function getMockModel() {
  if (!modelPromise) {
    modelPromise = loadMockModel().catch((error) => {
      modelPromise = null; // 실패하면 다음에 다시 시도할 수 있게 비워 둬요.
      throw error;
    });
  }
  return modelPromise;
}

export default function useModel() {
  // 화면이 처음 열릴 때 한 번만 정해요.
  const [useApi] = useState(shouldUseApi);
  const [status, setStatus] = useState(useApi ? 'ready' : 'loading');
  const modelRef = useRef(null);

  useEffect(() => {
    if (useApi) return undefined; // 외부 AI는 불러올 모델이 없어요.

    let cancelled = false; // 화면을 벗어난 뒤에는 상태를 바꾸지 않도록

    getMockModel()
      .then((model) => {
        if (cancelled) return;
        modelRef.current = model;
        setStatus('ready');
      })
      .catch((error) => {
        if (cancelled) return;
        console.error('모델을 불러오지 못했어요:', error);
        setStatus('error');
      });

    return () => {
      cancelled = true;
    };
  }, [useApi]);

  const predict = useCallback(
    async (imgElement) => {
      if (useApi) {
        return predictWithApi(imgElement);
      }
      if (!modelRef.current) {
        throw new Error('AI 모델이 아직 준비되지 않았어요.');
      }
      return modelRef.current.predict(imgElement);
    },
    [useApi],
  );

  return { status, predict };
}
