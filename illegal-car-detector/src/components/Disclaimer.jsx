// 결과·가이드 화면 맨 아래에 붙는 안내 문구 (AI 결과는 참고용이라는 내용)
import styles from './Disclaimer.module.css';

export default function Disclaimer() {
  return (
    <p className={styles.disclaimer}>
      <span aria-hidden="true">ℹ️</span> 이 결과는 AI의 참고용 판단이며 법적 효력이 없습니다. 실제
      위반 여부는 담당 기관이 판단합니다.
    </p>
  );
}
