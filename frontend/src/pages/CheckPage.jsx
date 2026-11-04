// 판별 화면 (/check)
// step 상태에 따라 화면이 바뀌어요: select → preview → analyzing → result → guide
import { useEffect, useRef, useState } from 'react';
import { analyzePhoto, isDemoMode } from '../api/analyzePhoto.js';
import { postJudgment } from '../api/client.js';
import { PRIVACY_NOTICE } from '../constants/aiInfo.js';
import { buildGuideResponse } from '../mocks/guides.js';
import { getConfidenceLevel, getViolationType } from '../constants/violationTypes.js';
import { resizeImage } from '../utils/resizeImage.js';
import Icon from '../components/Icon.jsx';
import PhotoPicker from '../components/PhotoPicker.jsx';
import PhotoPreview from '../components/PhotoPreview.jsx';
import PhotoTips from '../components/PhotoTips.jsx';
import AnalyzingProgress from '../components/AnalyzingProgress.jsx';
import ResultCard, { DangerReasons } from '../components/ResultCard.jsx';
import ProbabilityBars from '../components/ProbabilityBars.jsx';
import Checklist from '../components/Checklist.jsx';
import ReportGuide from '../components/ReportGuide.jsx';
import FeedbackButton from '../components/FeedbackButton.jsx';
import Disclaimer from '../components/Disclaimer.jsx';
import btn from '../styles/buttons.module.css';
import styles from './CheckPage.module.css';

// 단계별 제목
const STEP_TITLES = {
  select: '사진을 골라 주세요',
  preview: '이 사진으로 판별할까요?',
  analyzing: 'AI가 분석 중이에요',
  result: '판별 결과',
  guide: '신고 방법',
};

export default function CheckPage() {
  const [isDemo] = useState(isDemoMode); // 주소에 ?mock=... 이 있으면 비상 시연 모드

  const [step, setStep] = useState('select');
  const [photoUrl, setPhotoUrl] = useState(null); // 줄인 사진의 임시 주소(blob:)
  const [isPreparing, setIsPreparing] = useState(false); // 사진 줄이는 중
  const [phase, setPhase] = useState('prepare'); // 분석 진행 단계: prepare → analyze → save
  const [analyzeStartedAt, setAnalyzeStartedAt] = useState(0); // 분석 시작 시각 (지난 시간 표시용)
  const [prediction, setPrediction] = useState(null); // AI 결과 { label, confidence, probabilities, reason, modelVersion }
  const [judgment, setJudgment] = useState(null); // 서버 응답(또는 fallback 안내)
  const [saveFailed, setSaveFailed] = useState(false); // 서버 저장 실패 여부
  const [feedbackSent, setFeedbackSent] = useState(false); // "판별이 틀렸어요"를 보냈는지
  const [checkedIds, setCheckedIds] = useState([]); // 체크리스트에서 체크한 항목
  const [errorMessage, setErrorMessage] = useState('');
  const [analyzeFailed, setAnalyzeFailed] = useState(false); // 분석 실패 → 버튼을 "다시 시도"로

  const headingRef = useRef(null); // 단계가 바뀌면 제목으로 포커스를 옮겨요
  const prevStepRef = useRef(step);
  const isMountedRef = useRef(false); // 화면을 벗어난 뒤에는 상태를 바꾸지 않으려고
  const abortRef = useRef(null); // 분석 "취소"용 AbortController

  useEffect(() => {
    isMountedRef.current = true;
    return () => {
      isMountedRef.current = false;
      abortRef.current?.abort(); // 화면을 벗어나면 진행 중인 분석도 멈춰요.
    };
  }, []);

  // 사진 주소가 바뀌거나 화면을 벗어나면 이전 임시 주소를 지워서 메모리를 아껴요.
  useEffect(() => {
    if (!photoUrl) return undefined;
    return () => URL.revokeObjectURL(photoUrl);
  }, [photoUrl]);

  // 단계가 바뀌면 맨 위로 올리고, 제목에 포커스를 줘서 스크린리더가 읽게 해요.
  useEffect(() => {
    if (prevStepRef.current === step) return;
    prevStepRef.current = step;
    window.scrollTo(0, 0);
    headingRef.current?.focus();
  }, [step]);

  // 처음 단계로 돌아가요. (다시 찍기)
  function resetToSelect() {
    setPhotoUrl(null);
    setPrediction(null);
    setJudgment(null);
    setSaveFailed(false);
    setFeedbackSent(false);
    setCheckedIds([]);
    setErrorMessage('');
    setAnalyzeFailed(false);
    setStep('select');
  }

  // 사진을 고르면: 1024px로 줄이고 → 미리보기로
  async function handleSelectFile(file) {
    setErrorMessage('');
    setIsPreparing(true);
    try {
      const blob = await resizeImage(file);
      if (!isMountedRef.current) return;
      setPhotoUrl(URL.createObjectURL(blob));
      setStep('preview');
    } catch (error) {
      if (isMountedRef.current) setErrorMessage(error.message);
    } finally {
      if (isMountedRef.current) setIsPreparing(false);
    }
  }

  // "판별하기": AI로 분석 → 서버에 저장 → 결과 화면
  async function handleAnalyze() {
    if (!photoUrl) return;

    const controller = new AbortController();
    abortRef.current = controller;
    setErrorMessage('');
    setPhase('prepare');
    setAnalyzeStartedAt(Date.now());
    setStep('analyzing');

    try {
      // 화면에 보이는 <img>는 CSS 때문에 크기가 줄거나 늘어날 수 있어서,
      // 원래 비율 그대로인 새 <img>를 만들어 AI에 보낼 사진을 만들어요.
      const img = new Image();
      img.src = photoUrl;
      await img.decode(); // 사진을 다 읽을 때까지 기다려요.
      const result = await analyzePhoto(img, {
        signal: controller.signal,
        onPhase: (next) => isMountedRef.current && setPhase(next),
      });
      if (!isMountedRef.current) return;

      // 서버가 안 될 때를 대비해 기기 안의 안내 문구(fallback)를 먼저 만들어 둬요.
      let response = buildGuideResponse(result);
      let failed = false;

      // 확신이 낮으면 결과를 보여 주지 않으니 기록에도 남기지 않아요.
      if (getConfidenceLevel(result.confidence) !== 'LOW') {
        setPhase('save');
        try {
          // 서버가 알려 준 모델 이름(비상 시연 모드는 'demo')을 함께 저장해요.
          response = await postJudgment(result);
        } catch (error) {
          console.warn('판별 기록을 저장하지 못했어요. 기기 안의 안내를 보여 줄게요.', error);
          failed = true;
        }
      }
      if (!isMountedRef.current) return;

      setPrediction(result);
      setJudgment(response);
      setSaveFailed(failed);
      setAnalyzeFailed(false);
      setStep('result');
    } catch (error) {
      if (!isMountedRef.current) return;
      // "취소"를 눌렀으면 조용히 미리보기로 돌아가요.
      if (error.code === 'CANCELLED') {
        setStep('preview');
        return;
      }
      console.error('분석 중 오류:', error);
      // 서버가 { code, message }로 이유를 알려 줬으면(400·502·503 등) 그 안내를 그대로 보여 줘요.
      // 서버에 닿지 못했거나 시간이 초과되면 기본 안내를 보여 줘요.
      setErrorMessage(
        error.code && error.code !== 'TIMEOUT'
          ? error.message
          : 'AI 분석에 실패했어요. 다시 시도해 주세요.',
      );
      setAnalyzeFailed(true);
      setStep('preview');
    } finally {
      if (abortRef.current === controller) abortRef.current = null;
    }
  }

  return (
    <div className={styles.page}>
      <title>판별하기 | 길터</title>

      {step === 'guide' && (
        <button type="button" className={styles.backLink} onClick={() => setStep('result')}>
          <Icon name="chevronLeft" size={18} /> 결과로
        </button>
      )}

      {/* key={step}: 단계마다 새 제목을 만들어서 스크린리더가 확실히 읽게 해요. */}
      <h1 key={step} ref={headingRef} tabIndex={-1} className={styles.heading}>
        {STEP_TITLES[step]}
      </h1>

      {errorMessage && (
        <p className={styles.error} role="alert">
          <Icon name="alert" size={18} />
          <span>{errorMessage}</span>
        </p>
      )}

      {step === 'select' && (
        <SelectStep isPreparing={isPreparing} onSelectFile={handleSelectFile} />
      )}

      {step === 'preview' && (
        <>
          <PhotoPreview src={photoUrl} />
          <div className={styles.actions}>
            <button
              type="button"
              className={`${btn.primary} ${btn.large}`}
              onClick={handleAnalyze}
            >
              {analyzeFailed ? '다시 시도' : '판별하기'}
            </button>
            <button type="button" className={btn.secondary} onClick={resetToSelect}>
              다시 찍기
            </button>
            {isDemo ? (
              <p className={styles.status} role="status">
                <Icon name="flask" size={16} /> 비상 시연 모드예요. AI를 부르지 않고 정해진 결과를 보여 줘요.
              </p>
            ) : (
              <p className={styles.privacy}>
                <Icon name="lock" size={16} /> {PRIVACY_NOTICE}
              </p>
            )}
          </div>
        </>
      )}

      {step === 'analyzing' && (
        <>
          <PhotoPreview src={photoUrl} analyzing />
          <AnalyzingProgress
            phase={phase}
            startedAt={analyzeStartedAt}
            onCancel={() => abortRef.current?.abort()}
          />
        </>
      )}

      {step === 'result' && prediction && judgment && (
        <ResultStep
          photoUrl={photoUrl}
          prediction={prediction}
          judgment={judgment}
          saveFailed={saveFailed}
          feedbackSent={feedbackSent}
          onFeedbackSent={() => setFeedbackSent(true)}
          onShowGuide={() => setStep('guide')}
          onRetake={resetToSelect}
        />
      )}

      {step === 'guide' && judgment && (
        <GuideStep judgment={judgment} checkedIds={checkedIds} onChangeChecked={setCheckedIds} />
      )}
    </div>
  );
}

// 1단계: 사진 고르기
function SelectStep({ isPreparing, onSelectFile }) {
  return (
    <>
      <p className={styles.lead}>
        주차된 차와 주변(횡단보도·인도·소화전)이 함께 나온 사진을 올려 주세요.
      </p>
      <PhotoPicker onSelect={onSelectFile} disabled={isPreparing} />
      {isPreparing && (
        <p className={styles.status} role="status">
          사진을 준비하고 있어요…
        </p>
      )}
    </>
  );
}

// 4단계: 판별 결과 (확신도 HIGH / MEDIUM / LOW에 따라 다르게 보여요)
function ResultStep({
  photoUrl,
  prediction,
  judgment,
  saveFailed,
  feedbackSent,
  onFeedbackSent,
  onShowGuide,
  onRetake,
}) {
  // LOW: 결과 대신 다시 찍기 안내
  if (judgment.confidenceLevel === 'LOW') {
    return (
      <>
        <PhotoPreview src={photoUrl} />
        <section className={styles.lowCard} aria-labelledby="low-title">
          <h2 id="low-title" className={styles.lowTitle}>
            <Icon name="help" size={22} /> AI가 사진을 잘 알아보지 못했어요
          </h2>
          <p>판별 결과를 믿기 어려워서 보여 드리지 않을게요. 아래 요령대로 다시 찍어 주세요.</p>
        </section>
        <PhotoTips title="다시 찍을 때는 이렇게 해 보세요" />
        <button type="button" className={`${btn.primary} ${btn.large}`} onClick={onRetake}>
          다시 찍기
        </button>
        <Disclaimer />
      </>
    );
  }

  // HIGH / MEDIUM
  return (
    <>
      <ResultCard judgment={judgment} prediction={prediction} photoUrl={photoUrl} />
      <DangerReasons judgment={judgment} />
      <ProbabilityBars probabilities={prediction.probabilities} topLabel={prediction.label} />

      {saveFailed && (
        <p className={styles.status}>
          서버에 연결하지 못해서 이번 결과는 기록에 남지 않았어요. 안내는 기기에 저장된 내용으로
          보여 드려요.
        </p>
      )}

      {judgment.judgmentId != null && (
        <div className={styles.feedback}>
          <FeedbackButton
            judgmentId={judgment.judgmentId}
            predictedLabel={prediction.label}
            sent={feedbackSent}
            onSent={onFeedbackSent}
          />
        </div>
      )}

      <Disclaimer />

      {/* 스크롤해도 화면 아래에 붙어 있는 버튼 막대 */}
      <div className={styles.stickyBar}>
        {judgment.violation ? (
          <>
            <button
              type="button"
              className={styles.iconButton}
              onClick={onRetake}
              aria-label="다른 사진으로 다시 하기"
            >
              <Icon name="rotate" size={22} />
            </button>
            <button type="button" className={`${btn.primary} ${btn.large}`} onClick={onShowGuide}>
              신고 방법 보기 <Icon name="chevronRight" size={18} />
            </button>
          </>
        ) : (
          <button type="button" className={`${btn.primary} ${btn.large}`} onClick={onRetake}>
            다른 사진 확인하기
          </button>
        )}
      </div>
    </>
  );
}

// 5단계: 신고 가이드 (① 요건 확인하기 → ② 안전신문고에 신고)
function GuideStep({ judgment, checkedIds, onChangeChecked }) {
  const type = getViolationType(judgment.type);
  const items = judgment.checklist ?? [];
  const remaining = items.filter((item) => !checkedIds.includes(item.id)).length;
  const ready = remaining === 0;

  return (
    <>
      <p className={styles.typeChip} style={{ '--type-soft': type.softColor, '--type-text': type.textColor }}>
        <Icon name={type.icon} size={16} /> {judgment.title}
      </p>

      <ol className={styles.stepper} aria-label="진행 단계">
        <li className={ready ? styles.stepDone : styles.stepCurrent} aria-current={ready ? undefined : 'step'}>
          <span className={styles.stepNumber} aria-hidden="true">
            {ready ? <Icon name="check" size={14} strokeWidth={3} /> : 1}
          </span>
          요건 확인하기
        </li>
        <li className={ready ? styles.stepCurrent : styles.stepTodo} aria-current={ready ? 'step' : undefined}>
          <span className={styles.stepNumber} aria-hidden="true">
            2
          </span>
          안전신문고에 신고
        </li>
      </ol>

      <Checklist items={items} checkedIds={checkedIds} onChange={onChangeChecked} />
      <ReportGuide steps={judgment.reportSteps} />
      <Disclaimer />

      <div className={`${styles.stickyBar} ${styles.stickyColumn}`}>
        <a
          className={`${btn.primary} ${btn.large}`}
          href={judgment.reportUrl}
          target="_blank"
          rel="noopener noreferrer"
        >
          안전신문고로 이동 <Icon name="externalLink" size={18} />
          <span className="sr-only">(새 탭에서 열려요)</span>
        </a>
        <p className={styles.stickyNote}>
          {ready ? '신고 준비가 끝났어요.' : `확인하지 않은 항목이 ${remaining}개 있어요`}
        </p>
      </div>
    </>
  );
}
