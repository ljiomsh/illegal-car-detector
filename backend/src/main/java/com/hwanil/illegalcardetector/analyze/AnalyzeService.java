package com.hwanil.illegalcardetector.analyze;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.json.JsonMapper;

/**
 * 사진 판별: 비전 LLM에 사진을 보내고 → 답에서 결과를 꺼내 검사·보정해요.
 * 어느 AI를 쓸지는 application.yaml의 ai.provider로 골라요. (claude: 기본 / nvidia)
 * 사진은 저장하지 않고, 요청이 끝나면 버려요.
 */
@Slf4j
@Service
public class AnalyzeService {

	private final VisionClient visionClient;
	private final AnalysisParser analysisParser;

	public AnalyzeService(ClaudeVisionClient claudeVisionClient, NvidiaVisionClient nvidiaVisionClient,
			@Value("${ai.provider:claude}") String provider, JsonMapper jsonMapper) {
		this.visionClient = choose(provider, claudeVisionClient, nvidiaVisionClient);
		this.analysisParser = new AnalysisParser(jsonMapper, visionClient.modelVersion());
		log.info("사진 판별 AI: provider={}, modelVersion={}", provider.trim().toLowerCase(), visionClient.modelVersion());
		if (!visionClient.isConfigured()) {
			log.warn("AI API 키가 없어요. backend/secrets.properties에 키를 넣거나 환경변수로 설정하세요. "
					+ "(claude: ANTHROPIC_API_KEY, nvidia: NVIDIA_API_KEY) 그 전까지 /api/analyze는 503을 돌려줘요.");
		}
	}

	// ai.provider 값에 맞는 AI를 골라요. 모르는 값이면 서버가 켜질 때 바로 알려 줘요.
	static VisionClient choose(String provider, VisionClient claude, VisionClient nvidia) {
		return switch (provider.trim().toLowerCase()) {
			case "claude" -> claude;
			case "nvidia" -> nvidia;
			default -> throw new IllegalArgumentException("ai.provider는 claude 또는 nvidia여야 해요: " + provider);
		};
	}

	public AnalyzeResponse analyze(byte[] image, String mimeType) {
		if (!visionClient.isConfigured()) {
			throw new AiNotConfiguredException();
		}
		String content = visionClient.analyze(image, mimeType);
		AnalyzeResponse result = analysisParser.parse(content);
		log.info("AI 판별 완료: label={}, confidence={}", result.label(), result.confidence());
		return result;
	}
}
