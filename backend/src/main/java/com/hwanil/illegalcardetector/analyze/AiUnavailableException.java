package com.hwanil.illegalcardetector.analyze;

/**
 * 외부 AI 분석이 실패했을 때 (NVIDIA 오류, 시간 초과, 이상한 응답 등) → 502 AI_UNAVAILABLE
 * message는 로그에만 남기고, 사용자에게는 정해진 안내 문구만 보여 줘요.
 */
public class AiUnavailableException extends RuntimeException {

	public AiUnavailableException(String message) {
		super(message);
	}

	public AiUnavailableException(String message, Throwable cause) {
		super(message, cause);
	}
}
