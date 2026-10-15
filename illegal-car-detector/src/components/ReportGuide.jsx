// 신고 단계 목록 + "안전신문고로 이동" 버튼 (새 탭에서 열려요)
import btn from '../styles/buttons.module.css';
import styles from './ReportGuide.module.css';

export default function ReportGuide({ steps, reportUrl }) {
  return (
    <section className={styles.card} aria-labelledby="report-guide-title">
      <h2 id="report-guide-title" className={styles.title}>
        안전신문고 신고 방법
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
        <span aria-hidden="true">📱</span> 주민신고는 휴대폰의 안전신문고 앱에서, 앱 카메라로 찍은
        사진으로만 할 수 있어요.
      </p>

      <a
        className={`${btn.primary} ${btn.large}`}
        href={reportUrl}
        target="_blank"
        rel="noopener noreferrer"
      >
        안전신문고로 이동 <span aria-hidden="true">↗</span>
        <span className="sr-only">(새 탭에서 열려요)</span>
      </a>
    </section>
  );
}
