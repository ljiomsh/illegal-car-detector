// 시간을 "방금 전", "3분 전", "2시간 전", "5일 전" 같은 말로 바꿔요.
// 30일이 넘으면 "2026. 10. 15." 처럼 날짜로 보여 줘요.

export function timeAgo(isoString, now = Date.now()) {
  const time = new Date(isoString).getTime();
  if (Number.isNaN(time)) return '';

  const seconds = Math.max(0, Math.floor((now - time) / 1000));
  if (seconds < 60) return '방금 전';

  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) return `${minutes}분 전`;

  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours}시간 전`;

  const days = Math.floor(hours / 24);
  if (days < 30) return `${days}일 전`;

  return new Date(time).toLocaleDateString('ko-KR');
}
