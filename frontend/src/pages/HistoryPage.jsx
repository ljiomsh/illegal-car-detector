// 기록 화면 (/history): 유형별 통계(막대 하나) + 유형 필터 + 날짜별 판별 기록 목록
import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router';
import { getJudgments, getStats } from '../api/client';
import { getViolationType, LABELS, toPercent } from '../constants/violationTypes';
import { groupByDay, timeOfDay } from '../utils/dateGroups';
import { timeAgo } from '../utils/timeAgo';
import Icon from '../components/Icon.jsx';
import btn from '../styles/buttons.module.css';
import styles from './HistoryPage.module.css';

// 최근 기록을 몇 개까지 불러올지 (서버는 최대 100)
const RECENT_LIMIT = 50;

// 유형 필터 (ALL = 전체)
const FILTERS = [{ value: 'ALL', label: '전체' }, ...LABELS.map((label) => ({ value: label, label: getViolationType(label).name }))];

export default function HistoryPage() {
  const [status, setStatus] = useState('loading'); // 'loading' | 'ready' | 'error'
  const [stats, setStats] = useState(null);
  const [judgments, setJudgments] = useState([]);
  const [filter, setFilter] = useState('ALL');
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
    Promise.all([getStats(), getJudgments({ limit: RECENT_LIMIT })])
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

  const filtered = filter === 'ALL' ? judgments : judgments.filter((item) => item.label === filter);
  const groups = groupByDay(filtered, (item) => item.createdAt);

  return (
    <div className={styles.page}>
      <title>판별 기록 | 길터</title>
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
          <StatsSummary stats={stats} />

          <section aria-labelledby="recent-title" className={styles.recent}>
            <h2 id="recent-title" className={styles.sectionTitle}>
              최근 기록
            </h2>

            <div className={styles.filters} role="group" aria-label="유형으로 거르기">
              {FILTERS.map((option) => (
                <button
                  key={option.value}
                  type="button"
                  className={filter === option.value ? `${styles.chip} ${styles.chipOn}` : styles.chip}
                  aria-pressed={filter === option.value}
                  onClick={() => setFilter(option.value)}
                >
                  {option.label}
                </button>
              ))}
            </div>

            {groups.length === 0 ? (
              <p className={styles.emptyFilter}>이 유형의 기록이 없어요.</p>
            ) : (
              <div className={styles.dayGroups}>
                {groups.map((group) => (
                  <section key={group.key} aria-labelledby={`day-${group.key}`}>
                    <h3 id={`day-${group.key}`} className={styles.dayTitle}>
                      {group.label}
                      <span className={styles.dayCount}>{group.items.length}건</span>
                    </h3>
                    <ul className={styles.list}>
                      {group.items.map((item) => (
                        <HistoryItem key={item.judgmentId} item={item} isToday={group.label === '오늘'} />
                      ))}
                    </ul>
                  </section>
                ))}
              </div>
            )}
            {judgments.length >= RECENT_LIMIT && (
              <p className={styles.more}>최근 {RECENT_LIMIT}건까지 보여 줘요.</p>
            )}
          </section>
        </>
      )}
    </div>
  );
}

// 유형별 판별 횟수: 막대 하나에 유형별 비율 + 아래에 이름과 개수
function StatsSummary({ stats }) {
  const description = LABELS.map((label) => `${getViolationType(label).name} ${stats.byType?.[label] ?? 0}건`).join(', ');

  return (
    <section aria-labelledby="stats-title" className={styles.stats}>
      <div className={styles.statsHead}>
        <h2 id="stats-title" className={styles.statsTitle}>
          유형별 판별 횟수
        </h2>
        <span className={styles.total}>
          전체 <strong>{stats.total}</strong>건
        </span>
      </div>
      <div className={styles.stack} role="img" aria-label={description}>
        {LABELS.map((label) => {
          const count = stats.byType?.[label] ?? 0;
          if (count === 0) return null;
          return (
            <span
              key={label}
              className={styles.stackPart}
              style={{ flexGrow: count, background: getViolationType(label).chartColor }}
            />
          );
        })}
      </div>
      <ul className={styles.legend} aria-hidden="true">
        {LABELS.map((label) => {
          const type = getViolationType(label);
          return (
            <li key={label} className={styles.legendItem}>
              <span className={styles.swatch} style={{ background: type.chartColor }} />
              {type.name}
              <strong className={styles.legendCount}>{stats.byType?.[label] ?? 0}</strong>
            </li>
          );
        })}
      </ul>
    </section>
  );
}

// 기록 목록의 한 줄 (오늘 기록은 "3분 전", 그 전 기록은 "오후 3:24"처럼 시각을 보여 줘요)
function HistoryItem({ item, isToday }) {
  const type = getViolationType(item.label);
  const actualType = item.feedback?.actualLabel ? getViolationType(item.feedback.actualLabel) : null;

  return (
    <li className={styles.item}>
      <span
        className={styles.itemIcon}
        style={{ '--type-soft': type.softColor, '--type-text': type.textColor }}
      >
        <Icon name={type.icon} size={20} />
      </span>
      <div className={styles.itemBody}>
        <p className={styles.itemTitle}>{type.title}</p>
        <p className={styles.itemMeta}>
          <time dateTime={item.createdAt}>
            {isToday ? timeAgo(item.createdAt) : timeOfDay(item.createdAt)}
          </time>
        </p>
        {item.feedback && (
          <p className={styles.feedbackTag}>
            <Icon name="message" size={14} /> 피드백 보냄
            {actualType ? ` · 실제: ${actualType.title}` : ' · 실제 유형 모름'}
          </p>
        )}
      </div>
      <span className={styles.badge}>
        <span className="sr-only">확신도 </span>
        {toPercent(item.confidence)}%
      </span>
    </li>
  );
}

// 기록이 하나도 없을 때
function EmptyState() {
  return (
    <section className={styles.empty}>
      <Icon name="inbox" size={44} className={styles.emptyIcon} />
      <p className={styles.emptyTitle}>아직 판별 기록이 없어요</p>
      <p className={styles.message}>사진으로 첫 판별을 해 보세요.</p>
      <Link to="/check" className={`${btn.primary} ${btn.large}`}>
        <Icon name="camera" /> 사진으로 확인하기
      </Link>
    </section>
  );
}
