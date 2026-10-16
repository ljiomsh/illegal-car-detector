// 고른 사진을 보여 줘요. analyzing이 true면 사진 위에 "분석 중" 표시를 덮어요.
// compact가 true면 결과 화면용으로 작게 보여 줘요.
import styles from './PhotoPreview.module.css';

export default function PhotoPreview({ src, analyzing = false, compact = false }) {
  return (
    <figure className={`${styles.preview} ${compact ? styles.compact : ''}`}>
      <img src={src} alt="판별할 주차 사진" className={styles.image} />

      {analyzing && (
        <div className={styles.overlay} role="status">
          <span className={styles.spinner} aria-hidden="true" />
          <span className={styles.overlayText}>AI가 사진을 살펴보고 있어요… (최대 1분)</span>
        </div>
      )}
    </figure>
  );
}
