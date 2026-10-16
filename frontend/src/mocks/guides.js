// TODO: 제출 전 안전신문고 공지·도로교통법으로 재확인

// 유형별 안내 문구예요.
// - 지금은 mockApi가 이 문구로 서버 응답을 흉내 내요.
// - 나중에 진짜 서버를 붙인 뒤에도, 서버 요청이 실패하면 화면이 이 문구를 대신 보여 줘요(fallback).

import { getConfidenceLevel } from '../constants/violationTypes';

export const SAFETY_REPORT_URL = 'https://www.safetyreport.go.kr';

// 모든 위반 유형에 공통인 신고 요건
// (주민신고제는 안전신문고 앱 카메라로 찍은 사진만 인정해요. 갤러리 사진·동영상은 안 돼요.)
const COMMON_CHECKLIST = [
  { id: 'plate', text: '사진 2장 모두 차량 번호판이 또렷하게 보이나요?' },
  { id: 'scene', text: '위반 장소(횡단보도·인도·소화전)와 차량이 한 사진에 함께 보이나요?' },
  {
    id: 'interval',
    text: '안전신문고 앱 카메라로, 같은 위치·같은 방향에서 1분 이상 간격을 두고 2장 이상 찍었나요?',
  },
];

// 공통 신고 단계. typeName 자리에 유형 이름이 들어가요.
// 주민신고제 사진은 앱 카메라로만 찍을 수 있어서 웹사이트 대신 앱을 안내해요.
function makeReportSteps(typeName) {
  return [
    '휴대폰에서 안전신문고 앱을 열어요.',
    "'신고하기'를 눌러요.",
    "'불법 주정차'를 골라요.",
    `위반 유형 목록에서 '${typeName}' 항목을 골라요.`,
    '앱 카메라로 차량 번호판과 위반 장소가 함께 보이게 1장을 찍고, 1분 이상 지난 뒤 같은 위치·같은 방향에서 1장을 더 찍어요.',
    '지도에서 위반 위치가 맞는지 확인해요.',
    '내용을 확인하고 제출해요. 찍은 다음 날까지 신고해야 해요.',
  ];
}

export const GUIDES = {
  CROSSWALK: {
    title: '횡단보도 위 주정차',
    violation: true,
    dangerReasons: [
      '보행자가 차를 피해 차도로 돌아서 걸어야 해서 사고가 날 수 있어요.',
      '차에 가려서 운전자가 길을 건너는 보행자, 특히 키가 작은 어린이를 보지 못할 수 있어요.',
      '휠체어나 유모차는 차 옆으로 지나가기 어려워 횡단보도를 쓰지 못해요.',
    ],
    checklist: COMMON_CHECKLIST,
    reportSteps: makeReportSteps('횡단보도'),
  },
  SIDEWALK: {
    title: '인도 위 주정차',
    violation: true,
    dangerReasons: [
      '휠체어·유모차를 이용하는 사람과 시각장애인이 지나갈 길이 막혀요.',
      '보행자가 차를 피해 차도로 내려가 걸어야 해서 차에 부딪힐 위험이 커져요.',
    ],
    checklist: [
      ...COMMON_CHECKLIST,
      { id: 'sidewalk-curb', text: '연석 등으로 차도와 확실히 구분된 인도인가요?' },
      {
        id: 'sidewalk-hours',
        text: '인도는 지역마다 신고할 수 있는 시간이 달라요(예: 오전 8시~오후 9시). 지금 신고할 수 있는 시간인가요?',
      },
    ],
    reportSteps: makeReportSteps('보도(인도)'),
  },
  HYDRANT: {
    title: '소화전 주변 주정차',
    violation: true,
    dangerReasons: [
      '불이 났을 때 소방차가 소화전에 호스를 연결하지 못해 불을 끄는 시간이 늦어져요.',
      '소방차가 물을 구하러 다른 곳을 찾는 사이 불이 더 크게 번질 수 있어요.',
    ],
    checklist: [
      ...COMMON_CHECKLIST,
      { id: 'hydrant-distance', text: '차가 소화전에서 5m 이내에 있나요?' },
    ],
    reportSteps: makeReportSteps('소화전'),
  },
  NORMAL: {
    title: '정상 주차',
    violation: false,
    dangerReasons: [
      'AI가 보기에는 횡단보도·인도·소화전 주변에 세운 차가 아니에요.',
      'AI의 판단은 참고용이에요. 표지판이나 노면 표시(노란 선 등)로 주정차가 금지된 곳일 수도 있어요.',
      '주변 표지판과 바닥 표시를 한 번 더 확인해 주세요.',
    ],
    checklist: [],
    reportSteps: [],
  },
};

// 사진 찍는 요령 (사진 고르기 화면과 '다시 찍기' 안내에서 같이 써요)
export const PHOTO_TIPS = [
  '차량과 횡단보도·인도·소화전이 한 사진에 함께 보이게 찍어요.',
  '너무 가까이 찍지 말고 한두 걸음 물러나서 주변이 보이게 찍어요.',
  '밝은 곳에서 흔들리지 않게 찍어요.',
  '차도에 들어가지 말고 안전한 곳에서 찍어요.',
];

// 모델 결과로 JudgmentResponse 모양의 안내를 만들어요.
// judgmentId는 서버가 정해 주는 값이라 여기서는 null이에요.
export function buildGuideResponse({ label, confidence }) {
  const guide = GUIDES[label] ?? GUIDES.NORMAL;
  return {
    judgmentId: null,
    type: label,
    title: guide.title,
    violation: guide.violation,
    confidenceLevel: getConfidenceLevel(confidence),
    dangerReasons: guide.dangerReasons,
    checklist: guide.checklist,
    reportSteps: guide.reportSteps,
    reportUrl: SAFETY_REPORT_URL,
  };
}
