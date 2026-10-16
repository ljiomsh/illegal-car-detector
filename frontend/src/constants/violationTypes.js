// 판별 유형(라벨)별 한글 이름·아이콘·색상을 모아 둔 곳이에요.
// 아이콘을 나중에 SVG로 바꾸고 싶으면 이 파일의 icon 값만 바꾸면 돼요.

// AI 모델이 내놓는 라벨 4가지 (순서 = 화면에 보여 주는 기본 순서)
export const LABELS = ['CROSSWALK', 'SIDEWALK', 'HYDRANT', 'NORMAL'];

export const VIOLATION_TYPES = {
  CROSSWALK: {
    label: 'CROSSWALK',
    name: '횡단보도',
    title: '횡단보도 위 주정차',
    icon: '🚸',
    description: '횡단보도 위에 차가 서 있어요',
    violation: true,
    color: 'var(--color-danger)',
    textColor: 'var(--color-danger-text)',
    softColor: 'var(--color-danger-soft)',
  },
  SIDEWALK: {
    label: 'SIDEWALK',
    name: '인도',
    title: '인도 위 주정차',
    icon: '🚶',
    description: '사람이 걷는 인도 위에 차가 서 있어요',
    violation: true,
    color: 'var(--color-danger)',
    textColor: 'var(--color-danger-text)',
    softColor: 'var(--color-danger-soft)',
  },
  HYDRANT: {
    label: 'HYDRANT',
    name: '소화전',
    title: '소화전 주변 주정차',
    icon: '🧯',
    description: '소화전 가까이에 차가 서 있어요',
    violation: true,
    color: 'var(--color-danger)',
    textColor: 'var(--color-danger-text)',
    softColor: 'var(--color-danger-soft)',
  },
  NORMAL: {
    label: 'NORMAL',
    name: '정상 주차',
    title: '정상 주차',
    icon: '✅',
    description: '금지 구역이 아닌 곳에 세운 차예요',
    violation: false,
    color: 'var(--color-safe)',
    textColor: 'var(--color-safe-text)',
    softColor: 'var(--color-safe-soft)',
  },
};

// 라벨로 유형 정보를 찾아요. 모르는 라벨이면 undefined 대신 기본값을 돌려줘요.
export function getViolationType(label) {
  return (
    VIOLATION_TYPES[label] ?? {
      label,
      name: '알 수 없음',
      title: '알 수 없는 유형',
      icon: '❔',
      description: '',
      violation: false,
      color: 'var(--color-border-strong)',
      textColor: 'var(--color-text-sub)',
      softColor: 'var(--color-bg)',
    }
  );
}

// 확신도 기준: 0.7 이상 HIGH, 0.4 이상 MEDIUM, 그 아래는 LOW
export const CONFIDENCE_HIGH = 0.7;
export const CONFIDENCE_MEDIUM = 0.4;

export function getConfidenceLevel(confidence) {
  if (confidence >= CONFIDENCE_HIGH) return 'HIGH';
  if (confidence >= CONFIDENCE_MEDIUM) return 'MEDIUM';
  return 'LOW';
}

export const CONFIDENCE_LEVEL_NAMES = {
  HIGH: '높음',
  MEDIUM: '보통',
  LOW: '낮음',
};

// 0.873 → 87 처럼 확률을 퍼센트 정수로 바꿔요.
export function toPercent(value) {
  return Math.round((Number(value) || 0) * 100);
}
