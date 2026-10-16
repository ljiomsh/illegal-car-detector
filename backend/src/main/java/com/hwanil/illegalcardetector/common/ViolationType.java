package com.hwanil.illegalcardetector.common;

/**
 * AI 모델이 판별하는 4가지 유형.
 * 프론트(frontend/src/constants/violationTypes.js)의 LABELS와 이름이 정확히 같아야 해요.
 */
public enum ViolationType {
	CROSSWALK, // 횡단보도 위
	SIDEWALK,  // 인도 위
	HYDRANT,   // 소화전 주변
	NORMAL     // 정상 주차
}
