package com.hwanil.illegalcardetector.analyze;

/**
 * 사진을 볼 수 있는 AI(비전 LLM)에 사진을 보내는 창구.
 * 어느 AI를 쓸지는 application.yaml의 ai.provider로 골라요. (claude 또는 nvidia)
 */
public interface VisionClient {

	/** 판별 기록의 modelVersion 최대 길이 (JudgmentRequest 검사와 같아요) */
	int MAX_MODEL_VERSION_LENGTH = 20;

	/** API 키가 있는지 */
	boolean isConfigured();

	/** 판별 기록에 남길 모델 이름 (20자 이하, 영문·숫자·점·빼기만) */
	String modelVersion();

	/** 사진을 보내고 AI가 쓴 답(JSON 글)을 돌려줘요. 실패하면 AiUnavailableException */
	String analyze(byte[] image, String mimeType);

	/**
	 * 모델 이름 → 판별 기록용 이름. 회사 이름(/ 앞)을 빼고 20자까지만 써요.
	 * 예: deepseek-ai/deepseek-v4.1-flash → deepseek-v4.1-flash
	 */
	static String toModelVersion(String model) {
		String name = model.substring(model.lastIndexOf('/') + 1).replaceAll("[^A-Za-z0-9.-]", "-");
		return name.length() > MAX_MODEL_VERSION_LENGTH ? name.substring(0, MAX_MODEL_VERSION_LENGTH) : name;
	}
}
