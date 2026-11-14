package com.hwanil.illegalcardetector.analyze;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

import com.hwanil.illegalcardetector.common.ViolationType;

import tools.jackson.databind.json.JsonMapper;

/**
 * AI 답에서 JSON을 꺼내고, 판별 규칙(checks)으로 유형과 확신도를 정하는 AnalysisParser 테스트
 */
class AnalysisParserTest {

	private final AnalysisParser parser = new AnalysisParser(JsonMapper.builder().build(), "claude-sonnet-5-5");

	// 차가 있고 아무 위반 표시도 없는 checks. 필요한 칸만 바꿔서 써요.
	private static String checks(String crosswalk, String sidewalk, String hydrant, String noParking, String vehicle) {
		return """
				"checks":{"vehicle":"%s","crosswalk":"%s","sidewalk":"%s","hydrant":"%s","noParking":"%s"}"""
				.formatted(vehicle, crosswalk, sidewalk, hydrant, noParking);
	}

	private static final String PARKED_NOTHING = checks("none", "none", "none", "none", "parked");

	private static double sum(AnalyzeResponse result) {
		return result.probabilities().values().stream().mapToDouble(Double::doubleValue).sum();
	}

	@Test
	void 생각_과정과_코드펜스가_있어도_첫_JSON만_꺼낸다() {
		String content = """
				<think>사진을 보니 {흰 줄}이 보인다. label은 CROSSWALK일 것 같다.</think>
				```json
				{%s,"label":"CROSSWALK","probabilities":{"CROSSWALK":0.86,"SIDEWALK":0.08,"HYDRANT":0.02,"OTHER":0.0,"NORMAL":0.04},"reason":"차량 앞부분이 횡단보도 흰 줄 위에 있어요. {참고}"}
				```
				{"label":"NORMAL"}
				""".formatted(checks("on", "none", "none", "none", "parked"));

		AnalyzeResponse result = parser.parse(content);

		assertThat(result.label()).isEqualTo(ViolationType.CROSSWALK);
		assertThat(result.confidence()).isEqualTo(0.86);
		assertThat(result.probabilities()).containsOnlyKeys(ViolationType.values());
		assertThat(result.reason()).isEqualTo("차량 앞부분이 횡단보도 흰 줄 위에 있어요. {참고}");
		assertThat(result.checks()).containsEntry("crosswalk", "on").containsEntry("vehicle", "parked");
		assertThat(result.modelVersion()).isEqualTo("claude-sonnet-5-5");
	}

	@Test
	void 확률이_빠지거나_합이_1이_아니면_채우고_정규화한다() {
		AnalyzeResponse result = parser.parse("""
				{%s,"label":"sidewalk","probabilities":{"SIDEWALK":3,"NORMAL":"1"}}"""
				.formatted(checks("none", "on", "none", "none", "parked")));

		assertThat(result.label()).isEqualTo(ViolationType.SIDEWALK);
		assertThat(result.probabilities().get(ViolationType.SIDEWALK)).isEqualTo(0.75);
		assertThat(result.probabilities().get(ViolationType.NORMAL)).isEqualTo(0.25);
		assertThat(result.probabilities().get(ViolationType.CROSSWALK)).isZero();
		assertThat(result.probabilities().get(ViolationType.OTHER)).isZero();
		assertThat(result.confidence()).isEqualTo(0.75);
		assertThat(result.reason()).isNull();
	}

	@Test
	void 확률_합이_0이면_label에_1을_준다() {
		AnalyzeResponse result = parser.parse("""
				{%s,"label":"HYDRANT","probabilities":{"CROSSWALK":0,"SIDEWALK":0,"HYDRANT":0,"OTHER":0,"NORMAL":0}}"""
				.formatted(checks("none", "none", "near", "none", "parked")));

		assertThat(result.probabilities().get(ViolationType.HYDRANT)).isEqualTo(1.0);
		assertThat(result.confidence()).isEqualTo(1.0);
	}

	@Test
	void label의_확률이_최댓값이_아니면_최댓값과_바꾼다() {
		AnalyzeResponse result = parser.parse("""
				{%s,"label":"NORMAL","probabilities":{"CROSSWALK":0.6,"SIDEWALK":0.1,"HYDRANT":0.1,"OTHER":0.0,"NORMAL":0.2}}"""
				.formatted(PARKED_NOTHING));

		assertThat(result.label()).isEqualTo(ViolationType.NORMAL);
		assertThat(result.probabilities().get(ViolationType.NORMAL)).isEqualTo(0.6);
		assertThat(result.probabilities().get(ViolationType.CROSSWALK)).isEqualTo(0.2);
		assertThat(result.confidence()).isEqualTo(0.6);
		assertThat(sum(result)).isCloseTo(1.0, within(0.001));
	}

	@Test
	void AI의_label이_규칙과_다르면_규칙의_유형으로_바꾸고_확신도를_보통으로_낮춘다() {
		// 바퀴가 연석 위에 있다고 적어 놓고 label은 NORMAL로 고른 경우
		AnalyzeResponse result = parser.parse("""
				{%s,"label":"NORMAL","probabilities":{"CROSSWALK":0,"SIDEWALK":0.1,"HYDRANT":0,"OTHER":0,"NORMAL":0.9}}"""
				.formatted(checks("none", "on", "none", "none", "parked")));

		assertThat(result.label()).isEqualTo(ViolationType.SIDEWALK);
		assertThat(result.confidence()).isEqualTo(AnalysisParser.DOUBTFUL_MAX);
		assertThat(result.probabilities().values()).allMatch(value -> value <= result.confidence());
		assertThat(sum(result)).isCloseTo(1.0, within(0.001));
	}

	@Test
	void 규칙은_횡단보도_소화전_인도_기타_정상_순서로_먼저_맞는_것을_고른다() {
		AnalyzeResponse result = parser.parse("""
				{%s,"label":"CROSSWALK","probabilities":{"CROSSWALK":0.8,"SIDEWALK":0.1,"HYDRANT":0.1,"OTHER":0,"NORMAL":0}}"""
				.formatted(checks("on", "on", "near", "yellow_line", "parked")));
		assertThat(result.label()).isEqualTo(ViolationType.CROSSWALK);

		// 소화전이 가까우면 바퀴가 보도 위라고 해도 소화전이 먼저예요.
		AnalyzeResponse hydrant = parser.parse("""
				{%s,"label":"HYDRANT","probabilities":{"HYDRANT":0.8,"SIDEWALK":0.2}}"""
				.formatted(checks("none", "on", "near", "none", "parked")));
		assertThat(hydrant.label()).isEqualTo(ViolationType.HYDRANT);
		assertThat(hydrant.confidence()).isEqualTo(0.8);

		AnalyzeResponse other = parser.parse("""
				{%s,"label":"OTHER","probabilities":{"OTHER":0.85,"NORMAL":0.15}}"""
				.formatted(checks("none", "none", "far", "bus_stop", "parked")));
		assertThat(other.label()).isEqualTo(ViolationType.OTHER);
		assertThat(other.confidence()).isEqualTo(0.85);
	}

	@Test
	void 횡단보도_10m_안이면_줄_밖이어도_기타로_판별한다() {
		AnalyzeResponse result = parser.parse("""
				{%s,"label":"OTHER","probabilities":{"OTHER":0.8,"CROSSWALK":0.2}}"""
				.formatted(checks("near", "none", "none", "none", "parked")));

		assertThat(result.label()).isEqualTo(ViolationType.OTHER);
		assertThat(result.confidence()).isEqualTo(0.8);
	}

	@Test
	void 차가_없으면_확신도를_낮춰서_다시_찍기로_보낸다() {
		AnalyzeResponse result = parser.parse("""
				{%s,"label":"NORMAL","probabilities":{"NORMAL":1.0},"reason":"차가 보이지 않아요."}"""
				.formatted(checks("none", "none", "none", "none", "none")));

		assertThat(result.label()).isEqualTo(ViolationType.NORMAL);
		assertThat(result.confidence()).isEqualTo(AnalysisParser.NO_VEHICLE_MAX);
		assertThat(result.probabilities().values()).allMatch(value -> value <= AnalysisParser.NO_VEHICLE_MAX);
		assertThat(sum(result)).isCloseTo(1.0, within(0.001));
	}

	@Test
	void 차가_달리는_중이면_확신도를_보통으로_낮춘다() {
		AnalyzeResponse result = parser.parse("""
				{%s,"label":"CROSSWALK","probabilities":{"CROSSWALK":0.9,"NORMAL":0.1}}"""
				.formatted(checks("on", "none", "none", "none", "moving")));

		assertThat(result.label()).isEqualTo(ViolationType.CROSSWALK);
		assertThat(result.confidence()).isEqualTo(AnalysisParser.DOUBTFUL_MAX);
	}

	@Test
	void checks가_없거나_정해진_값이_아니면_AI의_label을_쓰되_확신도를_보통으로_낮춘다() {
		AnalyzeResponse missing = parser.parse("""
				{"label":"HYDRANT","probabilities":{"HYDRANT":0.9,"NORMAL":0.1}}""");
		assertThat(missing.label()).isEqualTo(ViolationType.HYDRANT);
		assertThat(missing.confidence()).isEqualTo(AnalysisParser.DOUBTFUL_MAX);
		assertThat(missing.checks()).isNull();

		AnalyzeResponse wrongValue = parser.parse("""
				{%s,"label":"HYDRANT","probabilities":{"HYDRANT":0.9,"NORMAL":0.1}}"""
				.formatted(checks("none", "none", "maybe", "none", "parked")));
		assertThat(wrongValue.checks()).isNull();
		assertThat(wrongValue.confidence()).isEqualTo(AnalysisParser.DOUBTFUL_MAX);
	}

	@Test
	void label이_5가지가_아니면_실패한다() {
		assertThatThrownBy(() -> parser.parse("{\"label\":\"PARKING_LOT\",\"probabilities\":{}}"))
				.isInstanceOf(AiUnavailableException.class);
	}

	@Test
	void JSON이_없으면_실패한다() {
		assertThatThrownBy(() -> parser.parse("<think>{\"label\":\"CROSSWALK\"}</think> 잘 모르겠어요."))
				.isInstanceOf(AiUnavailableException.class);
	}
}
