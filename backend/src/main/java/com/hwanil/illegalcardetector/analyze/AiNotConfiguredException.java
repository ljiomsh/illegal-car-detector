package com.hwanil.illegalcardetector.analyze;

/**
 * 서버에 NVIDIA_API_KEY 환경변수가 없어서 AI 분석을 할 수 없을 때 → 503 AI_NOT_CONFIGURED
 */
public class AiNotConfiguredException extends RuntimeException {

	public AiNotConfiguredException() {
		super("NVIDIA_API_KEY 환경변수가 설정되지 않았어요.");
	}
}
