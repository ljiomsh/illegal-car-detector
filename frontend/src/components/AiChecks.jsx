// "AI가 확인한 것": AI가 사진에서 본 것을 항목별로 보여 줘요. (판별 기준을 알 수 있게)
// 위반의 근거가 된 항목이나 결과를 믿기 어려운 이유는 굵게 표시해요.
import { CHECK_ORDER_TEXT, describeChecks } from '../constants/aiChecks.js';
import Icon from './Icon.jsx';
import styles from './AiChecks.module.css';

export default function AiChecks({ checks }) {
  const rows = describeChecks(checks);
  if (rows.length === 0) return null;

  return (
    <div className={styles.box}>
      <h3 className={styles.title}>
        <Icon name="check" size={16} /> AI가 확인한 것
      </h3>
      <dl className={styles.list}>
        {rows.map((row) => (
          <div key={row.key} className={row.important ? `${styles.row} ${styles.important}` : styles.row}>
            <dt>{row.label}</dt>
            <dd>{row.text}</dd>
          </div>
        ))}
      </dl>
      <p className={styles.note}>{CHECK_ORDER_TEXT}</p>
    </div>
  );
}
