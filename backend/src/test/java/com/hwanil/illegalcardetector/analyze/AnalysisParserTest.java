package com.hwanil.illegalcardetector.analyze;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

import com.hwanil.illegalcardetector.common.ViolationType;

import tools.jackson.databind.json.JsonMapper;

/**
 * AI 답에서 JSON을 꺼내고 보정하는 AnalysisParser 테스트
 */
class AnalysisParserTest {

	private final AnalysisParser parser = new AnalysisParser(JsonMapper.builder().build());

	@Test
	void 생각_과정과_코드펜스가_있어도_첫_JSON만_꺼낸다() {
		String content = """
				<think>사진을 보니 {흰 줄}이 보인다. label은 CROSSWALK일 것 같다.</think>
				```json
				{"label":"CROSSWALK","probabilities":{"CROSSWALK":0.86,"SIDEWALK":0.08,"HYDRANT":0.02,"NORMAL":0.04},"reason":"차량 앞부분이 횡단보도 흰 줄 위에 있어요. {참고}"}
				```
				{"label":"NORMAL"}
				""";

		AnalyzeResponse result = parser.parse(content);

		assertThat(result.label()).isEqualTo(ViolationType.CROSSWALK);
		assertThat(result.confidence()).isEqualTo(0.86);
		assertThat(result.probabilities()).containsOnlyKeys(ViolationType.values());
		assertThat(result.reason()).isEqualTo("차량 앞부분이 횡단보도 흰 줄 위에 있어요. {참고}");
		assertThat(result.modelVersion()).isEqualTo("deepseek-v4.1-flash");
	}

	@Test
	void 확률이_빠지거나_합이_1이_아니면_채우고_정규화한다() {
		AnalyzeResponse result = parser.parse("""
				{"label":"sidewalk","probabilities":{"SIDEWALK":3,"NORMAL":"1"}}""");

		assertThat(result.label()).isEqualTo(ViolationType.SIDEWALK);
		assertThat(result.probabilities().get(ViolationType.SIDEWALK)).isEqualTo(0.75);
		assertThat(result.probabilities().get(ViolationType.NORMAL)).isEqualTo(0.25);
		assertThat(result.probabilities().get(ViolationType.CROSSWALK)).isZero();
		assertThat(result.probabilities().get(ViolationType.HYDRANT)).isZero();
		assertThat(result.confidence()).isEqualTo(0.75);
		assertThat(result.reason()).isNull();
	}

	@Test
	void 확률_합이_0이면_label에_1을_준다() {
		AnalyzeResponse result = parser.parse("""
				{"label":"HYDRANT","probabilities":{"CROSSWALK":0,"SIDEWALK":0,"HYDRANT":0,"NORMAL":0}}""");

		assertThat(result.probabilities().get(ViolationType.HYDRANT)).isEqualTo(1.0);
		assertThat(result.confidence()).isEqualTo(1.0);
	}

	@Test
	void label의_확률이_최댓값이_아니면_최댓값과_바꾼다() {
		AnalyzeResponse result = parser.parse("""
				{"label":"NORMAL","probabilities":{"CROSSWALK":0.6,"SIDEWALK":0.1,"HYDRANT":0.1,"NORMAL":0.2}}""");

		assertThat(result.label()).isEqualTo(ViolationType.NORMAL);
		assertThat(result.probabilities().get(ViolationType.NORMAL)).isEqualTo(0.6);
		assertThat(result.probabilities().get(ViolationType.CROSSWALK)).isEqualTo(0.2);
		assertThat(result.confidence()).isEqualTo(0.6);
		double sum = result.probabilities().values().stream().mapToDouble(Double::doubleValue).sum();
		assertThat(sum).isCloseTo(1.0, within(0.001));
	}

	@Test
	void label이_4가지가_아니면_실패한다() {
		assertThatThrownBy(() -> parser.parse("{\"label\":\"PARKING_LOT\",\"probabilities\":{}}"))
				.isInstanceOf(AiUnavailableException.class);
	}

	@Test
	void JSON이_없으면_실패한다() {
		assertThatThrownBy(() -> parser.parse("<think>{\"label\":\"CROSSWALK\"}</think> 잘 모르겠어요."))
				.isInstanceOf(AiUnavailableException.class);
	}
}
