package com.hwanil.illegalcardetector.analyze;

import java.io.IOException;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 사진 판별 API
 * - POST /api/analyze (multipart/form-data, 필드 이름 image)
 * - jpeg/png/webp만, 최대 5MB. 그 외는 400
 * - 키가 없으면 503, AI 분석 실패는 502
 */
@RestController
public class AnalyzeController {

	static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;

	private final AnalyzeService analyzeService;

	// 필요한 객체를 스프링이 생성자로 넣어 줘요. (생성자 주입)
	public AnalyzeController(AnalyzeService analyzeService) {
		this.analyzeService = analyzeService;
	}

	@PostMapping(path = "/api/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public AnalyzeResponse analyze(@RequestParam("image") MultipartFile image) throws IOException {
		if (image.isEmpty()) {
			throw new InvalidImageException("사진 파일이 비어 있어요.");
		}
		if (image.getSize() > MAX_IMAGE_BYTES) {
			throw new InvalidImageException("사진은 5MB 이하만 올릴 수 있어요.");
		}
		byte[] bytes = image.getBytes();
		// 파일 이름이나 형식 정보는 바꿔서 보낼 수 있으니, 파일 앞부분을 직접 보고 형식을 확인해요.
		String mimeType = detectImageType(bytes);
		if (mimeType == null) {
			throw new InvalidImageException("JPG, PNG, WEBP 사진만 올릴 수 있어요.");
		}
		return analyzeService.analyze(bytes, mimeType);
	}

	/** 파일 앞부분(매직 바이트)으로 jpeg/png/webp를 알아내요. 아니면 null */
	static String detectImageType(byte[] b) {
		if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
			return "image/jpeg";
		}
		if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
				&& b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A) {
			return "image/png";
		}
		if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
				&& b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
			return "image/webp";
		}
		return null;
	}

	/** 올린 파일이 규칙에 맞지 않을 때 → 400 */
	public static class InvalidImageException extends RuntimeException {
		public InvalidImageException(String message) {
			super(message);
		}
	}
}
