// 사진 판별 AI의 정보와 화면 안내 문구를 모아 둔 곳이에요.
// 판별은 서버(/api/analyze)가 비전 LLM(NVIDIA Build)에 사진을 보내서 해요.
// ★ 백엔드 application.yaml의 nvidia.model을 바꾸면 아래 AI_MODEL_NAME도 같이 바꿔 주세요.

export const AI_PROVIDER = 'NVIDIA Build';
export const AI_MODEL_NAME = 'Nemotron 3 Nano Omni';

// 사진이 어디로 가는지 알려 주는 개인정보 안내
export const PRIVACY_NOTICE = `사진은 분석할 때만 외부 AI(${AI_PROVIDER})로 보내지고, 우리 서버에는 저장하지 않아요.`;

// 결과 화면·소개 화면 아래에 보여 주는 안내
export const EXTERNAL_AI_NOTICE = `사진 분석은 외부 AI(${AI_PROVIDER} · ${AI_MODEL_NAME})가 해요. 번호판·얼굴이 보이지 않는 사진을 사용해 주세요.`;
