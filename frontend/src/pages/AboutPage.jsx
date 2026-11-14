// 소개 화면 (/about): 서비스 목적, 작동 방식, 팀 소개, AI의 한계, 모델 정보
import { AI_MODEL_NAME, AI_PROVIDER, EXTERNAL_AI_NOTICE } from '../constants/aiInfo.js';
import Icon from '../components/Icon.jsx';
import styles from './AboutPage.module.css';

// 작동 방식 3단계 (icon: components/Icon.jsx의 아이콘 이름)
const HOW_IT_WORKS = [
  { icon: 'camera', title: '사진 찍기', text: '주차된 차와 주변이 함께 나오게 사진을 찍어요.' },
  {
    icon: 'sparkles',
    title: 'AI 분석',
    text: `사진을 볼 수 있는 AI(${AI_PROVIDER} · ${AI_MODEL_NAME})가 우리가 정한 판별 기준에 따라 분석해요. 사진은 우리 서버에 저장하지 않아요.`,
  },
  {
    icon: 'compass',
    title: '안내 받기',
    text: '위반 유형, 위험한 이유, 신고 요건과 신고 방법을 알려 드려요.',
  },
];

// 팀원
const TEAM = [
  { role: '기획', name: '강동윤' },
  { role: '개발', name: '임채준' },
  { role: '디자인', name: '서한은' },
];

// AI의 한계
const LIMITS = [
  '사진만으로는 거리를 정확히 잴 수 없어요. 예를 들어 소화전에서 5m 이내인지는 직접 확인해야 해요.',
  'AI가 사진을 잘못 볼 수 있어요. 어둡거나 멀리서 찍은 사진, 차와 주변이 가려진 사진에서는 틀리기 쉬워요.',
  '같은 사진이라도 분석할 때마다 확률이 조금씩 다를 수 있어요.',
  '표지판이나 노면 표시로 정해진 금지 구역은 AI가 알아보지 못할 수 있어요.',
  '최종 판단은 사람이 해요. 실제 위반 여부는 담당 기관이 판단해요.',
];

// AI 모델 정보
const MODEL_INFO = [
  { term: '모델', value: `${AI_MODEL_NAME} (${AI_PROVIDER})` },
  { term: '종류', value: '사진을 볼 수 있는 대규모 언어 모델(비전 LLM)' },
  { term: '판별 방법', value: '라벨링 기준서의 5가지 정의를 AI에게 알려 주고, 유형별 확률과 판단 근거를 받아요.' },
  { term: '자체 평가', value: '정답을 매겨 둔 새 사진 112장 중 98장을 맞혔어요 (약 88%, 2026년 11월)' },
  { term: '사진 저장', value: '저장하지 않아요 (판별 결과 숫자만 기록)' },
];

export default function AboutPage() {
  return (
    <div className={styles.page}>
      <title>소개 | 길터</title>
      <h1 tabIndex={-1} className={styles.heading}>길터 소개</h1>

      <section className={styles.card} aria-labelledby="purpose-title">
        <h2 id="purpose-title" className={styles.title}>
          왜 만들었나요?
        </h2>
        <p>
          횡단보도·인도·소화전 주변의 불법 주정차는 보행자와 소방 활동을 위험하게 만들어요. 하지만
          어떤 경우가 신고 대상인지, 어떻게 신고해야 하는지 잘 모르는 사람이 많아요. 사진 한 장으로
          위반 여부를 확인하고 올바르게 신고할 수 있도록 돕고 싶어서 만들었어요.
        </p>
      </section>

      <section className={styles.card} aria-labelledby="how-title">
        <h2 id="how-title" className={styles.title}>
          어떻게 작동하나요?
        </h2>
        <ol className={styles.flow}>
          {HOW_IT_WORKS.map((item, index) => (
            <li key={item.title} className={styles.flowItem}>
              <span className={styles.flowIcon}>
                <Icon name={item.icon} size={22} />
              </span>
              <div>
                <h3 className={styles.flowTitle}>
                  {index + 1}. {item.title}
                </h3>
                <p className={styles.sub}>{item.text}</p>
              </div>
            </li>
          ))}
        </ol>
      </section>

      <section className={styles.card} aria-labelledby="limits-title">
        <h2 id="limits-title" className={styles.title}>
          AI의 한계
        </h2>
        <ul className={styles.bullets}>
          {LIMITS.map((limit) => (
            <li key={limit}>{limit}</li>
          ))}
        </ul>
      </section>

      <section className={styles.card} aria-labelledby="model-title">
        <h2 id="model-title" className={styles.title}>
          AI 모델 정보
        </h2>
        <dl className={styles.modelInfo}>
          {MODEL_INFO.map((row) => (
            <div key={row.term} className={styles.modelRow}>
              <dt>{row.term}</dt>
              <dd>{row.value}</dd>
            </div>
          ))}
        </dl>
      </section>

      <section className={styles.card} aria-labelledby="team-title">
        <h2 id="team-title" className={styles.title}>
          만든 사람들
        </h2>
        <p className={styles.teamName}>환일고등학교 「환일야호」</p>
        <ul className={styles.team}>
          {TEAM.map((member) => (
            <li key={member.name} className={styles.member}>
              <span className={styles.role}>{member.role}</span>
              <span className={styles.memberName}>{member.name}</span>
            </li>
          ))}
        </ul>
        <p className={styles.sub}>2026 AI동행 프로젝트 자율탐구 출품작</p>
      </section>

      <p className={styles.sub}>
        <Icon name="globe" size={16} /> {EXTERNAL_AI_NOTICE}
      </p>
    </div>
  );
}
