// 신고 요건 체크리스트: 진행 막대로 몇 개 확인했는지 보여 주고, 모두 체크하면 "신고 준비 완료!"가 나타나요.
// checkedIds(체크한 항목 id 목록)는 부모(CheckPage)가 가지고 있어요.
// 그래야 "결과로"를 눌렀다 돌아와도 체크한 내용이 남아 있어요.
import Icon from './Icon.jsx';
import styles from './Checklist.module.css';

export default function Checklist({ items, checkedIds, onChange }) {
  function toggle(id) {
    onChange(
      checkedIds.includes(id)
        ? checkedIds.filter((checkedId) => checkedId !== id)
        : [...checkedIds, id],
    );
  }

  const checkedCount = items.filter((item) => checkedIds.includes(item.id)).length;
  const allChecked = items.length > 0 && checkedCount === items.length;
  const percent = items.length > 0 ? (checkedCount / items.length) * 100 : 0;

  return (
    <section className={styles.card} aria-labelledby="checklist-title">
      <div className={styles.head}>
        <h2 id="checklist-title" className={styles.title}>
          신고 전에 확인해요
        </h2>
        <span className={styles.count}>
          {checkedCount} / {items.length}
        </span>
      </div>
      <div className={styles.track} aria-hidden="true">
        <div className={styles.bar} style={{ width: `${percent}%` }} />
      </div>

      <ul className={styles.list}>
        {items.map((item) => (
          <li key={item.id}>
            <label className={styles.item}>
              <input
                type="checkbox"
                className={styles.checkbox}
                checked={checkedIds.includes(item.id)}
                onChange={() => toggle(item.id)}
              />
              <span>{item.text}</span>
            </label>
          </li>
        ))}
      </ul>

      {/* 스크린리더도 완료 소식을 들을 수 있게 role="status" 영역을 항상 두어요. */}
      <div role="status" className={styles.statusArea}>
        {allChecked && (
          <p className={styles.done}>
            <Icon name="checkCircle" size={22} /> 신고 준비 완료!
          </p>
        )}
      </div>
    </section>
  );
}
