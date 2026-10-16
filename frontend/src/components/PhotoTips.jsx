// 사진 찍는 요령 목록 (사진 고르기 화면, '다시 찍기' 안내에서 같이 써요)
import { useId } from 'react';
import { PHOTO_TIPS } from '../mocks/guides';
import styles from './PhotoTips.module.css';

export default function PhotoTips({ title = '이렇게 찍으면 정확해요' }) {
  const titleId = useId(); // 제목과 영역을 연결하는 고유 id

  return (
    <section className={styles.tips} aria-labelledby={titleId}>
      <h2 id={titleId} className={styles.title}>
        <span aria-hidden="true">💡</span> {title}
      </h2>
      <ul className={styles.list}>
        {PHOTO_TIPS.map((tip) => (
          <li key={tip}>{tip}</li>
        ))}
      </ul>
    </section>
  );
}
