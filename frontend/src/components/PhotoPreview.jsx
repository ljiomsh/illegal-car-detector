// 고른 사진을 보여 줘요. analyzing이 true면 사진을 어둡게 하고 훑어보는 선을 움직여요.
// (분석 진행 상황 글은 AnalyzingProgress가 보여 줘요)
import styles from './PhotoPreview.module.css';

export default function PhotoPreview({ src, analyzing = false }) {
  return (
    <figure className={`${styles.preview} ${analyzing ? styles.analyzing : ''}`}>
      <img src={src} alt="판별할 주차 사진" className={styles.image} />
      {analyzing && <span className={styles.scan} aria-hidden="true" />}
    </figure>
  );
}
