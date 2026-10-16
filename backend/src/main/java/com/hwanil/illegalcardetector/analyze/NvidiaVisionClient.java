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
 * NVIDIA Build API(OpenAI 호환 chat/completions)에 사진을 보내서 판별 결과 글을 받아 와요.
 * - API 키(NVIDIA_API_KEY)는 backend/secrets.properties 파일이나 환경변수에서 읽어요.
 *   (application.yaml처럼 git에 올라가는 파일에는 절대 쓰지 마세요!)
 * - 사진·base64는 로그에 남기지 않아요.
 * - 타임아웃: 연결 10초, 응답 60초
 * - 빠른 응답을 위해 모델의 생각 과정(thinking)을 꺼요. (chat_template_kwargs)
 */
@Slf4j
@Component
public class NvidiaVisionClient {

	static final String MODEL = "deepseek-ai/deepseek-v4.1-flash";

	/** 판별 프롬프트 (기획에서 정한 문구 그대로) */
	static final String PROMPT = """
			너는 불법 주정차 판별 도우미야. 사진 속 주차된 차량의 위치를 보고 아래 4가지 중 하나로 분류해.
			- CROSSWALK: 차체 일부라도 횡단보도 흰 줄 위에 있음 (정지선 뒤에 서 있으면 해당 안 됨)
			- SIDEWALK: 바퀴가 보도(인도, 보도블록, 보도 턱 위)에 올라가 있음 (한쪽 바퀴만 걸쳐도 해당)
			- HYDRANT: 소화전이 차량 바로 근처(약 5m 이내)에 보임
			- NORMAL: 위 3가지에 해당하지 않음 (주차칸 안, 일반 도로변 등)
			차량이 없거나 사진이 불분명하면 가장 가능성 높은 것을 고르되 확률을 낮게 줘.
			반드시 아래 형식의 JSON 하나만 출력하고 다른 말은 하지 마:
			{"label":"CROSSWALK|SIDEWALK|HYDRANT|NORMAL","probabilities":{"CROSSWALK":0~1,"SIDEWALK":0~1,"HYDRANT":0~1,"NORMAL":0~1},"reason":"판단 근거를 한국어 한두 문장으로"}""";

	private final JsonMapper jsonMapper;
	private final String apiKey;
	private final String baseUrl;
	private final RestClient restClient;

	// apiKey: secrets.properties 또는 환경변수의 NVIDIA_API_KEY (없으면 빈 값)
	public NvidiaVisionClient(JsonMapper jsonMapper,
			@Value("${nvidia.base-url:https://integrate.api.nvidia.com/v1}") String baseUrl,
			@Value("${NVIDIA_API_KEY:}") String apiKey) {
		this.jsonMapper = jsonMapper;
		this.baseUrl = baseUrl;
		this.apiKey = apiKey == null ? "" : apiKey.trim();

		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
				HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
		requestFactory.setReadTimeout(Duration.ofSeconds(60));
		this.restClient = RestClient.builder().requestFactory(requestFactory).build();

		if (!isConfigured()) {
			log.warn("NVIDIA_API_KEY가 없어요. backend/secrets.properties에 키를 넣거나 환경변수로 설정하세요. "
					+ "그 전까지 /api/analyze는 503을 돌려줘요.");
		}
	}

	/** API 키가 있는지 */
	public boolean isConfigured() {
		return !apiKey.isEmpty();
	}

	/**
	 * 사진을 보내고 AI의 답(choices[0].message.content)을 돌려줘요.
	 * 추론 과정(reasoning_content 등)은 쓰지 않아요. 실패하면 AiUnavailableException
	 */
	public String analyze(byte[] image, String mimeType) {
		String dataUrl = "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(image);

		Map<String, Object> body = Map.of(
				"model", MODEL,
				"messages", List.of(Map.of(
						"role", "user",
						"content", List.of(
								Map.of("type", "text", "text", PROMPT),
								Map.of("type", "image_url", "image_url", Map.of("url", dataUrl))))),
				"temperature", 0.2,
				"top_p", 0.95,
				"max_tokens", 4096,
				"stream", false,
				// 생각 과정(reasoning)을 끄는 옵션. 공식 예제에는 없지만 직접 측정해서 넣었어요.
				// 켜 두면 생각 글을 1만 자 넘게 쓰느라 약 95초가 걸려 시간 초과가 나고, 끄면 약 19초예요.
				"chat_template_kwargs", Map.of("thinking", false));

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
