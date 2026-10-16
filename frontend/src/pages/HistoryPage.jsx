// 기록 화면 (/history): 유형별 통계 + 최근 판별 기록 목록
import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router';
import { getJudgments, getStats } from '../api/client';
import { getViolationType, LABELS, toPercent } from '../constants/violationTypes';
import { timeAgo } from '../utils/timeAgo';
import btn from '../styles/buttons.module.css';
import styles from './HistoryPage.module.css';

export default function HistoryPage() {
  const [status, setStatus] = useState('loading'); // 'loading' | 'ready' | 'error'
  const [stats, setStats] = useState(null);
  const [judgments, setJudgments] = useState([]);
  const [reloadCount, setReloadCount] = useState(0); // "다시 시도"를 누르면 1씩 늘려서 다시 불러와요
  const headingRef = useRef(null);
  const retriedRef = useRef(false);

  // "다시 시도" 후 결과가 나오면 제목으로 포커스를 옮겨요. (누른 버튼이 사라져서 포커스를 잃지 않게)
  useEffect(() => {
    if (status !== 'loading' && retriedRef.current) {
      retriedRef.current = false;
      headingRef.current?.focus();
    }
  }, [status]);

  useEffect(() => {
    let cancelled = false;

    // 통계와 목록을 동시에 불러와요.
    Promise.all([getStats(), getJudgments({ limit: 20 })])
      .then(([statsData, judgmentsData]) => {
        if (cancelled) return;
        setStats(statsData);
        setJudgments(judgmentsData);
        setStatus('ready');
      })
      .catch((error) => {
        if (cancelled) return;
        console.error('기록을 불러오지 못했어요:', error);
        setStatus('error');
      });

    return () => {
      cancelled = true;
    };
  }, [reloadCount]);

  function retry() {
    retriedRef.current = true;
    setStatus('loading');
    setReloadCount((count) => count + 1);
  }

  return (
    <div className={styles.page}>
      <title>판별 기록 | 불법 주정차 판별·신고 도우미</title>
      <h1 ref={headingRef} tabIndex={-1} className={styles.heading}>
        판별 기록
      </h1>

      {status === 'loading' && (
        <p className={styles.message} role="status">
          기록을 불러오고 있어요…
        </p>
      )}

      {status === 'error' && (
        <div className={styles.errorBox} role="alert">
          <p>기록을 불러오지 못했어요.</p>
          <button type="button" className={btn.secondary} onClick={retry}>
            다시 시도
          </button>
        </div>
      )}

      {status === 'ready' && stats?.total === 0 && <EmptyState />}

      {status === 'ready' && stats?.total > 0 && (
        <>
          <section aria-labelledby="stats-title">
            <h2 id="stats-title" className={styles.sectionTitle}>
              유형별 판별 횟수 <span className={styles.total}>(전체 {stats.total}건)</span>
            </h2>
            <ul className={styles.statGrid}>
              {LABELS.map((label) => {
                const type = getViolationType(label);
                return (
                  <li key={label} className={styles.statCard}>
                    <span className={styles.statIcon} aria-hidden="true">
                      {type.icon}
                    </span>
                    <span className={styles.statName}>{type.name}</span>
                    <span className={styles.statCount}>
                      {stats.byType?.[label] ?? 0}
                      <span className={styles.statUnit}>건</span>
                    </span>
                  </li>
                );
              })}
            </ul>
          </section>

          <section aria-labelledby="recent-title">
            <h2 id="recent-title" className={styles.sectionTitle}>
              최근 기록
            </h2>
            <ul className={styles.list}>
              {judgments.map((item) => (
                <HistoryItem key={item.judgmentId} item={item} />
              ))}
            </ul>
          </section>
        </>
      )}
    </div>
  );
}

// 기록 목록의 한 줄
function HistoryItem({ item }) {
  const type = getViolationType(item.label);
  const actualType = item.feedback?.actualLabel ? getViolationType(item.feedback.actualLabel) : null;

  return (
    <li className={styles.item}>
      <span className={styles.itemIcon} aria-hidden="true">
        {type.icon}
      </span>
      <div className={styles.itemBody}>
        <p className={styles.itemTitle}>{type.title}</p>
        <p className={styles.itemMeta}>
          확신도 {toPercent(item.confidence)}% ·{' '}
          <time dateTime={item.createdAt}>{timeAgo(item.createdAt)}</time>
        </p>
        {item.feedback && (
          <p className={styles.feedbackTag}>
            <span aria-hidden="true">💬</span> 피드백 보냄
            {actualType ? ` · 실제: ${actualType.title}` : ' · 실제 유형 모름'}
          </p>
        )}
      </div>
    </li>
  );
}

// 기록이 하나도 없을 때
function EmptyState() {
  return (
    <section className={styles.empty}>
      <span className={styles.emptyIcon} aria-hidden="true">
        📭
      </span>
      <p className={styles.emptyTitle}>아직 판별 기록이 없어요</p>
      <p className={styles.message}>사진으로 첫 판별을 해 보세요.</p>
      <Link to="/check" className={`${btn.primary} ${btn.large}`}>
        <span aria-hidden="true">📷</span> 사진으로 확인하기
      </Link>
    </section>
  );
}
