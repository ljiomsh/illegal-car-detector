package com.hwanil.illegalcardetector.analyze;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import com.anthropic.models.messages.OutputConfig;

/**
 * ClaudeVisionClient 테스트: 실제 Claude API는 부르지 않고, 설정을 읽는 부분만 확인해요.
 */
class ClaudeVisionClientTest {

	@Test
	void 키가_없으면_설정되지_않은_상태다() {
		ClaudeVisionClient client = new ClaudeVisionClient("claude-sonnet-5-5", "low", Duration.ofSeconds(30), "  ");

		assertThat(client.isConfigured()).isFalse();
	}

	@Test
	void 모델_이름을_판별_기록용_이름으로_쓴다() {
		ClaudeVisionClient client = new ClaudeVisionClient(" claude-sonnet-5-5 ", "low", Duration.ofSeconds(30), "test-key");

		assertThat(client.isConfigured()).isTrue();
		assertThat(client.modelVersion()).isEqualTo("claude-sonnet-5-5");
	}

	@Test
	void 생각_깊이는_low_medium_high만_받는다() {
		assertThat(ClaudeVisionClient.toEffort("low")).isEqualTo(OutputConfig.Effort.LOW);
		assertThat(ClaudeVisionClient.toEffort(" Medium ")).isEqualTo(OutputConfig.Effort.MEDIUM);
		assertThat(ClaudeVisionClient.toEffort("high")).isEqualTo(OutputConfig.Effort.HIGH);
		assertThatThrownBy(() -> ClaudeVisionClient.toEffort("fast")).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void 답의_모양에_checks와_유형_5가지가_모두_들어_있다() {
		String schema = ClaudeVisionClient.answerSchema()._additionalProperties().toString();

		// AnalysisParser.CHECK_VALUES의 키와 유형 이름이 빠지면 판별이 '애매'로 떨어져요.
		assertThat(schema).contains("vehicle", "crosswalk", "sidewalk", "hydrant", "noParking");
		assertThat(schema).contains("CROSSWALK", "SIDEWALK", "HYDRANT", "OTHER", "NORMAL");
		assertThat(schema).contains("yellow_line", "fire_lane", "probabilities", "reason");
	}
}
