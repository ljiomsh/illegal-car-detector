// 공통 레이아웃: 위쪽 헤더 + 가운데 페이지 + 아래쪽 탭(홈/판별/기록/소개)
import { useEffect, useRef } from 'react';
import { Link, Navigate, NavLink, Route, Routes, useLocation } from 'react-router';
import HomePage from './pages/HomePage.jsx';
import CheckPage from './pages/CheckPage.jsx';
import HistoryPage from './pages/HistoryPage.jsx';
import AboutPage from './pages/AboutPage.jsx';
import styles from './App.module.css';

// 아래 탭 메뉴 목록
const TABS = [
  { to: '/', icon: '🏠', label: '홈' },
  { to: '/check', icon: '📷', label: '판별' },
  { to: '/history', icon: '📋', label: '기록' },
  { to: '/about', icon: 'ℹ️', label: '소개' },
];

export default function App() {
  const { pathname } = useLocation();
  const mainRef = useRef(null);
  const prevPathRef = useRef(pathname);

  // 다른 페이지로 이동하면 맨 위부터 보이게 하고, 새 페이지 제목으로 포커스를 옮겨요.
  // (스크린리더가 페이지가 바뀐 걸 알 수 있게. 처음 열 때는 옮기지 않아요.)
  useEffect(() => {
    if (prevPathRef.current === pathname) return;
    prevPathRef.current = pathname;
    window.scrollTo(0, 0);
    mainRef.current?.querySelector('h1')?.focus();
  }, [pathname]);

  return (
    <div className={styles.app}>
      <a className={styles.skipLink} href="#main">
        본문으로 건너뛰기
      </a>

      <header className={styles.header}>
        <Link to="/" className={styles.logo}>
          <span aria-hidden="true">🚗</span> 불법 주정차 판별·신고 도우미
        </Link>
      </header>

      <main id="main" ref={mainRef} className={styles.main} tabIndex={-1}>
        <Routes>
          <Route path="/" element={<HomePage />} />
          <Route path="/check" element={<CheckPage />} />
          <Route path="/history" element={<HistoryPage />} />
          <Route path="/about" element={<AboutPage />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </main>

      <nav className={styles.tabbar} aria-label="주요 메뉴">
        <ul className={styles.tabList}>
          {TABS.map((tab) => (
            <li key={tab.to}>
              <NavLink
                to={tab.to}
                end={tab.to === '/'}
                className={({ isActive }) =>
                  isActive ? `${styles.tab} ${styles.tabActive}` : styles.tab
                }
              >
                <span className={styles.tabIcon} aria-hidden="true">
                  {tab.icon}
                </span>
                <span>{tab.label}</span>
              </NavLink>
            </li>
          ))}
        </ul>
      </nav>
    </div>
  );
}
