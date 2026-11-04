// 선(stroke) 아이콘이에요. 이모지 대신 써서 기기마다 모양이 달라지지 않게 해요.
// 사용법: <Icon name="camera" size={20} />  (색은 글자색(currentColor)을 따라가요)
// 새 아이콘은 아래 ICONS에 [태그, 속성] 목록으로 추가해요. (24×24 기준)
import styles from './Icon.module.css';

const ICONS = {
  car: [
    ['path', { d: 'M5 17h14v-5l-2-5H7l-2 5z' }],
    ['circle', { cx: 8, cy: 17, r: 2 }],
    ['circle', { cx: 16, cy: 17, r: 2 }],
  ],
  camera: [
    ['path', { d: 'M14.5 4h-5L7 7H4a2 2 0 0 0-2 2v9a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V9a2 2 0 0 0-2-2h-3l-2.5-3z' }],
    ['circle', { cx: 12, cy: 13, r: 3 }],
  ],
  image: [
    ['rect', { x: 3, y: 3, width: 18, height: 18, rx: 2 }],
    ['circle', { cx: 9, cy: 9, r: 2 }],
    ['path', { d: 'm21 15-3.1-3.1a2 2 0 0 0-2.8 0L6 21' }],
  ],
  home: [
    ['path', { d: 'm3 9 9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z' }],
    ['path', { d: 'M9 22V12h6v10' }],
  ],
  clipboard: [
    ['rect', { x: 8, y: 2, width: 8, height: 4, rx: 1 }],
    ['path', { d: 'M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2' }],
    ['path', { d: 'M9 12h6M9 16h6' }],
  ],
  info: [
    ['circle', { cx: 12, cy: 12, r: 10 }],
    ['path', { d: 'M12 16v-4M12 8h.01' }],
  ],
  // 판별 유형
  crosswalk: [['path', { d: 'M5 4v16M10 4v16M15 4v16M20 4v16' }]],
  sidewalk: [
    ['circle', { cx: 13, cy: 4, r: 2 }],
    ['path', { d: 'M10 22l2-7 3 3v4' }],
    ['path', { d: 'M8 12l3-4h3l2 4' }],
  ],
  hydrant: [
    ['path', { d: 'M8 21h8' }],
    ['path', { d: 'M9 21V9a3 3 0 0 1 6 0v12' }],
    ['path', { d: 'M6 13h3M15 13h3M8 9h8' }],
  ],
  parking: [
    ['rect', { x: 3, y: 3, width: 18, height: 18, rx: 3 }],
    ['path', { d: 'M9 17V7h4a3 3 0 0 1 0 6H9' }],
  ],
  // 주정차 금지 (P에 빗금)
  noParking: [
    ['circle', { cx: 12, cy: 12, r: 10 }],
    ['path', { d: 'M9.5 17V7h3.5a3 3 0 0 1 0 6H9.5' }],
    ['path', { d: 'm4.9 4.9 14.2 14.2' }],
  ],
  help: [
    ['circle', { cx: 12, cy: 12, r: 10 }],
    ['path', { d: 'M9.1 9a3 3 0 0 1 5.8 1c0 2-3 3-3 3M12 17h.01' }],
  ],
  // 그 밖의 아이콘
  clock: [
    ['circle', { cx: 12, cy: 12, r: 10 }],
    ['path', { d: 'M12 6v6l4 2' }],
  ],
  chevronRight: [['path', { d: 'm9 18 6-6-6-6' }]],
  chevronLeft: [['path', { d: 'm15 18-6-6 6-6' }]],
  check: [['path', { d: 'M20 6 9 17l-5-5' }]],
  checkCircle: [
    ['circle', { cx: 12, cy: 12, r: 10 }],
    ['path', { d: 'm9 12 2 2 4-4' }],
  ],
  alert: [
    ['path', { d: 'm21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3' }],
    ['path', { d: 'M12 9v4M12 17h.01' }],
  ],
  sparkles: [['path', { d: 'M12 3l1.9 5.1L19 10l-5.1 1.9L12 17l-1.9-5.1L5 10l5.1-1.9z' }]],
  lock: [
    ['rect', { x: 3, y: 11, width: 18, height: 11, rx: 2 }],
    ['path', { d: 'M7 11V7a5 5 0 0 1 10 0v4' }],
  ],
  lightbulb: [
    ['path', { d: 'M9 18h6M10 22h4' }],
    ['path', { d: 'M12 2a7 7 0 0 0-4 12.7V16h8v-1.3A7 7 0 0 0 12 2z' }],
  ],
  externalLink: [
    ['path', { d: 'M15 3h6v6M10 14 21 3' }],
    ['path', { d: 'M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6' }],
  ],
  rotate: [
    ['path', { d: 'M3 12a9 9 0 1 0 3-6.7L3 8' }],
    ['path', { d: 'M3 3v5h5' }],
  ],
  message: [['path', { d: 'M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z' }]],
  globe: [
    ['circle', { cx: 12, cy: 12, r: 10 }],
    ['path', { d: 'M2 12h20M12 2a15 15 0 0 1 0 20 15 15 0 0 1 0-20' }],
  ],
  smartphone: [
    ['rect', { x: 6, y: 2, width: 12, height: 20, rx: 2 }],
    ['path', { d: 'M11 18h2' }],
  ],
  inbox: [
    ['path', { d: 'M22 12h-6l-2 3h-4l-2-3H2' }],
    ['path', { d: 'M5.5 5.1 2 12v6a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-6l-3.5-6.9A2 2 0 0 0 16.8 4H7.2a2 2 0 0 0-1.7 1.1z' }],
  ],
  flask: [
    ['path', { d: 'M10 2v7.5L4.5 19a2 2 0 0 0 1.7 3h11.6a2 2 0 0 0 1.7-3L14 9.5V2' }],
    ['path', { d: 'M8.5 2h7M7 16h10' }],
  ],
  heart: [
    [
      'path',
      {
        d: 'M19 14c1.5-1.5 3-3.2 3-5.5A5.5 5.5 0 0 0 16.5 3c-1.8 0-3 .5-4.5 2-1.5-1.5-2.7-2-4.5-2A5.5 5.5 0 0 0 2 8.5c0 2.3 1.5 4 3 5.5l7 7z',
      },
    ],
  ],
  note: [
    ['path', { d: 'M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z' }],
    ['path', { d: 'M14 2v6h6M8 13h8M8 17h6' }],
  ],
  compass: [
    ['circle', { cx: 12, cy: 12, r: 10 }],
    ['path', { d: 'm16.2 7.8-2.1 6.3-6.3 2.1 2.1-6.3z' }],
  ],
};

export default function Icon({ name, size = 20, strokeWidth = 2, className = '' }) {
  const shapes = ICONS[name] ?? ICONS.help;
  return (
    <svg
      className={`${styles.icon} ${className}`}
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={strokeWidth}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      focusable="false"
    >
      {shapes.map(([Tag, attrs], index) => (
        <Tag key={index} {...attrs} />
      ))}
    </svg>
  );
}
