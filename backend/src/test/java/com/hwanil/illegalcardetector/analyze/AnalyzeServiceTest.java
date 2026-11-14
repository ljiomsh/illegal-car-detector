package com.hwanil.illegalcardetector.analyze;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;

import com.hwanil.illegalcardetector.common.ViolationType;

import tools.jackson.databind.json.JsonMapper;

/**
 * AnalyzeService 테스트: 진짜 AI 대신 가짜(Mock)를 두고, 어느 AI를 고르는지와 결과 처리를 확인해요.
 */
class AnalyzeServiceTest {

	private static final String ANSWER = """
			{"checks":{"vehicle":"parked","crosswalk":"on","sidewalk":"none","hydrant":"none","noParking":"none"},
			 "label":"CROSSWALK",
			 "probabilities":{"CROSSWALK":0.9,"SIDEWALK":0.02,"HYDRANT":0.02,"OTHER":0.03,"NORMAL":0.03},
			 "reason":"차가 횡단보도 줄무늬 위에 서 있어요."}""";

	private final ClaudeVisionClient claude = mock(ClaudeVisionClient.class);
	private final NvidiaVisionClient nvidia = mock(NvidiaVisionClient.class);

	@Test
	void provider에_맞는_AI를_고른다() {
		assertThat(AnalyzeService.choose("claude", claude, nvidia)).isSameAs(claude);
		assertThat(AnalyzeService.choose(" NVIDIA ", claude, nvidia)).isSameAs(nvidia);
		assertThatThrownBy(() -> AnalyzeService.choose("gpt", claude, nvidia)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void 고른_AI의_답과_모델_이름으로_결과를_만든다() {
		given(claude.modelVersion()).willReturn("claude-sonnet-5-5");
		given(claude.isConfigured()).willReturn(true);
		byte[] image = { 1, 2, 3 };
		given(claude.analyze(image, "image/jpeg")).willReturn(ANSWER);
		AnalyzeService service = new AnalyzeService(claude, nvidia, "claude", JsonMapper.builder().build());

		AnalyzeResponse result = service.analyze(image, "image/jpeg");

		assertThat(result.label()).isEqualTo(ViolationType.CROSSWALK);
		assertThat(result.modelVersion()).isEqualTo("claude-sonnet-5-5");
	}

	@Test
	void 고른_AI의_키가_없으면_AiNotConfiguredException() {
		given(nvidia.modelVersion()).willReturn("nemotron-3.5-super-vl");
		given(nvidia.isConfigured()).willReturn(false);
		AnalyzeService service = new AnalyzeService(claude, nvidia, "nvidia", JsonMapper.builder().build());

		assertThatThrownBy(() -> service.analyze(new byte[] { 1 }, "image/jpeg"))
				.isInstanceOf(AiNotConfiguredException.class);
	}
}
