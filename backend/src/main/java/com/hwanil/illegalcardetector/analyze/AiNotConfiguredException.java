package com.hwanil.illegalcardetector.analyze;

/**
 * 서버에 AI API 키(ANTHROPIC_API_KEY 또는 NVIDIA_API_KEY)가 없어서 AI 분석을 할 수 없을 때 → 503 AI_NOT_CONFIGURED
 */
public class AiNotConfiguredException extends RuntimeException {

	public AiNotConfiguredException() {
		super("AI API 키가 설정되지 않았어요.");
	}
}
