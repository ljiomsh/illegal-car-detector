// 브라우저 안에서 카메라 화면을 띄우고, "촬영하기"를 누르면 사진 파일을 만들어 줘요.
// onCapture(file): 찍은 사진(File)을 부모에게 넘겨요.
// onCancel(): 카메라를 닫아요.
// onFallback(): 카메라를 쓸 수 없을 때 기기의 기본 카메라 앱(파일 선택)으로 넘어가요.
import { useEffect, useRef, useState } from 'react';
import btn from '../styles/buttons.module.css';
import styles from './CameraCapture.module.css';

// 카메라 오류 종류에 따라 안내 문구를 골라요.
function getCameraErrorMessage(error) {
  if (error?.name === 'NotAllowedError') {
    return '카메라 사용이 허용되지 않았어요. 브라우저 설정에서 카메라 권한을 허용해 주세요.';
  }
  if (error?.name === 'NotFoundError' || error?.name === 'OverconstrainedError') {
    return '사용할 수 있는 카메라를 찾지 못했어요.';
  }
  if (error?.name === 'NotReadableError') {
    return '다른 앱이 카메라를 쓰고 있어요. 그 앱을 닫고 다시 시도해 주세요.';
  }
  return '카메라를 켜지 못했어요.';
}

export default function CameraCapture({ onCapture, onCancel, onFallback }) {
  const [status, setStatus] = useState('starting'); // 'starting' | 'ready' | 'error'
  const [errorMessage, setErrorMessage] = useState('');
  const videoRef = useRef(null);
  const streamRef = useRef(null);
  const shutterRef = useRef(null);

  // 화면에 나타나면 카메라를 켜고, 사라지면 꼭 꺼요. (안 끄면 카메라 불이 계속 켜져 있어요)
  useEffect(() => {
    let cancelled = false;

    navigator.mediaDevices
      .getUserMedia({
        video: {
          facingMode: { ideal: 'environment' }, // 휴대폰이면 뒤쪽 카메라
          width: { ideal: 1920 },
          height: { ideal: 1080 },
        },
        audio: false,
      })
      .then((stream) => {
        if (cancelled) {
          stream.getTracks().forEach((track) => track.stop());
          return;
        }
        streamRef.current = stream;
        videoRef.current.srcObject = stream;
        setStatus('ready');
      })
      .catch((error) => {
        if (cancelled) return;
        console.warn('카메라를 켜지 못했어요:', error);
        setErrorMessage(getCameraErrorMessage(error));
        setStatus('error');
      });

    return () => {
      cancelled = true;
      streamRef.current?.getTracks().forEach((track) => track.stop());
      streamRef.current = null;
    };
  }, []);

  // 카메라가 켜지면 키보드 포커스를 "촬영하기" 버튼으로 옮겨요.
  useEffect(() => {
    if (status === 'ready') shutterRef.current?.focus();
  }, [status]);

  // 지금 카메라 화면을 사진 한 장으로 만들어요.
  function handleShoot() {
    const video = videoRef.current;
    if (!video || !video.videoWidth) return;

    const canvas = document.createElement('canvas');
    canvas.width = video.videoWidth;
    canvas.height = video.videoHeight;
    canvas.getContext('2d').drawImage(video, 0, 0, canvas.width, canvas.height);

    canvas.toBlob(
      (blob) => {
        if (!blob) {
          setErrorMessage('사진을 만들지 못했어요. 다시 시도해 주세요.');
          setStatus('error');
          return;
        }
        onCapture(new File([blob], `camera-${Date.now()}.jpg`, { type: 'image/jpeg' }));
      },
      'image/jpeg',
      0.92,
    );
  }

  if (status === 'error') {
    return (
      <section className={styles.camera} aria-labelledby="camera-title">
        <h2 id="camera-title" className={styles.title}>
          카메라
        </h2>
        <p className={styles.error} role="alert">
          <span aria-hidden="true">⚠️</span> {errorMessage}
        </p>
        <button type="button" className={btn.primary} onClick={onFallback}>
          <span aria-hidden="true">📱</span> 기기 카메라 앱으로 찍기
        </button>
        <button type="button" className={btn.secondary} onClick={onCancel}>
          닫기
        </button>
      </section>
    );
  }

  return (
    <section className={styles.camera} aria-labelledby="camera-title">
      <h2 id="camera-title" className={styles.title}>
        카메라
      </h2>

      <div className={styles.viewfinder}>
        {/* playsInline: 아이폰에서 전체 화면으로 바뀌지 않게, muted: 자동 재생이 막히지 않게 */}
        <video
          ref={videoRef}
          className={styles.video}
          autoPlay
          playsInline
          muted
          aria-label="카메라 화면"
        />
        {status === 'starting' && (
          <p className={styles.starting} role="status">
            카메라를 켜고 있어요…
          </p>
        )}
      </div>

      <button
        ref={shutterRef}
        type="button"
        className={`${btn.primary} ${btn.large}`}
        onClick={handleShoot}
        disabled={status !== 'ready'}
      >
        <span aria-hidden="true">📸</span> 촬영하기
      </button>
      <button type="button" className={btn.secondary} onClick={onCancel}>
        취소
      </button>
    </section>
  );
}
