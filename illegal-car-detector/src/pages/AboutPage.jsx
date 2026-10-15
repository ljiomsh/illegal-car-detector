// 소개 화면 (/about): 서비스 목적, 작동 방식, 팀 소개, AI의 한계, 모델 정보
import styles from './AboutPage.module.css';

// 작동 방식 3단계
const HOW_IT_WORKS = [
  { icon: '📷', title: '사진 찍기', text: '주차된 차와 주변이 함께 나오게 사진을 찍어요.' },
  {
    icon: '📱',
    title: '기기 안에서 AI 분석',
    text: '사진은 서버로 보내지 않고, 내 휴대폰 안에서 AI가 분석해요.',
  },
  {
    icon: '🧭',
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
  'AI는 우리가 모은 학습 사진만큼만 알아요. 처음 보는 장소나 어두운 사진에서는 틀릴 수 있어요.',
  '표지판이나 노면 표시로 정해진 금지 구역은 AI가 알아보지 못할 수 있어요.',
  '최종 판단은 사람이 해요. 실제 위반 여부는 담당 기관이 판단해요.',
];

export default function AboutPage() {
  return (
    <div className={styles.page}>
      <title>소개 | 불법 주정차 판별·신고 도우미</title>
      <h1 tabIndex={-1} className={styles.heading}>서비스 소개</h1>

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
              <span className={styles.flowIcon} aria-hidden="true">
                {item.icon}
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
          <div className={styles.modelRow}>
            <dt>정확도</dt>
            <dd>준비 중</dd>
          </div>
          <div className={styles.modelRow}>
            <dt>학습 데이터 수</dt>
            <dd>준비 중</dd>
          </div>
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
    </div>
  );
}
