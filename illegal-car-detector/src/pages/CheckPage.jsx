// 판별 화면 (/check)
// step 상태에 따라 화면이 바뀌어요: select → preview → analyzing → result → guide
import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router';
import useModel, { MODEL_VERSION } from '../hooks/useModel';
import { postJudgment } from '../api/client';
import { buildGuideResponse } from '../mocks/guides';
import { getConfidenceLevel, getViolationType, LABELS, toPercent } from '../constants/violationTypes';
import { resizeImage } from '../utils/resizeImage';
import PhotoPicker from '../components/PhotoPicker.jsx';
import PhotoPreview from '../components/PhotoPreview.jsx';
import PhotoTips from '../components/PhotoTips.jsx';
import ResultCard from '../components/ResultCard.jsx';
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

// 2위 확률이 이 값 이상이면 "○○에도 해당될 수 있어요"를 보여 줘요.
const SECOND_CANDIDATE_MIN = 0.3;

// 확률이 두 번째로 높은 라벨을 찾아요.
function findSecondLabel(probabilities) {
  const sorted = [...LABELS].sort((a, b) => (probabilities[b] ?? 0) - (probabilities[a] ?? 0));
  return sorted[1];
}

export default function CheckPage() {
  const navigate = useNavigate();
  const { status: modelStatus, predict } = useModel();

  const [step, setStep] = useState('select');
  const [photoUrl, setPhotoUrl] = useState(null); // 줄인 사진의 임시 주소(blob:)
  const [isPreparing, setIsPreparing] = useState(false); // 사진 줄이는 중
  const [prediction, setPrediction] = useState(null); // 모델 결과 { label, confidence, probabilities }
  const [judgment, setJudgment] = useState(null); // 서버 응답(또는 fallback 안내)
  const [saveFailed, setSaveFailed] = useState(false); // 서버 저장 실패 여부
  const [feedbackSent, setFeedbackSent] = useState(false); // "판별이 틀렸어요"를 보냈는지
  const [checkedIds, setCheckedIds] = useState([]); // 체크리스트에서 체크한 항목
  const [errorMessage, setErrorMessage] = useState('');

  const headingRef = useRef(null); // 단계가 바뀌면 제목으로 포커스를 옮겨요
  const prevStepRef = useRef(step);
  const isMountedRef = useRef(false); // 화면을 벗어난 뒤에는 상태를 바꾸지 않으려고

  useEffect(() => {
    isMountedRef.current = true;
    return () => {
      isMountedRef.current = false;
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

  // "판별하기": 모델로 분석 → 서버에 저장 → 결과 화면
  async function handleAnalyze() {
    if (!photoUrl || modelStatus !== 'ready') return;

    setErrorMessage('');
    setStep('analyzing');

    try {
      // 화면에 보이는 <img>는 CSS 때문에 크기가 줄거나 늘어날 수 있어서,
      // 원래 비율 그대로인 새 <img>를 만들어 모델에 넘겨요.
      const img = new Image();
      img.src = photoUrl;
      await img.decode(); // 사진을 다 읽을 때까지 기다려요.
      const result = await predict(img);
      if (!isMountedRef.current) return;

      // 서버가 안 될 때를 대비해 기기 안의 안내 문구(fallback)를 먼저 만들어 둬요.
      let response = buildGuideResponse(result);
      let failed = false;

      // 확신이 낮으면 결과를 보여 주지 않으니 기록에도 남기지 않아요.
      if (getConfidenceLevel(result.confidence) !== 'LOW') {
        try {
          response = await postJudgment({ ...result, modelVersion: MODEL_VERSION });
        } catch (error) {
          console.warn('판별 기록을 저장하지 못했어요. 기기 안의 안내를 보여 줄게요.', error);
          failed = true;
        }
      }
      if (!isMountedRef.current) return;

      setPrediction(result);
      setJudgment(response);
      setSaveFailed(failed);
      setStep('result');
    } catch (error) {
      console.error('분석 중 오류:', error);
      if (!isMountedRef.current) return;
      setErrorMessage('사진을 분석하지 못했어요. 잠시 후 다시 시도해 주세요.');
      setStep('preview');
    }
  }

  return (
    <div className={styles.page}>
      <title>판별하기 | 불법 주정차 판별·신고 도우미</title>

      {/* key={step}: 단계마다 새 제목을 만들어서 스크린리더가 확실히 읽게 해요. */}
      <h1 key={step} ref={headingRef} tabIndex={-1} className={styles.heading}>
        {STEP_TITLES[step]}
      </h1>

      {errorMessage && (
        <p className={styles.error} role="alert">
          <span aria-hidden="true">⚠️</span> {errorMessage}
        </p>
      )}

      {step === 'select' && (
        <SelectStep
          modelStatus={modelStatus}
          isPreparing={isPreparing}
          onSelectFile={handleSelectFile}
        />
      )}

      {(step === 'preview' || step === 'analyzing') && (
        <>
          <PhotoPreview src={photoUrl} analyzing={step === 'analyzing'} />
          {step === 'preview' && (
            <div className={styles.actions}>
              <button
                type="button"
                className={`${btn.primary} ${btn.large}`}
                onClick={handleAnalyze}
                disabled={modelStatus !== 'ready'}
              >
                {modelStatus === 'ready' && '판별하기'}
                {modelStatus === 'loading' && 'AI 준비 중…'}
                {modelStatus === 'error' && 'AI를 불러오지 못했어요'}
              </button>
              <button type="button" className={btn.secondary} onClick={resetToSelect}>
                다시 찍기
              </button>
              <ModelStatusMessage status={modelStatus} />
              <p className={styles.privacy}>
                <span aria-hidden="true">🔒</span> 사진은 내 기기 안에서만 분석되며 서버로 전송되지
                않아요.
              </p>
            </div>
          )}
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
        <GuideStep
          judgment={judgment}
          checkedIds={checkedIds}
          onChangeChecked={setCheckedIds}
          onBack={() => setStep('result')}
          onHome={() => navigate('/')}
        />
      )}
    </div>
  );
}

// AI 모델 준비 상태 안내 (준비 중 / 실패)
function ModelStatusMessage({ status }) {
  if (status === 'loading') {
    return (
      <p className={styles.status} role="status">
        AI를 준비하고 있어요…
      </p>
    );
  }
  if (status === 'error') {
    return (
      <div className={styles.error} role="alert">
        <p>AI를 불러오지 못했어요. 인터넷 연결을 확인하고 새로고침해 주세요.</p>
        <button type="button" className={btn.secondary} onClick={() => window.location.reload()}>
          새로고침
        </button>
      </div>
    );
  }
  return null;
}

// 1단계: 사진 고르기
function SelectStep({ modelStatus, isPreparing, onSelectFile }) {
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
      <ModelStatusMessage status={modelStatus} />
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
  const level = judgment.confidenceLevel;

  // LOW: 결과 대신 다시 찍기 안내
  if (level === 'LOW') {
    return (
      <>
        <PhotoPreview src={photoUrl} compact />
        <section className={styles.lowCard} aria-labelledby="low-title">
          <h2 id="low-title" className={styles.lowTitle}>
            <span aria-hidden="true">🤔</span> AI가 사진을 잘 알아보지 못했어요
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
  const secondLabel = findSecondLabel(prediction.probabilities);
  const secondProbability = prediction.probabilities[secondLabel] ?? 0;
  const secondType = getViolationType(secondLabel);

  return (
    <>
      <PhotoPreview src={photoUrl} compact />

      {level === 'MEDIUM' && (
        <p className={styles.warning} role="note">
          <span aria-hidden="true">⚠️</span> AI가 확신하지 못해요. 다른 각도에서 다시 찍어보세요.
        </p>
      )}

      <ResultCard judgment={judgment} confidence={prediction.confidence} />

      {secondProbability >= SECOND_CANDIDATE_MIN && (
        <p className={styles.notice}>
          <span aria-hidden="true">{secondType.icon}</span>{' '}
          {secondLabel === 'NORMAL'
            ? '정상 주차일 가능성도 있어요'
            : `${secondType.title}에도 해당될 수 있어요`}{' '}
          ({toPercent(secondProbability)}%)
        </p>
      )}

      <ProbabilityBars probabilities={prediction.probabilities} topLabel={prediction.label} />

      {saveFailed && (
        <p className={styles.status}>
          서버에 연결하지 못해서 이번 결과는 기록에 남지 않았어요. 안내는 기기에 저장된 내용으로
          보여 드려요.
        </p>
      )}

      <div className={styles.actions}>
        {judgment.violation ? (
          <>
            <button type="button" className={`${btn.primary} ${btn.large}`} onClick={onShowGuide}>
              신고 방법 보기
            </button>
            <button type="button" className={btn.secondary} onClick={onRetake}>
              다른 사진으로 다시 하기
            </button>
          </>
        ) : (
          <button type="button" className={`${btn.primary} ${btn.large}`} onClick={onRetake}>
            다른 사진 확인하기
          </button>
        )}
      </div>

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
    </>
  );
}

// 5단계: 신고 가이드
function GuideStep({ judgment, checkedIds, onChangeChecked, onBack, onHome }) {
  const type = getViolationType(judgment.type);

  return (
    <>
      <p className={styles.typeSummary}>
        <span aria-hidden="true">{type.icon}</span> {judgment.title}
      </p>
      <Checklist items={judgment.checklist} checkedIds={checkedIds} onChange={onChangeChecked} />
      <ReportGuide steps={judgment.reportSteps} reportUrl={judgment.reportUrl} />
      <div className={styles.actions}>
        <button type="button" className={btn.secondary} onClick={onBack}>
          결과 다시 보기
        </button>
        <button type="button" className={btn.secondary} onClick={onHome}>
          처음으로
        </button>
      </div>
      <Disclaimer />
    </>
  );
}
