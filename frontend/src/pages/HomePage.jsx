// 홈 화면 (/): 서비스 소개 + 시작 버튼 + 4가지 유형 + 개인정보 안내
import { Link } from 'react-router';
import { getViolationType, LABELS } from '../constants/violationTypes';
import { PRIVACY_NOTICE } from '../constants/aiMode.js';
import btn from '../styles/buttons.module.css';
import styles from './HomePage.module.css';

export default function HomePage() {
  return (
    <div className={styles.page}>
      <title>불법 주정차 판별·신고 도우미</title>

      <section className={styles.hero}>
        <h1 tabIndex={-1} className={styles.heading}>
          사진 한 장으로 불법 주정차인지 확인하고, 올바르게 신고하세요
        </h1>
        <Link to="/check" className={`${btn.primary} ${btn.large}`}>
          <span aria-hidden="true">📷</span> 사진으로 확인하기
        </Link>
      </section>

      <section aria-labelledby="types-title">
        <h2 id="types-title" className={styles.sectionTitle}>
          이런 경우를 알려 드려요
        </h2>
        <ul className={styles.typeGrid}>
          {LABELS.map((label) => {
            const type = getViolationType(label);
            return (
              <li
                key={label}
                className={styles.typeCard}
                style={{ '--type-soft': type.softColor }}
              >
                <span className={styles.typeIcon} aria-hidden="true">
                  {type.icon}
                </span>
                <div>
                  <h3 className={styles.typeName}>{type.title}</h3>
                  <p className={styles.typeDesc}>{type.description}</p>
                </div>
              </li>
            );
          })}
        </ul>
      </section>

      <p className={styles.privacy}>
        <span aria-hidden="true">🔒</span> {PRIVACY_NOTICE}
      </p>
    </div>
  );
}
