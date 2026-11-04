// 판별 결과 카드: 결론(유형·확신도)을 맨 위에, 사진은 작은 썸네일로 옆에 둬요.
// 확신도가 '보통'이면 경고를, 2위 유형 확률이 높으면 그 안내를, AI 판단 근거를 카드 안에 함께 보여 줘요.
// judgment: 서버(또는 fallback)가 준 JudgmentResponse
// prediction: AI 결과 { label, confidence, probabilities, reason }
// photoUrl: 판별한 사진 주소
import {
  CONFIDENCE_LEVEL_NAMES,
  getViolationType,
  LABELS,
  toPercent,
} from '../constants/violationTypes';
import AiChecks from './AiChecks.jsx';
import Icon from './Icon.jsx';
import styles from './ResultCard.module.css';

// 2위 확률이 이 값 이상이면 "○○일 수도 있어요"를 보여 줘요.
const SECOND_CANDIDATE_MIN = 0.3;

// 확률이 두 번째로 높은 라벨을 찾아요.
function findSecondLabel(probabilities) {
  const sorted = [...LABELS].sort((a, b) => (probabilities[b] ?? 0) - (probabilities[a] ?? 0));
  return sorted[1];
}

export default function ResultCard({ judgment, prediction, photoUrl }) {
  const type = getViolationType(judgment.type);
  const level = judgment.confidenceLevel;

  const secondLabel = findSecondLabel(prediction.probabilities);
  const secondProbability = prediction.probabilities[secondLabel] ?? 0;
  const secondText =
    secondProbability >= SECOND_CANDIDATE_MIN
      ? `${secondLabel === 'NORMAL' ? '정상 주차' : getViolationType(secondLabel).title}일 수도 있어요 (${toPercent(secondProbability)}%).`
      : '';

  return (
    <section
      className={styles.card}
      style={{ '--type-color': type.color, '--type-text': type.textColor }}
      aria-labelledby="result-title"
    >
      <div className={styles.head}>
        <img src={photoUrl} alt="판별한 사진" className={styles.thumb} />
        <div className={styles.summary}>
          <p className={styles.verdict}>
            <Icon name={judgment.violation ? 'alert' : 'checkCircle'} size={16} />
            {judgment.violation ? '불법 주정차로 보여요' : '정상 주차로 보여요'}
          </p>
          <h2 id="result-title" className={styles.title}>
            {judgment.title}
          </h2>
          <p className={styles.confidence}>
            확신도 <strong className={styles.percent}>{toPercent(prediction.confidence)}%</strong>
            {CONFIDENCE_LEVEL_NAMES[level] && (
              <span className={`${styles.level} ${level === 'MEDIUM' ? styles.levelMedium : styles.levelHigh}`}>
                {CONFIDENCE_LEVEL_NAMES[level]}
              </span>
            )}
          </p>
        </div>
      </div>

      {level === 'MEDIUM' ? (
        <p className={styles.warning} role="note">
          <Icon name="alert" size={18} />
          <span>
            AI가 확실하지 않아요. {secondText} 다른 각도에서 한 장 더 찍어 보세요.
          </span>
        </p>
      ) : (
        secondText && <p className={styles.notice}>{secondText}</p>
      )}

      {prediction.reason && (
        <div className={styles.reason}>
          <h3 className={styles.reasonTitle}>
            <Icon name="sparkles" size={16} /> AI 판단 근거
          </h3>
          <p>{prediction.reason}</p>
        </div>
      )}

      <AiChecks checks={prediction.checks} />
    </section>
  );
}

// "왜 위험한가요?" (정상 주차는 "참고해 주세요") 카드
export function DangerReasons({ judgment }) {
  if (!judgment.dangerReasons?.length) return null;
  return (
    <section className={styles.reasonsCard} aria-labelledby="danger-title">
      <h2 id="danger-title" className={styles.reasonsTitle}>
        {judgment.violation ? '왜 위험한가요?' : '참고해 주세요'}
      </h2>
      <ul className={styles.reasonList}>
        {judgment.dangerReasons.map((reason) => (
          <li key={reason}>{reason}</li>
        ))}
      </ul>
    </section>
  );
}
