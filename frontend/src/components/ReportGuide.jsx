// 안전신문고 신고 순서 목록 + 앱으로만 신고할 수 있다는 안내
// ("안전신문고로 이동" 버튼은 화면 아래 고정 막대에 있어요 → CheckPage의 GuideStep)
import Icon from './Icon.jsx';
import styles from './ReportGuide.module.css';

export default function ReportGuide({ steps }) {
  return (
    <section className={styles.card} aria-labelledby="report-guide-title">
      <h2 id="report-guide-title" className={styles.title}>
        안전신문고 신고 순서
      </h2>

      <ol className={styles.steps}>
        {steps.map((step, index) => (
          <li key={step} className={styles.step}>
            <span className={styles.number} aria-hidden="true">
              {index + 1}
            </span>
            <span>{step}</span>
          </li>
        ))}
      </ol>

      <p className={styles.note}>
        <Icon name="smartphone" size={18} />
        <span>주민신고는 휴대폰의 안전신문고 앱에서, 앱 카메라로 찍은 사진으로만 할 수 있어요.</span>
      </p>
    </section>
  );
}
