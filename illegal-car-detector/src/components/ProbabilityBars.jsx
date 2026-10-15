// 4가지 유형의 확률을 막대그래프로 보여 줘요. (높은 순서대로)
// 1위 막대만 유형 색으로 칠하고, 나머지는 회색이에요. 숫자(%)도 항상 같이 보여 줘요.
import { getViolationType, LABELS, toPercent } from '../constants/violationTypes';
import styles from './ProbabilityBars.module.css';

export default function ProbabilityBars({ probabilities, topLabel }) {
  // 확률이 높은 순서로 정렬
  const sorted = [...LABELS].sort(
    (a, b) => (probabilities[b] ?? 0) - (probabilities[a] ?? 0),
  );

  return (
    <section className={styles.card} aria-labelledby="probability-title">
      <h2 id="probability-title" className={styles.title}>
        유형별 가능성
      </h2>
      <ul className={styles.list}>
        {sorted.map((label) => {
          const type = getViolationType(label);
          const percent = toPercent(probabilities[label]);
          const isTop = label === topLabel;

          return (
            <li key={label} className={styles.row}>
              <div className={styles.rowHead}>
                <span className={isTop ? styles.nameTop : styles.name}>
                  <span aria-hidden="true">{type.icon}</span> {type.name}
                </span>
                <span className={styles.percent}>{percent}%</span>
              </div>
              <div className={styles.track} aria-hidden="true">
                <div
                  className={styles.bar}
                  style={{
                    width: `${percent}%`,
                    background: isTop ? type.color : 'var(--color-border-strong)',
                  }}
                />
              </div>
            </li>
          );
        })}
      </ul>
    </section>
  );
}
