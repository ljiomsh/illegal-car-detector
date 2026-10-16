// 사진 고르기: "사진 찍기"(카메라) / "갤러리에서 고르기" 버튼 + 촬영 팁
// "사진 찍기"는 브라우저 안에서 카메라 화면을 띄워요. (PC 웹캠도 가능)
// 브라우저가 카메라를 지원하지 않으면(예: https가 아닌 주소) 기기의 기본 카메라 앱을 열어요.
import { useRef, useState } from 'react';
import CameraCapture from './CameraCapture.jsx';
import PhotoTips from './PhotoTips.jsx';
import btn from '../styles/buttons.module.css';
import styles from './PhotoPicker.module.css';

// 이 브라우저에서 카메라 화면을 직접 띄울 수 있는지 확인해요.
// (보안 때문에 https 또는 localhost 주소에서만 쓸 수 있어요)
function canUseCameraInBrowser() {
  return Boolean(window.isSecureContext && navigator.mediaDevices?.getUserMedia);
}

export default function PhotoPicker({ onSelect, disabled = false }) {
  const [isCameraOpen, setIsCameraOpen] = useState(false);
  const cameraInputRef = useRef(null);
  const galleryInputRef = useRef(null);
  const cameraButtonRef = useRef(null);

  // 파일을 고르면 부모(CheckPage)에게 알려 줘요.
  function handleChange(event) {
    const file = event.target.files?.[0];
    // 같은 사진을 다시 골라도 change가 일어나도록 값을 비워 둬요.
    event.target.value = '';
    if (file) onSelect(file);
  }

  // "사진 찍기": 가능하면 브라우저 카메라, 안 되면 기기 카메라 앱
  function openCamera() {
    if (canUseCameraInBrowser()) {
      setIsCameraOpen(true);
    } else {
      cameraInputRef.current?.click();
    }
  }

  // 카메라로 찍은 사진
  function handleCapture(file) {
    setIsCameraOpen(false);
    onSelect(file);
  }

  // 카메라 닫기: 키보드 포커스를 "사진 찍기" 버튼으로 돌려놔요.
  function closeCamera() {
    setIsCameraOpen(false);
    setTimeout(() => cameraButtonRef.current?.focus(), 0);
  }

  // 브라우저 카메라를 쓸 수 없을 때: 기기 카메라 앱(파일 선택)으로
  function openDeviceCamera() {
    setIsCameraOpen(false);
    cameraInputRef.current?.click();
  }

  return (
    <div className={styles.picker}>
      {isCameraOpen ? (
        <CameraCapture
          onCapture={handleCapture}
          onCancel={closeCamera}
          onFallback={openDeviceCamera}
        />
      ) : (
        <div className={styles.buttons}>
          <button
            ref={cameraButtonRef}
            type="button"
            className={`${btn.primary} ${btn.large}`}
            onClick={openCamera}
            disabled={disabled}
          >
            <span aria-hidden="true">📷</span> 사진 찍기
          </button>
          <button
            type="button"
            className={btn.secondary}
            onClick={() => galleryInputRef.current?.click()}
            disabled={disabled}
          >
            <span aria-hidden="true">🖼</span> 갤러리에서 고르기
          </button>
        </div>
      )}

      {/* 실제 파일 선택 창은 숨겨 두고, 위 버튼으로 열어요.
          capture="environment": 휴대폰에서 뒤쪽 카메라 앱을 바로 열어요. */}
      <input
        ref={cameraInputRef}
        type="file"
        accept="image/*"
        capture="environment"
        onChange={handleChange}
        hidden
        tabIndex={-1}
        aria-hidden="true"
      />
      <input
        ref={galleryInputRef}
        type="file"
        accept="image/*"
        onChange={handleChange}
        hidden
        tabIndex={-1}
        aria-hidden="true"
      />

      <PhotoTips />
    </div>
  );
}
