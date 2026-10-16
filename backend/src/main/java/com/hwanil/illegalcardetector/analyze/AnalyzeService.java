package com.hwanil.illegalcardetector.analyze;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.json.JsonMapper;

/**
 * 사진 판별: NVIDIA AI에 사진을 보내고 → 답에서 결과를 꺼내 검사·보정해요.
 * 사진은 저장하지 않고, 요청이 끝나면 버려요.
 */
@Slf4j
@Service
public class AnalyzeService {

	private final NvidiaVisionClient nvidiaVisionClient;
	private final AnalysisParser analysisParser;

	public AnalyzeService(NvidiaVisionClient nvidiaVisionClient, JsonMapper jsonMapper) {
		this.nvidiaVisionClient = nvidiaVisionClient;
		this.analysisParser = new AnalysisParser(jsonMapper);
	}

	public AnalyzeResponse analyze(byte[] image, String mimeType) {
		if (!nvidiaVisionClient.isConfigured()) {
			throw new AiNotConfiguredException();
		}
		String content = nvidiaVisionClient.analyze(image, mimeType);
		AnalyzeResponse result = analysisParser.parse(content);
		log.info("AI 판별 완료: label={}, confidence={}", result.label(), result.confidence());
		return result;
	}
}
