// AI 판별 방식(모드)과 그에 따라 바뀌는 안내 문구를 모아 둔 곳이에요.
// - VITE_MODEL_MODE=api  → 서버(/api/analyze)를 거쳐 외부 AI(NVIDIA Build)가 사진을 분석해요.
// - 그 외               → 가짜(mock) 모델이 기기 안에서 결과를 만들어요.
// (주소에 ?mock=... 이 있으면 모드와 상관없이 mock을 써요. → hooks/useModel.js)

export const IS_API_MODE = import.meta.env.VITE_MODEL_MODE === 'api';

// 사진이 어디로 가는지 알려 주는 개인정보 안내 (모드마다 사실이 달라서 나눠 둬요)
export const PRIVACY_NOTICE = IS_API_MODE
  ? '사진은 분석할 때만 외부 AI(NVIDIA Build)로 보내지고, 우리 서버에는 저장하지 않아요.'
  : '사진은 내 기기 안에서만 분석되며 서버로 전송되지 않아요.';

// 외부 AI를 쓸 때 화면 아래에 보여 주는 안내
export const EXTERNAL_AI_NOTICE =
  '현재 버전은 외부 AI(NVIDIA Build · DeepSeek V4.1 Flash)로 사진을 분석해요. 번호판·얼굴이 보이지 않는 사진을 사용해 주세요.';
