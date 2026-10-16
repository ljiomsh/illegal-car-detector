// 사진을 긴 변 최대 1024px로 줄여요. (canvas 사용)
// 큰 사진을 그대로 쓰면 화면이 느려지고 AI 분석도 오래 걸려서 미리 줄여요.
//
// 사용법: const blob = await resizeImage(file);
//         const url = URL.createObjectURL(blob);

export async function resizeImage(file, maxSize = 1024) {
  if (!file || !file.type.startsWith('image/')) {
    throw new Error('사진 파일만 고를 수 있어요.');
  }

  const image = await loadImage(file);

  // 긴 변이 maxSize보다 크면 비율을 유지한 채 줄여요. 작으면 그대로 둬요.
  const longSide = Math.max(image.naturalWidth, image.naturalHeight);
  const scale = Math.min(1, maxSize / longSide);
  const width = Math.round(image.naturalWidth * scale);
  const height = Math.round(image.naturalHeight * scale);

  const canvas = document.createElement('canvas');
  canvas.width = width;
  canvas.height = height;
  const context = canvas.getContext('2d');
  context.drawImage(image, 0, 0, width, height);

  return new Promise((resolve, reject) => {
    canvas.toBlob(
      (blob) => {
        if (blob) resolve(blob);
        else reject(new Error('사진을 줄이지 못했어요.'));
      },
      'image/jpeg',
      0.9,
    );
  });
}

// 파일을 <img>로 불러와요. 다 쓰면 임시 주소는 바로 지워요.
function loadImage(file) {
  return new Promise((resolve, reject) => {
    const url = URL.createObjectURL(file);
    const image = new Image();
    image.onload = () => {
      URL.revokeObjectURL(url);
      resolve(image);
    };
    image.onerror = () => {
      URL.revokeObjectURL(url);
      reject(new Error('이 사진은 쓸 수 없어요. 다른 사진을 골라 주세요.'));
    };
    image.src = url;
  });
}

// 화면의 <img>를 긴 변 최대 maxSize의 JPEG 파일(Blob)로 만들어요. (서버로 보내는 사진용)
export function imageToJpegBlob(image, maxSize = 1024, quality = 0.85) {
  const longSide = Math.max(image.naturalWidth, image.naturalHeight);
  const scale = Math.min(1, maxSize / longSide);
  const canvas = document.createElement('canvas');
  canvas.width = Math.round(image.naturalWidth * scale);
  canvas.height = Math.round(image.naturalHeight * scale);
  canvas.getContext('2d').drawImage(image, 0, 0, canvas.width, canvas.height);

  return new Promise((resolve, reject) => {
    canvas.toBlob(
      (blob) => (blob ? resolve(blob) : reject(new Error('사진을 준비하지 못했어요.'))),
      'image/jpeg',
      quality,
    );
  });
}
