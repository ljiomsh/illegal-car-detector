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
 * - 모델 이름·주소·응답 대기 시간은 application.yaml의 nvidia.* 에서 정해요.
 * - API 키(NVIDIA_API_KEY)는 backend/secrets.properties 파일이나 환경변수에서 읽어요.
 *   (application.yaml처럼 git에 올라가는 파일에는 절대 쓰지 마세요!)
 * - 사진·base64는 로그에 남기지 않아요.
 * - 타임아웃: 연결 10초, 응답 nvidia.read-timeout (기본 60초)
 * - 빠른 응답을 위해 모델의 생각 과정(thinking)을 꺼요. (chat_template_kwargs)
 */
@Slf4j
@Component
public class NvidiaVisionClient {

	/** 판별 기록의 modelVersion 최대 길이 (JudgmentRequest 검사와 같아요) */
	static final int MAX_MODEL_VERSION_LENGTH = 20;

	/** 생각 과정을 켰을 때 답 길이 제한 (생각 글 + JSON) */
	static final int REASONING_MAX_TOKENS = 8192;

	/**
	 * 판별 기준 (system 메시지). AI는 사진에서 보이는 것을 checks에 정해진 값으로 적고,
	 * 서버(AnalysisParser)가 checks로 같은 규칙을 한 번 더 적용해서 최종 유형을 정해요.
	 * checks의 키·값을 바꾸면 AnalysisParser의 규칙과 프론트 constants/aiChecks.js도 같이 바꿔 주세요.
	 */
	static final String SYSTEM_PROMPT = """
			너는 사진으로 불법 주정차를 판별하는 도우미야. 아래 순서대로 사진을 꼼꼼히 확인하고, 확인한 것을 checks에 정해진 값으로만 적어.
			사진에 실제로 보이는 것만 근거로 삼고, 보이지 않는 것은 추측하지 마.

			1. vehicle: 사진의 중심이 되는 자동차
			   - "parked": 멈춰 세워 둔(주차·정차한) 자동차가 사진에 크게 보임
			   - "moving": 자동차가 있지만 도로를 달리는 중으로 보임
			   - "none": 자동차가 없거나, 멀리 작게만 보여서 알아볼 수 없음
			   - "unclear": 너무 어둡거나 흐리거나 가려져서 판단할 수 없음
			2. crosswalk: 횡단보도(차도 위의 흰색·노란색 줄무늬)
			   - "on": 차 바로 아래(바퀴 사이) 바닥에 줄무늬가 있음. 차체가 줄무늬를 덮고 있어야 해.
			   - "near": 줄무늬가 차 앞이나 뒤에 보이지만 차 아래에는 없음, 차가 줄무늬에서 약 10m(차 두 대 길이) 안에 있음
			   - "none": 횡단보도가 안 보이거나, 차에서 10m보다 멀리 있음
			3. sidewalk: 보도(인도)
			   - "on": 바퀴가 하나라도 연석(턱) 위의 보도·보도블록에 올라가 있음 (한쪽 바퀴만 걸쳐도 on)
			     보도는 회색 보도블록뿐 아니라 초록색·빨간색 등으로 칠한 보도, 점자블록이 깔린 길도 포함해.
			   - "none": 바퀴가 모두 차도(아스팔트)에 있음. 차와 보도 사이에 울타리·볼라드가 있으면 none
			   - 사진을 보도에서 찍어서 사진 앞쪽에 보도블록이 보이는 것만으로는 "on"이 아니야. 차 바퀴가 놓인 바닥을 봐.
			4. hydrant: 소화전
			   - "near": 소화전(빨간색·노란색 기둥)이나 소화전 표시(빨간 연석, '소화전' 글자)가 차에서 약 5m(차 한 대 길이) 안, 같은 쪽 길가에 있음
			   - "far": 소화전이 보이지만 차에서 차 한 대 길이보다 멀거나, 길 건너편·울타리 건너편에 있음
			   - "none": 소화전이 보이지 않음
			5. noParking: 1~4가 아니어도 사진에서 주정차 금지를 알 수 있는 것
			   - "yellow_line": 차 옆 도로 가장자리에 노란 실선이나 노란 겹선이 있음
			   - "intersection": 차가 교차로 모퉁이에서 약 5m 안에 있음
			   - "bus_stop": 차가 버스 정류소 표지판·승강장에서 약 10m 안에 있음
			   - "school_zone": 차가 어린이보호구역(빨간 노면, '어린이보호구역' 글자) 안에 있음
			   - "fire_lane": 차가 소방차 전용구역(빨간 테두리 칸, '소방차 전용' 글자)에 있음
			   - "sign": 주정차 금지 표지판이 차 바로 근처에 있음
			   - "none": 위에 해당하는 것이 없음

			label은 위에서부터 처음 맞는 규칙으로 골라:
			- crosswalk가 "on" → CROSSWALK
			- hydrant가 "near" → HYDRANT
			- sidewalk가 "on" → SIDEWALK
			- crosswalk가 "near"(횡단보도 10m 안)이거나 noParking이 "none"이 아님 → OTHER
			- 그 밖 → NORMAL (흰 주차구획선 안, 주차가 허용된 도로변 등)

			probabilities는 5가지 유형의 가능성(합계 1)이야.
			- 근거가 사진에 또렷하게 보이면 label에 0.8 이상
			- 근거가 일부 가려지거나 애매하면 label에 0.4~0.7
			- vehicle이 "none"이나 "unclear"면 어떤 유형도 0.3을 넘기지 마
			reason은 무엇을 보고 그렇게 판단했는지 한국어 한두 문장으로 써.

			반드시 아래 형식의 JSON 하나만 출력하고 다른 말은 하지 마:
			{"checks":{"vehicle":"parked|moving|none|unclear","crosswalk":"on|near|none","sidewalk":"on|none","hydrant":"near|far|none","noParking":"yellow_line|intersection|bus_stop|school_zone|fire_lane|sign|none"},"label":"CROSSWALK|SIDEWALK|HYDRANT|OTHER|NORMAL","probabilities":{"CROSSWALK":0~1,"SIDEWALK":0~1,"HYDRANT":0~1,"OTHER":0~1,"NORMAL":0~1},"reason":"판단 근거"}""";

	/** 사진과 함께 보내는 요청 문장 (user 메시지) */
	static final String USER_PROMPT = "이 사진의 자동차를 위 순서대로 확인하고 JSON으로 답해 줘.";

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

		log.info("사진 판별 AI: model={}, 생각 과정={}, 응답 대기 {}초", this.model, reasoning ? "켬" : "끔",
				readTimeout.toSeconds());
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
	 * 판별 기록에 남길 모델 이름. 모델 이름에서 회사 이름(/ 앞)을 빼고 20자까지만 써요.
	 * 예: deepseek-ai/deepseek-v4.1-flash → deepseek-v4.1-flash
	 */
	public String modelVersion() {
		return toModelVersion(model);
	}

	static String toModelVersion(String model) {
		String name = model.substring(model.lastIndexOf('/') + 1).replaceAll("[^A-Za-z0-9.-]", "-");
		return name.length() > MAX_MODEL_VERSION_LENGTH ? name.substring(0, MAX_MODEL_VERSION_LENGTH) : name;
	}

	/**
	 * 사진을 보내고 AI의 답(choices[0].message.content)을 돌려줘요.
	 * 추론 과정(reasoning_content 등)은 쓰지 않아요. 실패하면 AiUnavailableException
	 */
	public String analyze(byte[] image, String mimeType) {
		String dataUrl = "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(image);

		Map<String, Object> body = Map.of(
				"model", model,
				// 판별 기준은 system 메시지로, 사진은 user 메시지로 보내요.
				"messages", List.of(
						Map.of("role", "system", "content", SYSTEM_PROMPT),
						Map.of("role", "user", "content", List.of(
								Map.of("type", "text", "text", USER_PROMPT),
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
