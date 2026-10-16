// 결과·가이드 화면 맨 아래에 붙는 안내 문구 (AI 결과는 참고용이라는 내용)
// 외부 AI 모드(VITE_MODEL_MODE=api)에서는 외부 AI를 쓴다는 안내도 한 줄 더 보여 줘요.
import { EXTERNAL_AI_NOTICE, IS_API_MODE } from '../constants/aiMode.js';
import styles from './Disclaimer.module.css';

export default function Disclaimer() {
  return (
    <div className={styles.disclaimer}>
      <p>
        <span aria-hidden="true">ℹ️</span> 이 결과는 AI의 참고용 판단이며 법적 효력이 없습니다. 실제
        위반 여부는 담당 기관이 판단합니다.
      </p>
      {IS_API_MODE && (
        <p className={styles.external}>
          <span aria-hidden="true">🌐</span> {EXTERNAL_AI_NOTICE}
        </p>
      )}
    </div>
  );
}
