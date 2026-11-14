package com.hwanil.illegalcardetector.analyze;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * NVIDIA Build API(OpenAI 호환 chat/completions)의 비전 LLM에 사진을 보내서 판별 결과 글을 받아 와요.
 * (application.yaml의 ai.provider가 nvidia일 때 쓰여요. 기본은 Claude예요 → ClaudeVisionClient)
 * - 모델 이름·주소·응답 대기 시간은 application.yaml의 nvidia.* 에서 정해요.
 * - API 키(NVIDIA_API_KEY)는 backend/secrets.properties 파일이나 환경변수에서 읽어요.
 *   (application.yaml처럼 git에 올라가는 파일에는 절대 쓰지 마세요!)
 * - 사진·base64는 로그에 남기지 않아요.
 * - 타임아웃: 연결 10초, 응답 nvidia.read-timeout (기본 60초)
 * - 빠른 응답을 위해 모델의 생각 과정(thinking)을 꺼요. (chat_template_kwargs)
 */
@Slf4j
@Component
public class NvidiaVisionClient implements VisionClient {

	/** 생각 과정을 켰을 때 답 길이 제한 (생각 글 + JSON) */
	static final int REASONING_MAX_TOKENS = 8192;

	private final JsonMapper jsonMapper;
	private final String apiKey;
	private final String baseUrl;
	private final String model;
	private final boolean reasoning;
	private final RestClient restClient;

	// apiKey: secrets.properties 또는 환경변수의 NVIDIA_API_KEY (없으면 빈 값)
	public NvidiaVisionClient(JsonMapper jsonMapper,
			@Value("${nvidia.base-url:https://integrate.api.nvidia.com/v1}") String baseUrl,
			@Value("${nvidia.model:deepseek-ai/deepseek-v4.1-flash}") String model,
			@Value("${nvidia.read-timeout:60s}") Duration readTimeout,
			@Value("${nvidia.reasoning:false}") boolean reasoning,
			@Value("${NVIDIA_API_KEY:}") String apiKey) {
		this.jsonMapper = jsonMapper;
		this.baseUrl = baseUrl;
		this.model = model.trim();
		this.reasoning = reasoning;
		this.apiKey = apiKey == null ? "" : apiKey.trim();

		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
				HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
		requestFactory.setReadTimeout(readTimeout);
		this.restClient = RestClient.builder().requestFactory(requestFactory).build();

		log.info("NVIDIA 비전 모델: model={}, 생각 과정={}, 응답 대기 {}초", this.model, reasoning ? "켬" : "끔",
				readTimeout.toSeconds());
	}

	/** API 키가 있는지 */
	@Override
	public boolean isConfigured() {
		return !apiKey.isEmpty();
	}

	/** 판별 기록에 남길 모델 이름 (예: nvidia/nemotron-3.5-super-vl-120b-a12b → nemotron-3.5-super-vl) */
	@Override
	public String modelVersion() {
		return VisionClient.toModelVersion(model);
	}

	/**
	 * 사진을 보내고 AI의 답(choices[0].message.content)을 돌려줘요.
	 * 추론 과정(reasoning_content 등)은 쓰지 않아요. 실패하면 AiUnavailableException
	 */
	@Override
	public String analyze(byte[] image, String mimeType) {
		String dataUrl = "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(image);

		Map<String, Object> body = Map.of(
				"model", model,
				// 판별 기준은 system 메시지로, 사진은 user 메시지로 보내요.
				"messages", List.of(
						Map.of("role", "system", "content", VisionPrompts.SYSTEM_FOR_NVIDIA),
						Map.of("role", "user", "content", List.of(
								Map.of("type", "text", "text", VisionPrompts.USER),
								Map.of("type", "image_url", "image_url", Map.of("url", dataUrl))))),
				// 같은 사진에 같은 답이 나오도록 무작위성을 거의 없애요.
				"temperature", 0.1,
				"top_p", 0.9,
				// 답은 JSON 하나(약 150토큰)라서 1024면 충분해요. 모델이 길게 늘어놓아도 여기서 끊겨요.
				// 생각 과정을 켜면 생각 글이 앞에 붙어서 더 넉넉하게 줘요.
				"max_tokens", reasoning ? REASONING_MAX_TOKENS : 1024,
				"stream", false,
				// 생각 과정(reasoning)을 켜고 끄는 옵션 (application.yaml의 nvidia.reasoning).
				// 모델마다 이름이 달라서 둘 다 보내요. (모르는 이름은 무시돼요)
				// - thinking: DeepSeek. 켜 두면 생각 글을 1만 자 넘게 써서 약 95초가 걸렸어요.
				// - enable_thinking: Nemotron. 끄면 실측 1~2초예요.
				"chat_template_kwargs", Map.of("thinking", reasoning, "enable_thinking", reasoning));

		byte[] responseBytes;
		try {
			// 글자가 깨지지 않게 JSON을 UTF-8 바이트로 직접 만들어 보내요.
			responseBytes = restClient.post()
					.uri(baseUrl + "/chat/completions")
					.header("Authorization", "Bearer " + apiKey)
					.contentType(MediaType.APPLICATION_JSON)
					.accept(MediaType.APPLICATION_JSON)
					.body(jsonMapper.writeValueAsBytes(body))
					.retrieve()
					.body(byte[].class);
		} catch (RestClientResponseException e) {
			// NVIDIA가 오류를 돌려줌 (401: 키가 틀림, 429: 요청이 너무 많음 등)
			log.warn("NVIDIA API 오류: status={}{}", e.getStatusCode().value(),
					e.getStatusCode().value() == 401 ? " (API 키를 확인하세요)" : "");
			throw new AiUnavailableException("NVIDIA API 오류 " + e.getStatusCode().value(), e);
		} catch (ResourceAccessException e) {
			log.warn("NVIDIA API에 연결하지 못했거나 시간이 초과됐어요: {}", e.getMessage());
			throw new AiUnavailableException("NVIDIA API 연결 실패", e);
		} catch (RestClientException e) {
			log.warn("NVIDIA API 호출 실패: {}", e.getMessage());
			throw new AiUnavailableException("NVIDIA API 호출 실패", e);
		}

		String content = extractContent(responseBytes);
		log.debug("AI 응답(앞 500자): {}", content.length() > 500 ? content.substring(0, 500) : content);
		return content;
	}

	// choices[0].message.content만 꺼내요. 비어 있으면 실패
	private String extractContent(byte[] responseBytes) {
		if (responseBytes == null || responseBytes.length == 0) {
			throw new AiUnavailableException("NVIDIA API 응답이 비어 있어요.");
		}
		JsonNode root;
		try {
			root = jsonMapper.readTree(responseBytes);
		} catch (JacksonException e) {
			throw new AiUnavailableException("NVIDIA API 응답이 JSON이 아니에요.", e);
		}
		JsonNode content = root.path("choices").path(0).path("message").path("content");
		if (!content.isString() || content.asString().isBlank()) {
			throw new AiUnavailableException("AI 응답 내용(content)이 비어 있어요.");
		}
		return content.asString();
	}
}
