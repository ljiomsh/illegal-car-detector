package com.hwanil.illegalcardetector.common;

/**
 * AI 확신도 등급.
 * 기준값(0.7, 0.4)은 이 파일의 from() 한 곳에서만 정해요.
 * 프론트(frontend/src/constants/violationTypes.js)의 기준과 같은 값으로 맞춰 주세요.
 */
public enum ConfidenceLevel {
	HIGH, MEDIUM, LOW;

	public static final double HIGH_THRESHOLD = 0.7;
	public static final double MEDIUM_THRESHOLD = 0.4;

	/** 확신도(0~1)를 등급으로 바꿔요. 0.7 이상 HIGH, 0.4 이상 MEDIUM, 그 아래는 LOW */
	public static ConfidenceLevel from(double confidence) {
		if (confidence >= HIGH_THRESHOLD) {
			return HIGH;
		}
		if (confidence >= MEDIUM_THRESHOLD) {
			return MEDIUM;
		}
		return LOW;
	}
}
