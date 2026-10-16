package com.hwanil.illegalcardetector.stats;

import java.util.Map;

import com.hwanil.illegalcardetector.common.ViolationType;

/**
 * GET /api/stats 응답
 * 예: { "total": 120, "byType": { "CROSSWALK": 30, "SIDEWALK": 40, "HYDRANT": 10, "NORMAL": 40 },
 *       "feedbackCount": 25, "feedbackAccuracy": 0.84 }
 *
 * @param byType           유형별 개수 (0건인 유형도 항상 4개 모두 들어 있어요)
 * @param feedbackCount    피드백을 받은 기록 수
 * @param feedbackAccuracy 피드백 중 "맞았다"의 비율 (0~1). 피드백이 0건이면 null
 */
public record StatsResponse(
		long total,
		Map<ViolationType, Long> byType,
		long feedbackCount,
		Double feedbackAccuracy) {
}
