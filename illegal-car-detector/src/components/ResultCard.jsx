// 판별 결과 카드: 유형 배지(아이콘+이름) + 확신도 + 위험한 이유
// judgment: 서버(또는 fallback)가 준 JudgmentResponse
// confidence: 모델이 준 최고 확률 (0~1)
import {
  CONFIDENCE_LEVEL_NAMES,
  getViolationType,
  toPercent,
} from '../constants/violationTypes';
import styles from './ResultCard.module.css';

export default function ResultCard({ judgment, confidence }) {
  const type = getViolationType(judgment.type);
  const percent = toPercent(confidence);

  return (
    <section
      className={styles.card}
      style={{ '--type-color': type.color, '--type-text': type.textColor, '--type-soft': type.softColor }}
      aria-labelledby="result-title"
    >
      <div className={styles.head}>
        <span className={styles.icon} aria-hidden="true">
          {type.icon}
        </span>
        <div>
          <p className={styles.verdict}>
            {judgment.violation ? '불법 주정차로 보여요' : '정상 주차로 보여요'}
          </p>
          <h2 id="result-title" className={styles.title}>
            {judgment.title}
          </h2>
        </div>
      </div>

      <p className={styles.confidence}>
        AI 확신도 <strong className={styles.percent}>{percent}%</strong>
        <span className={styles.level}>
          {CONFIDENCE_LEVEL_NAMES[judgment.confidenceLevel] ?? ''}
        </span>
      </p>

      {judgment.dangerReasons?.length > 0 && (
        <div className={styles.reasons}>
          <h3 className={styles.reasonsTitle}>
            <span aria-hidden="true">{judgment.violation ? '⚠️' : '📝'}</span>{' '}
            {judgment.violation ? '왜 위험한가요?' : '참고해 주세요'}
          </h3>
          <ul className={styles.reasonList}>
            {judgment.dangerReasons.map((reason) => (
              <li key={reason}>{reason}</li>
            ))}
          </ul>
        </div>
      )}
    </section>
  );
}
