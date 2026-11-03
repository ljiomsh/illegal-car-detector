// 기록을 날짜(내 기기 시간 기준)별로 묶고, "오늘", "어제", "11월 2일 (일)" 같은 제목을 만들어요.

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'];

// 날짜를 "2026-11-04" 같은 글자로 바꿔요. (같은 날이면 같은 글자)
function toDayKey(date) {
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}

// 날짜 묶음의 제목: 오늘 / 어제 / 11월 2일 (일) / 2025년 12월 30일 (화)
export function dayLabel(date, now = new Date()) {
  const yesterday = new Date(now);
  yesterday.setDate(now.getDate() - 1);

  if (toDayKey(date) === toDayKey(now)) return '오늘';
  if (toDayKey(date) === toDayKey(yesterday)) return '어제';

  const year = date.getFullYear() === now.getFullYear() ? '' : `${date.getFullYear()}년 `;
  return `${year}${date.getMonth() + 1}월 ${date.getDate()}일 (${WEEKDAYS[date.getDay()]})`;
}

// "오후 3:24" 같은 시각
export function timeOfDay(isoString) {
  const date = new Date(isoString);
  if (Number.isNaN(date.getTime())) return '';
  return date.toLocaleTimeString('ko-KR', { hour: 'numeric', minute: '2-digit' });
}

// 기록 목록(최신순)을 날짜별로 묶어요. 순서는 그대로 지켜요.
// → [{ key: '2026-11-04', label: '오늘', items: [...] }, ...]
export function groupByDay(items, getIsoString, now = new Date()) {
  const groups = [];
  for (const item of items) {
    const date = new Date(getIsoString(item));
    const key = Number.isNaN(date.getTime()) ? 'unknown' : toDayKey(date);
    let group = groups.find((g) => g.key === key);
    if (!group) {
      group = { key, label: key === 'unknown' ? '날짜 모름' : dayLabel(date, now), items: [] };
      groups.push(group);
    }
    group.items.push(item);
  }
  return groups;
}
