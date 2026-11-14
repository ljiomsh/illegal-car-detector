package com.hwanil.illegalcardetector.analyze;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 모델 이름 → 판별 기록용 modelVersion 변환 테스트
 * (modelVersion은 JudgmentRequest 검사: 20자 이하, 영문·숫자·점·빼기만)
 */
class VisionClientTest {

	@Test
	void 회사_이름을_빼고_모델_이름만_쓴다() {
		assertThat(VisionClient.toModelVersion("deepseek-ai/deepseek-v4.1-flash")).isEqualTo("deepseek-v4.1-flash");
	}

	@Test
	void 쓸_수_없는_글자는_빼기로_바꾸고_20자까지만_쓴다() {
		String version = VisionClient.toModelVersion("meta/llama-3.2_90b-vision-instruct");

		assertThat(version).isEqualTo("llama-3.2-90b-vision");
		assertThat(version).matches("^[A-Za-z0-9.-]{1,20}$");
	}
}
