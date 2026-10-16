package com.hwanil.illegalcardetector.analyze;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import com.hwanil.illegalcardetector.common.ViolationType;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * AI가 보낸 글(content)에서 판별 결과 JSON을 꺼내고, 값을 검사·보정해요.
 * - <think>...</think> 같은 생각 과정이나 ```json 코드펜스가 붙어 있어도 첫 번째 JSON 객체만 꺼내요.
 * - label이 4개 유형이 아니면 실패해요.
 * - 확률은 4개 키를 모두 채우고(없으면 0), 합이 1이 되게 맞춰요. 합이 0이면 label에 1.0을 줘요.
 * - label의 확률이 가장 크지 않으면, 가장 큰 값과 label의 값을 서로 바꿔요. (화면과 기록 API가 "label = 최댓값"을 전제로 해요)
 * - confidence는 label의 확률로 다시 계산해요.
 */
public class AnalysisParser {

	public static final String MODEL_VERSION = "deepseek-v4.1-flash";

	private static final int MAX_REASON_LENGTH = 300;
	private static final Pattern THINK_BLOCK =
			Pattern.compile("<think>.*?</think>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

	private final JsonMapper jsonMapper;

	public AnalysisParser(JsonMapper jsonMapper) {
		this.jsonMapper = jsonMapper;
	}

	/** AI의 답(content)을 AnalyzeResponse로 바꿔요. 문제가 있으면 AiUnavailableException */
	public AnalyzeResponse parse(String content) {
		JsonNode json = findFirstJsonObject(removeThinking(content));

		ViolationType label = parseLabel(json.path("label"));
		Map<ViolationType, Double> probabilities = normalize(json.path("probabilities"), label);
		String reason = parseReason(json.path("reason"));

		return new AnalyzeResponse(label, probabilities.get(label), probabilities, reason, MODEL_VERSION);
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
		throw new AiUnavailableException("AI 응답의 label이 4가지 유형이 아니에요: " + text);
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
