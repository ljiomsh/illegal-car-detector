// "판별이 틀렸어요" 버튼: 누르면 실제 유형을 고르고, 서버에 피드백을 보내요.
// 단계: idle(버튼만) → choosing(유형 고르기) → sending(보내는 중) → done(고마워요) / error
// sent / onSent: 피드백을 보냈는지는 부모(CheckPage)가 기억해요. (화면을 오가도 두 번 보내지 않게)
import { useEffect, useRef, useState } from 'react';
import { postFeedback } from '../api/client';
import { getViolationType, LABELS } from '../constants/violationTypes';
import btn from '../styles/buttons.module.css';
import styles from './FeedbackButton.module.css';

export default function FeedbackButton({ judgmentId, predictedLabel, sent = false, onSent }) {
  const [state, setState] = useState('idle');
  const openButtonRef = useRef(null);
  const firstOptionRef = useRef(null);
  const thanksRef = useRef(null);
  const prevStateRef = useRef(state);

  // 화면이 바뀔 때 키보드 포커스를 알맞은 곳으로 옮겨요.
  useEffect(() => {
    const prev = prevStateRef.current;
    prevStateRef.current = state;
    if (prev === 'idle' && state === 'choosing') firstOptionRef.current?.focus();
    if (prev !== 'idle' && state === 'idle') openButtonRef.current?.focus();
    if (state === 'done') thanksRef.current?.focus();
    if (state === 'error') firstOptionRef.current?.focus();
  }, [state]);

  // AI가 고른 라벨을 뺀 나머지가 선택지예요.
  const options = LABELS.filter((label) => label !== predictedLabel);

  async function sendFeedback(actualLabel) {
    setState('sending');
    try {
      await postFeedback(judgmentId, { correct: false, actualLabel });
      setState('done');
      onSent?.();
    } catch (error) {
      console.error('피드백을 보내지 못했어요:', error);
      setState('error');
    }
  }

  if (sent || state === 'done') {
    return (
      <p ref={thanksRef} tabIndex={-1} className={styles.thanks} role="status">
        <span aria-hidden="true">🙏</span> 알려줘서 고마워요! AI를 더 똑똑하게 만드는 데 쓸게요.
      </p>
    );
  }

  if (state === 'idle') {
    return (
      <button
        ref={openButtonRef}
        type="button"
        className={btn.text}
        onClick={() => setState('choosing')}
      >
        판별이 틀렸어요
      </button>
    );
  }

  // choosing / sending / error
  return (
    <fieldset className={styles.box} disabled={state === 'sending'}>
      <legend className={styles.legend}>실제로는 어떤 경우였나요?</legend>

      <div className={styles.options}>
        {options.map((label, index) => {
          const type = getViolationType(label);
          return (
            <button
              key={label}
              ref={index === 0 ? firstOptionRef : undefined}
              type="button"
              className={btn.secondary}
              onClick={() => sendFeedback(label)}
            >
              <span aria-hidden="true">{type.icon}</span> {type.title}
            </button>
          );
        })}
        <button type="button" className={btn.secondary} onClick={() => sendFeedback(null)}>
          잘 모르겠어요
        </button>
      </div>

      {state === 'sending' && (
        <p className={styles.message} role="status">
          보내는 중이에요…
        </p>
      )}
      {state === 'error' && (
        <p className={styles.error} role="alert">
          피드백을 보내지 못했어요. 잠시 후 다시 눌러 주세요.
        </p>
      )}

      <button type="button" className={btn.text} onClick={() => setState('idle')}>
        취소
      </button>
    </fieldset>
  );
}
