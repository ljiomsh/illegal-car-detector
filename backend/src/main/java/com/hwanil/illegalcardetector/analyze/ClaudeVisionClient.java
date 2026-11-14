package com.hwanil.illegalcardetector.analyze;

import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.messages.Base64ImageSource;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.JsonOutputFormat;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;

import lombok.extern.slf4j.Slf4j;

/**
 * Anthropic의 Claude(비전 LLM)에 사진을 보내서 판별 결과 글(JSON)을 받아 와요. 공식 Java SDK를 써요.
 * - 모델 이름·생각 깊이·응답 대기 시간은 application.yaml의 claude.* 에서 정해요.
 * - API 키(ANTHROPIC_API_KEY)는 backend/secrets.properties 파일이나 환경변수에서 읽어요.
 *   (application.yaml처럼 git에 올라가는 파일에는 절대 쓰지 마세요!)
 * - 사진·base64는 로그에 남기지 않아요.
 * - 답의 모양(JSON 스키마)을 API에 알려 줘서, 정해진 키·값으로만 답하게 해요. (구조화된 출력)
 * - Claude Sonnet 5.5는 temperature 같은 무작위성 옵션을 받지 않아서 보내지 않아요.
 */
@Slf4j
@Component
public class ClaudeVisionClient implements VisionClient {

	/** 답 길이 제한. 답은 JSON 하나(약 200토큰)지만, 모델이 잠깐 생각하는 글도 여기에 포함돼서 넉넉하게 줘요. */
	static final long MAX_TOKENS = 8000;

	/** Claude가 받는 사진 형식 (AnalyzeController가 JPEG·PNG·WebP만 통과시켜요) */
	private static final Map<String, Base64ImageSource.MediaType> MEDIA_TYPES = Map.of(
			"image/jpeg", Base64ImageSource.MediaType.IMAGE_JPEG,
			"image/png", Base64ImageSource.MediaType.IMAGE_PNG,
			"image/webp", Base64ImageSource.MediaType.IMAGE_WEBP);

	private final String apiKey;
	private final String model;
	private final OutputConfig outputConfig;
	private final AnthropicClient client;

	// apiKey: secrets.properties 또는 환경변수의 ANTHROPIC_API_KEY (없으면 빈 값)
	public ClaudeVisionClient(
			@Value("${claude.model:claude-sonnet-5-5}") String model,
			@Value("${claude.effort:low}") String effort,
			@Value("${claude.timeout:30s}") Duration timeout,
			@Value("${ANTHROPIC_API_KEY:}") String apiKey) {
		this.model = model.trim();
		this.apiKey = apiKey == null ? "" : apiKey.trim();
		this.outputConfig = OutputConfig.builder()
				.effort(toEffort(effort))
				.format(JsonOutputFormat.builder().schema(answerSchema()).build())
				.build();
		// 프론트가 70초까지 기다려서, 정해진 시간 안에 답이 없으면 한 번만 다시 시도해요.
		this.client = isConfigured()
				? AnthropicOkHttpClient.builder().apiKey(this.apiKey).timeout(timeout).maxRetries(1).build()
				: null;
		log.info("Claude 비전 모델: model={}, 생각 깊이={}, 응답 대기 {}초", this.model, effort, timeout.toSeconds());
	}

	@Override
	public boolean isConfigured() {
		return !apiKey.isEmpty();
	}

	/** 판별 기록에 남길 모델 이름 (예: claude-sonnet-5-5) */
	@Override
	public String modelVersion() {
		return VisionClient.toModelVersion(model);
	}

	/**
	 * 사진을 보내고 Claude가 쓴 답(JSON 글)을 돌려줘요. 실패하면 AiUnavailableException
	 */
	@Override
	public String analyze(byte[] image, String mimeType) {
		Base64ImageSource.MediaType mediaType = MEDIA_TYPES.get(mimeType);
		if (mediaType == null) {
			throw new AiUnavailableException("Claude가 받지 않는 사진 형식이에요: " + mimeType);
		}
		MessageCreateParams params = MessageCreateParams.builder()
				.model(model)
				.maxTokens(MAX_TOKENS)
				// 판별 기준은 system으로, 사진은 user 메시지로 보내요. (사진을 글보다 먼저 둬요)
				.system(VisionPrompts.SYSTEM_FOR_CLAUDE)
				.addUserMessageOfContentBlockParams(List.of(
						ContentBlockParam.ofImage(Base64ImageSource.builder()
								.mediaType(mediaType)
								.data(Base64.getEncoder().encodeToString(image))
								.build()),
						ContentBlockParam.ofText(VisionPrompts.USER)))
				.outputConfig(outputConfig)
				.build();

		Message response;
		try {
			response = client.messages().create(params);
		} catch (AnthropicServiceException e) {
			// Anthropic이 오류를 돌려줌 (401: 키가 틀림, 429: 요청이 너무 많음, 529: 서버가 바쁨 등)
			log.warn("Claude API 오류: status={}{}", e.statusCode(), e.statusCode() == 401 ? " (API 키를 확인하세요)" : "");
			throw new AiUnavailableException("Claude API 오류 " + e.statusCode(), e);
		} catch (AnthropicException e) {
			log.warn("Claude API에 연결하지 못했거나 시간이 초과됐어요: {}", e.getMessage());
			throw new AiUnavailableException("Claude API 호출 실패", e);
		}

		// 답을 읽기 전에 왜 멈췄는지부터 확인해요.
		StopReason stopReason = response.stopReason().orElse(null);
		if (StopReason.REFUSAL.equals(stopReason)) {
			log.warn("Claude가 이 사진의 판별을 거절했어요.");
			throw new AiUnavailableException("Claude가 답을 거절했어요.");
		}
		if (StopReason.MAX_TOKENS.equals(stopReason)) {
			throw new AiUnavailableException("Claude의 답이 길이 제한에 걸려 끊겼어요.");
		}
		String content = response.content().stream()
				.flatMap(block -> block.text().stream())
				.map(text -> text.text())
				.collect(Collectors.joining());
		if (content.isBlank()) {
			throw new AiUnavailableException("Claude 응답 내용이 비어 있어요.");
		}
		log.debug("AI 응답(앞 500자): {}", content.length() > 500 ? content.substring(0, 500) : content);
		return content;
	}

	// claude.effort 글자를 SDK 값으로 바꿔요. 모르는 값이면 서버가 켜질 때 바로 알려 줘요.
	static OutputConfig.Effort toEffort(String effort) {
		return switch (effort.trim().toLowerCase()) {
			case "low" -> OutputConfig.Effort.LOW;
			case "medium" -> OutputConfig.Effort.MEDIUM;
			case "high" -> OutputConfig.Effort.HIGH;
			default -> throw new IllegalArgumentException("claude.effort는 low, medium, high 중 하나여야 해요: " + effort);
		};
	}

	/**
	 * 답의 모양 (JSON 스키마). VisionPrompts 끝의 형식, AnalysisParser.CHECK_VALUES와 같아야 해요.
	 */
	static JsonOutputFormat.Schema answerSchema() {
		Map<String, Object> checks = object(Map.of(
				"vehicle", oneOf("parked", "moving", "none", "unclear"),
				"crosswalk", oneOf("on", "near", "none"),
				"sidewalk", oneOf("on", "none"),
				"hydrant", oneOf("near", "far", "none"),
				"noParking", oneOf("yellow_line", "intersection", "bus_stop", "school_zone", "fire_lane", "sign", "none")),
				List.of("vehicle", "crosswalk", "sidewalk", "hydrant", "noParking"));
		List<String> labels = List.of("CROSSWALK", "SIDEWALK", "HYDRANT", "OTHER", "NORMAL");
		Map<String, Object> probabilities = object(
				labels.stream().collect(Collectors.toMap(label -> label, label -> Map.of("type", "number"))), labels);
		Map<String, Object> answer = object(Map.of(
				"checks", checks,
				"label", oneOf(labels.toArray(String[]::new)),
				"probabilities", probabilities,
				"reason", Map.of("type", "string")),
				List.of("checks", "label", "probabilities", "reason"));

		JsonOutputFormat.Schema.Builder schema = JsonOutputFormat.Schema.builder();
		answer.forEach((key, value) -> schema.putAdditionalProperty(key, JsonValue.from(value)));
		return schema.build();
	}

	private static Map<String, Object> object(Map<String, ?> properties, List<String> required) {
		return Map.of("type", "object", "properties", properties, "required", required, "additionalProperties", false);
	}

	private static Map<String, Object> oneOf(String... values) {
		return Map.of("type", "string", "enum", List.of(values));
	}
}
