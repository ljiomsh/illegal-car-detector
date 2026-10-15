// AI 모델을 불러오고, 사진을 판별하는 predict 함수를 주는 hook이에요.
//
// 사용법:
//   const { status, predict } = useModel();
//   status: 'loading' | 'ready' | 'error'
//   predict(imgElement) → Promise<{ label, confidence, probabilities }>
//
// ★ 지금은 가짜 모델(mocks/mockModel.js)을 써요.
//   진짜 Teachable Machine 모델로 바꿀 때는 이 파일의 loadModel 함수와 MODEL_VERSION만 바꾸면 돼요.
//   (자세한 방법은 README.md의 "mock을 실제 모델로 바꾸기"를 보세요.)

import { useCallback, useEffect, useRef, useState } from 'react';
import { loadMockModel, MOCK_MODEL_VERSION } from '../mocks/mockModel';

// 서버에 판별 기록을 보낼 때 함께 보내는 모델 버전 이름
export const MODEL_VERSION = MOCK_MODEL_VERSION;

// 모델을 불러와서 { predict(imgElement) } 모양의 객체를 돌려줘요.
function loadModel() {
  return loadMockModel();
}

// 모델은 한 번만 불러오고 계속 재사용해요. (판별 화면에 다시 들어와도 또 불러오지 않게)
let modelPromise = null;

function getModel() {
  if (!modelPromise) {
    modelPromise = loadModel().catch((error) => {
      modelPromise = null; // 실패하면 다음에 다시 시도할 수 있게 비워 둬요.
      throw error;
    });
  }
  return modelPromise;
}

export default function useModel() {
  const [status, setStatus] = useState('loading');
  const modelRef = useRef(null);

  useEffect(() => {
    let cancelled = false; // 화면을 벗어난 뒤에는 상태를 바꾸지 않도록

    getModel()
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
  }, []);

  const predict = useCallback(async (imgElement) => {
    if (!modelRef.current) {
      throw new Error('AI 모델이 아직 준비되지 않았어요.');
    }
    return modelRef.current.predict(imgElement);
  }, []);

  return { status, predict };
}
