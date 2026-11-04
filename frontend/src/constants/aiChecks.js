// AI가 사진에서 확인한 것(checks)을 화면에 보여 줄 때 쓰는 이름표예요.
// 키·값은 백엔드 NvidiaVisionClient.SYSTEM_PROMPT, AnalysisParser.CHECK_VALUES와 같아야 해요.
// 서버는 이 확인 결과로 아래 순서의 규칙을 적용해 유형을 정해요:
//   횡단보도 줄 위 → 소화전 5m 안 → 인도 위 → 횡단보도 10m 안·다른 금지 표시 → 정상

export const CHECK_ORDER_TEXT =
  '판별 순서: 횡단보도 줄 위 → 소화전 5m 안 → 인도 위 → 횡단보도 10m 안·다른 금지 표시 → 해당 없으면 정상';

export const CHECK_ITEMS = [
  {
    key: 'vehicle',
    label: '주차된 차',
    values: { parked: '있음', moving: '달리는 중', none: '없음', unclear: '판단하기 어려움' },
  },
  {
    key: 'crosswalk',
    label: '횡단보도',
    values: { on: '차 아래에 줄무늬', near: '10m 안 (줄 밖)', none: '안 보이거나 멀리 있음' },
    hits: ['on', 'near'],
  },
  {
    key: 'hydrant',
    label: '소화전',
    values: { near: '5m 안에 있음', far: '멀리 있음', none: '안 보임' },
    hits: ['near'],
  },
  {
    key: 'sidewalk',
    label: '인도',
    values: { on: '바퀴가 보도 위', none: '바퀴가 차도에 있음' },
    hits: ['on'],
  },
  {
    key: 'noParking',
    label: '다른 금지 표시',
    values: {
      yellow_line: '노란 선',
      intersection: '교차로 모퉁이',
      bus_stop: '버스 정류소',
      school_zone: '어린이보호구역',
      fire_lane: '소방차 전용구역',
      sign: '주정차 금지 표지판',
      none: '없음',
    },
  },
];

// 화면에 보여 줄 줄 목록: [{ key, label, text, important }]
// important: 위반의 근거가 된 항목이거나, 차가 없는 등 결과를 믿기 어려운 이유인지
export function describeChecks(checks) {
  if (!checks) return [];
  // 차가 없으면 나머지 항목(바퀴 위치 등)은 뜻이 없어서 "주차된 차: 없음"만 보여 줘요.
  const items = checks.vehicle === 'none' ? CHECK_ITEMS.slice(0, 1) : CHECK_ITEMS;
  return items.filter((item) => checks[item.key]).map((item) => {
    const value = checks[item.key];
    const important =
      item.key === 'vehicle' ? value !== 'parked' : item.hits ? item.hits.includes(value) : value !== 'none';
    return { key: item.key, label: item.label, text: item.values[value] ?? value, important };
  });
}
