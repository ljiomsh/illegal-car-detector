package com.hwanil.illegalcardetector.analyze;

import java.util.Map;

import com.hwanil.illegalcardetector.common.ViolationType;

/**
 * POST /api/analyze 응답 (외부 AI가 사진을 보고 판별한 결과)
 * 예: { "label": "CROSSWALK", "confidence": 0.86,
 *       "probabilities": { "CROSSWALK": 0.86, "SIDEWALK": 0.08, "HYDRANT": 0.02, "NORMAL": 0.04 },
 *       "reason": "차량 앞부분이 횡단보도 흰 줄 위에 걸쳐 있어요.",
 *       "modelVersion": "deepseek-v4.1-flash" }
 *
 * @param confidence    label의 확률 (서버가 다시 계산한 값)
 * @param probabilities 4개 유형의 확률 (합계 1이 되게 서버가 맞춘 값)
 * @param reason        AI가 말한 판단 근거 (없으면 null)
 */
public record AnalyzeResponse(
		ViolationType label,
		double confidence,
		Map<ViolationType, Double> probabilities,
		String reason,
		String modelVersion) {
}
