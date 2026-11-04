package com.hwanil.illegalcardetector.analyze;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.hwanil.illegalcardetector.common.ViolationType;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * AI가 보낸 글(content)에서 판별 결과 JSON을 꺼내고, 값을 검사·보정해요.
 * - <think>...</think> 같은 생각 과정이나 ```json 코드펜스가 붙어 있어도 첫 번째 JSON 객체만 꺼내요.
 * - label이 5개 유형이 아니면 실패해요.
 * - 확률은 5개 키를 모두 채우고(없으면 0), 합이 1이 되게 맞춰요. 합이 0이면 label에 1.0을 줘요.
 * - label의 확률이 가장 크지 않으면, 가장 큰 값과 label의 값을 서로 바꿔요. (화면과 기록 API가 "label = 최댓값"을 전제로 해요)
 * - confidence는 label의 확률로 다시 계산해요.
 *
 * 판별 규칙 (AI가 적은 checks로 서버가 한 번 더 확인해요. 기준은 NvidiaVisionClient.SYSTEM_PROMPT와 같아요)
 * - 유형: crosswalk "on" → 횡단보도, hydrant "near" → 소화전, sidewalk "on" → 인도,
 *         crosswalk "near"(횡단보도 10m 안)이거나 noParking이 "none"이 아님 → 기타, 그 밖 → 정상 (위에서부터 처음 맞는 것)
 *   소화전을 인도보다 먼저 봐요. 보도에서 찍은 소화전 사진을 AI가 "바퀴가 보도 위"로 자주 잘못 봐서예요. (평가사진 실측)
 * - AI가 고른 label이 규칙과 다르면 규칙의 유형으로 바꾸고, 확신도를 '보통'(0.6 이하)으로 낮춰요.
 * - 차가 없거나(none) 판단할 수 없으면(unclear) 확신도를 0.3 이하로 낮춰요. → 화면이 '다시 찍기'를 안내해요.
 * - 차가 달리는 중(moving)이면 주차 위반인지 확실하지 않아서 '보통'(0.6 이하)으로 낮춰요.
 * - checks가 없거나 정해진 값이 아니면 AI의 label을 쓰되 '보통'(0.6 이하)으로 낮춰요.
 */
public class AnalysisParser {

	/** 차가 없거나 판단할 수 없을 때 확신도 최대값 (0.4 미만 = LOW → 다시 찍기) */
	static final double NO_VEHICLE_MAX = 0.3;
	/** 근거가 어긋나거나 애매할 때 확신도 최대값 (0.7 미만 = MEDIUM) */
	static final double DOUBTFUL_MAX = 0.6;

	/** checks에 쓸 수 있는 키와 값 (SYSTEM_PROMPT와 같아요) */
	static final Map<String, Set<String>> CHECK_VALUES = checkValues();

	private static final int MAX_REASON_LENGTH = 300;
	private static final Pattern THINK_BLOCK =
			Pattern.compile("<think>.*?</think>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

	private final JsonMapper jsonMapper;
	private final String modelVersion;

	// modelVersion: 결과에 함께 담을 모델 이름 (NvidiaVisionClient.modelVersion())
	public AnalysisParser(JsonMapper jsonMapper, String modelVersion) {
		this.jsonMapper = jsonMapper;
		this.modelVersion = modelVersion;
	}

	/** AI의 답(content)을 AnalyzeResponse로 바꿔요. 문제가 있으면 AiUnavailableException */
	public AnalyzeResponse parse(String content) {
		JsonNode json = findFirstJsonObject(removeThinking(content));

		ViolationType aiLabel = parseLabel(json.path("label"));
		Map<ViolationType, Double> probabilities = normalize(json.path("probabilities"), aiLabel);
		String reason = parseReason(json.path("reason"));
		Map<String, String> checks = parseChecks(json.path("checks"));

		ViolationType label = aiLabel;
		if (checks == null) {
			// AI가 확인 결과를 제대로 적지 않았으면 믿기 어려워요.
			cap(probabilities, label, DOUBTFUL_MAX);
		} else {
			label = ruleLabel(checks);
			if (label != aiLabel) {
				moveTopTo(probabilities, label);
				cap(probabilities, label, DOUBTFUL_MAX);
			}
			switch (checks.get("vehicle")) {
				case "none", "unclear" -> cap(probabilities, label, NO_VEHICLE_MAX);
				case "moving" -> cap(probabilities, label, DOUBTFUL_MAX);
				default -> { }
			}
		}

		return new AnalyzeResponse(label, probabilities.get(label), probabilities, reason, checks, modelVersion);
	}

	/** checks로 정한 유형 (위에서부터 처음 맞는 규칙) */
	static ViolationType ruleLabel(Map<String, String> checks) {
		if ("on".equals(checks.get("crosswalk"))) {
			return ViolationType.CROSSWALK;
		}
		if ("near".equals(checks.get("hydrant"))) {
			return ViolationType.HYDRANT;
		}
		if ("on".equals(checks.get("sidewalk"))) {
			return ViolationType.SIDEWALK;
		}
		// 횡단보도에서 10m 안도 주정차 금지예요. (도로교통법 제32조)
		if ("near".equals(checks.get("crosswalk")) || !"none".equals(checks.get("noParking"))) {
			return ViolationType.OTHER;
		}
		return ViolationType.NORMAL;
	}

	// checks를 읽어요. 키가 하나라도 없거나 정해진 값이 아니면 null (믿을 수 없는 답)
	private static Map<String, String> parseChecks(JsonNode node) {
		if (!node.isObject()) {
			return null;
		}
		Map<String, String> checks = new LinkedHashMap<>();
		for (Map.Entry<String, Set<String>> entry : CHECK_VALUES.entrySet()) {
			JsonNode value = node.path(entry.getKey());
			String text = value.isString() ? value.asString().trim().toLowerCase(Locale.ROOT) : "";
			if (!entry.getValue().contains(text)) {
				return null;
			}
			checks.put(entry.getKey(), text);
		}
		return checks;
	}

	private static Map<String, Set<String>> checkValues() {
		Map<String, Set<String>> values = new LinkedHashMap<>();
		values.put("vehicle", Set.of("parked", "moving", "none", "unclear"));
		values.put("crosswalk", Set.of("on", "near", "none"));
		values.put("sidewalk", Set.of("on", "none"));
		values.put("hydrant", Set.of("near", "far", "none"));
		values.put("noParking", Set.of("yellow_line", "intersection", "bus_stop", "school_zone", "fire_lane", "sign", "none"));
		return Collections.unmodifiableMap(values);
	}

	// label이 가장 큰 확률을 갖도록 가장 큰 값과 서로 바꿔요.
	private static void moveTopTo(Map<ViolationType, Double> probabilities, ViolationType label) {
		ViolationType top = label;
		for (ViolationType type : ViolationType.values()) {
			if (probabilities.get(type) > probabilities.get(top)) {
				top = type;
			}
		}
		if (top != label) {
			double labelValue = probabilities.get(label);
			probabilities.put(label, probabilities.get(top));
			probabilities.put(top, labelValue);
		}
	}

	/**
	 * label의 확률을 max 이하로 낮추고, 줄인 만큼을 나머지에 나눠 줘요. (합계 1, label이 여전히 가장 커요)
	 * 나머지는 원래 비율대로 늘리고, 그러다 label보다 커지는 게 있으면 똑같이 나눠요.
	 */
	static void cap(Map<ViolationType, Double> probabilities, ViolationType label, double max) {
		double labelValue = probabilities.get(label);
		if (labelValue <= max) {
			return;
		}
		double othersSum = 1.0 - labelValue;
		int othersCount = ViolationType.values().length - 1;
		double equalShare = (1.0 - max) / othersCount;
		boolean useEqual = othersSum <= 0;
		if (!useEqual) {
			for (ViolationType type : ViolationType.values()) {
				if (type != label && probabilities.get(type) / othersSum * (1.0 - max) > max) {
					useEqual = true;
				}
			}
		}
		for (ViolationType type : ViolationType.values()) {
			if (type == label) {
				probabilities.put(type, max);
			} else {
				double value = useEqual ? equalShare : probabilities.get(type) / othersSum * (1.0 - max);
				probabilities.put(type, round(value));
			}
		}
	}

	// <think>...</think>를 지워요. 닫는 태그만 있으면 그 뒤의 글만 써요.
	static String removeThinking(String content) {
		String text = THINK_BLOCK.matcher(content).replaceAll("");
		int closing = text.toLowerCase(Locale.ROOT).lastIndexOf("</think>");
		if (closing >= 0) {
			text = text.substring(closing + "</think>".length());
		}
		return text;
	}

	// 글에서 처음 나오는 올바른 JSON 객체 { ... }를 찾아요. (코드펜스나 앞뒤 설명은 무시)
	private JsonNode findFirstJsonObject(String text) {
		int start = text.indexOf('{');
		while (start >= 0) {
			int end = findMatchingBrace(text, start);
			if (end < 0) {
				break; // 닫는 괄호가 없으면 더 찾을 게 없어요.
			}
			try {
				JsonNode node = jsonMapper.readTree(text.substring(start, end + 1));
				if (node.isObject()) {
					return node;
				}
			} catch (JacksonException e) {
				// JSON이 아니면 다음 '{'부터 다시 찾아요.
			}
			start = text.indexOf('{', start + 1);
		}
		throw new AiUnavailableException("AI 응답에서 JSON을 찾지 못했어요.");
	}

	// start 위치의 '{'와 짝이 맞는 '}' 위치. 문자열("...") 안의 괄호는 세지 않아요.
	private static int findMatchingBrace(String text, int start) {
		int depth = 0;
		boolean inString = false;
		boolean escaped = false;
		for (int i = start; i < text.length(); i++) {
			char c = text.charAt(i);
			if (inString) {
				if (escaped) {
					escaped = false;
				} else if (c == '\\') {
					escaped = true;
				} else if (c == '"') {
					inString = false;
				}
			} else if (c == '"') {
				inString = true;
			} else if (c == '{') {
				depth++;
			} else if (c == '}') {
				depth--;
				if (depth == 0) {
					return i;
				}
			}
		}
		return -1;
	}

	private static ViolationType parseLabel(JsonNode node) {
		if (!node.isString()) {
			throw new AiUnavailableException("AI 응답에 label이 없어요.");
		}
		String text = node.asString().trim().toUpperCase(Locale.ROOT);
		for (ViolationType type : ViolationType.values()) {
			if (type.name().equals(text)) {
				return type;
			}
		}
		throw new AiUnavailableException("AI 응답의 label이 5가지 유형이 아니에요: " + text);
	}

	private static Map<ViolationType, Double> normalize(JsonNode node, ViolationType label) {
		Map<ViolationType, Double> raw = new EnumMap<>(ViolationType.class);
		double sum = 0;
		for (ViolationType type : ViolationType.values()) {
			double value = toNumber(node.path(type.name()));
			raw.put(type, value);
			sum += value;
		}

		Map<ViolationType, Double> result = new EnumMap<>(ViolationType.class);
		for (ViolationType type : ViolationType.values()) {
			if (sum <= 0) {
				result.put(type, type == label ? 1.0 : 0.0);
			} else {
				result.put(type, round(raw.get(type) / sum));
			}
		}

		// label의 확률이 가장 크도록 맞춰요.
		ViolationType top = label;
		for (ViolationType type : ViolationType.values()) {
			if (result.get(type) > result.get(top)) {
				top = type;
			}
		}
		if (top != label) {
			double labelValue = result.get(label);
			result.put(label, result.get(top));
			result.put(top, labelValue);
		}
		return result;
	}

	// 숫자나 "0.8" 같은 글자를 숫자로. 음수·이상한 값은 0
	private static double toNumber(JsonNode node) {
		double value = 0;
		if (node.isNumber()) {
			value = node.doubleValue();
		} else if (node.isString()) {
			try {
				value = Double.parseDouble(node.asString().trim());
			} catch (NumberFormatException e) {
				value = 0;
			}
		}
		return Double.isFinite(value) && value > 0 ? value : 0;
	}

	private static String parseReason(JsonNode node) {
		if (!node.isString() || node.asString().isBlank()) {
			return null;
		}
		String reason = node.asString().trim();
		return reason.length() > MAX_REASON_LENGTH ? reason.substring(0, MAX_REASON_LENGTH) + "…" : reason;
	}

	private static double round(double value) {
		return Math.round(value * 10000) / 10000.0;
	}
}
