package com.hwanil.illegalcardetector.common;

/**
 * AI 모델이 판별하는 5가지 유형.
 * 프론트(frontend/src/constants/violationTypes.js)의 LABELS와 이름·순서가 정확히 같아야 해요.
 * DB에는 이름(문자열)으로 저장해서, 중간에 유형을 넣어도 예전 기록은 그대로예요.
 */
public enum ViolationType {
	CROSSWALK, // 횡단보도 위
	SIDEWALK,  // 인도 위
	HYDRANT,   // 소화전 주변
	OTHER,     // 기타 불법 주정차 (교차로 모퉁이, 버스 정류소, 어린이보호구역 정문 앞 등)
	NORMAL     // 정상 주차
}
