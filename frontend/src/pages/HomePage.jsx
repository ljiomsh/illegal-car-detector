// 홈 화면 (/): 시작 버튼 + 진행 3단계 + 5가지 유형 + 판별 수 요약 + 개인정보 안내
import { useEffect, useState } from 'react';
import { Link } from 'react-router';
import { getStats } from '../api/client';
import { getViolationType, LABELS } from '../constants/violationTypes';
import { PRIVACY_NOTICE } from '../constants/aiInfo.js';
import Icon from '../components/Icon.jsx';
import styles from './HomePage.module.css';

// 판별이 어떻게 진행되는지 3단계
const FLOW = [
  { title: '사진 찍기', text: '차와 주변이 함께 나오게' },
  { title: 'AI 판별', text: '5가지 유형 중 하나로' },
  { title: '신고 안내', text: '요건 확인 후 안전신문고로' },
];

export default function HomePage() {
  const [stats, setStats] = useState(null); // 판별 수 요약 (못 불러오면 그 칸만 안 보여요)

  useEffect(() => {
    let cancelled = false;
    getStats()
      .then((data) => {
        if (!cancelled) setStats(data);
      })
      .catch((error) => console.warn('판별 수를 불러오지 못했어요:', error));
    return () => {
      cancelled = true;
    };
  }, []);

  const violationCount = stats ? stats.total - (stats.byType?.NORMAL ?? 0) : 0;

  return (
    <div className={styles.page}>
      <title>길터 - 불법 주정차 판별·신고 도우미</title>

      <section className={styles.hero}>
        <h1 tabIndex={-1} className={styles.heading}>
          사진 한 장으로 불법 주정차인지 확인하고 올바르게 신고하세요
        </h1>
        <p className={styles.heroSub}>
          <Icon name="clock" size={16} /> AI가 10~40초 안에 판별해요
        </p>
        <Link to="/check" className={styles.heroButton}>
          <Icon name="camera" /> 사진으로 확인하기
        </Link>
      </section>

      <section aria-labelledby="flow-title">
        <h2 id="flow-title" className={styles.sectionTitle}>
          이렇게 진행돼요
        </h2>
        <ol className={styles.flow}>
          {FLOW.map((step, index) => (
            <li key={step.title} className={styles.flowItem}>
              <span className={styles.flowNumber} aria-hidden="true">
                {index + 1}
              </span>
              <span className={styles.flowTitle}>{step.title}</span>
              <span className={styles.flowText}>{step.text}</span>
            </li>
          ))}
        </ol>
      </section>

      <section aria-labelledby="types-title">
        <h2 id="types-title" className={styles.sectionTitle}>
          이런 경우를 알려 드려요
        </h2>
        <ul className={styles.typeGrid}>
          {LABELS.map((label) => {
            const type = getViolationType(label);
            return (
              <li
                key={label}
                className={styles.typeCard}
                style={{ '--type-soft': type.softColor, '--type-text': type.textColor }}
              >
                <span className={styles.typeIcon}>
                  <Icon name={type.icon} size={22} />
                </span>
                <div>
                  <h3 className={styles.typeName}>{type.title}</h3>
                  <p className={styles.typeDesc}>{type.description}</p>
                </div>
              </li>
            );
          })}
        </ul>
      </section>

      {stats?.total > 0 && (
        <Link to="/history" className={styles.statsLink}>
          <Icon name="clipboard" size={22} className={styles.statsIcon} />
          <span className={styles.statsText}>
            <span className={styles.nowrap}>
              지금까지 <strong>{stats.total}건</strong> 판별했어요
            </span>{' '}
            ·{' '}
            <span className={styles.nowrap}>
              위반 의심 <strong className={styles.statsViolation}>{violationCount}건</strong>
            </span>
          </span>
          <Icon name="chevronRight" className={styles.chevron} />
        </Link>
      )}

      <p className={styles.privacy}>
        <Icon name="lock" size={16} /> {PRIVACY_NOTICE}
      </p>
    </div>
  );
}
