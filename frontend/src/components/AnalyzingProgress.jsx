// 분석 중 화면의 진행 상황: 3단계(사진 준비 → AI 판단 → 결과 정리) + 지난 시간 + 기다리는 동안 팁 + 취소
// phase: 'prepare' | 'analyze' | 'save'  (CheckPage가 analyzePhoto의 onPhase로 받아서 넘겨 줘요)
// startedAt: 분석을 시작한 시각(Date.now())  /  onCancel(): "취소"를 눌렀을 때
import { useEffect, useState } from 'react';
import Icon from './Icon.jsx';
import btn from '../styles/buttons.module.css';
import styles from './AnalyzingProgress.module.css';

const STEPS = [
  { phase: 'prepare', label: '사진 준비하기' },
  { phase: 'analyze', label: 'AI가 판단하는 중' },
  { phase: 'save', label: '결과 정리하기' },
];

// 기다리는 동안 하나를 골라 보여 줘요.
const WAIT_TIPS = [
  '주민신고는 안전신문고 앱 카메라로 같은 위치에서 1분 이상 간격을 두고 2장을 찍어야 해요.',
  '번호판과 위반 장소(횡단보도·인도·소화전)가 한 사진에 함께 보여야 신고가 받아들여져요.',
  '소화전 주변은 사진만으로 5m를 잴 수 없어서, 거리는 직접 확인해 주세요.',
];

// AI는 보통 10~40초 걸려요. 막대는 40초에 거의 다 차게 하고, 끝까지는 채우지 않아요.
const EXPECTED_SECONDS = 40;

function progressPercent(phase, seconds) {
  if (phase === 'prepare') return 8;
  if (phase === 'save') return 96;
  return 10 + Math.min(seconds / EXPECTED_SECONDS, 1) * 80;
}

export default function AnalyzingProgress({ phase, startedAt, onCancel }) {
  const [now, setNow] = useState(() => Date.now());
  const [tip] = useState(() => WAIT_TIPS[Math.floor(Math.random() * WAIT_TIPS.length)]);

  // 1초마다 지난 시간을 다시 계산해요.
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, []);

  const seconds = Math.max(0, Math.floor((now - startedAt) / 1000));
  const currentIndex = STEPS.findIndex((step) => step.phase === phase);

  return (
    <div className={styles.wrap}>
      <section className={styles.card} aria-label="진행 상황">
        {/* 스크린리더에는 단계가 바뀔 때만 읽어 줘요. (초는 매초 읽지 않게 따로 둬요) */}
        <p className="sr-only" role="status">
          {STEPS[currentIndex]?.label ?? ''}
        </p>
        <ol className={styles.steps}>
          {STEPS.map((step, index) => {
            const state = index < currentIndex ? 'done' : index === currentIndex ? 'current' : 'todo';
            return (
              <li key={step.phase} className={`${styles.step} ${styles[state]}`}>
                <span className={styles.marker} aria-hidden="true">
                  {state === 'done' && <Icon name="check" size={16} strokeWidth={3} />}
                  {state === 'todo' && index + 1}
                </span>
                <span className={styles.label}>{step.label}</span>
                <span className={styles.meta} aria-hidden="true">
                  {state === 'done' && '완료'}
                  {state === 'current' && step.phase === 'analyze' && `${seconds}초`}
                </span>
              </li>
            );
          })}
        </ol>
        <div className={styles.track} aria-hidden="true">
          <div className={styles.bar} style={{ width: `${progressPercent(phase, seconds)}%` }} />
        </div>
        <p className={styles.hint}>보통 10~40초 걸려요. 화면을 닫지 말고 기다려 주세요.</p>
      </section>

      <section className={styles.tip} aria-labelledby="wait-tip-title">
        <Icon name="lightbulb" className={styles.tipIcon} />
        <div>
          <h2 id="wait-tip-title" className={styles.tipTitle}>
            기다리는 동안 알아 두세요
          </h2>
          <p className={styles.tipText}>{tip}</p>
        </div>
      </section>

      <button type="button" className={btn.secondary} onClick={onCancel} disabled={phase === 'save'}>
        취소
      </button>
    </div>
  );
}
