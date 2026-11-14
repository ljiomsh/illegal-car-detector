package com.hwanil.illegalcardetector.analyze;

import java.util.Map;

import com.hwanil.illegalcardetector.common.ViolationType;

/**
 * POST /api/analyze 응답 (비전 LLM이 사진을 보고 판별한 결과)
 * 예: { "label": "CROSSWALK", "confidence": 0.86,
 *       "probabilities": { "CROSSWALK": 0.86, "SIDEWALK": 0.08, "HYDRANT": 0.02, "OTHER": 0.0, "NORMAL": 0.04 },
 *       "reason": "차량 앞부분이 횡단보도 흰 줄 위에 걸쳐 있어요.",
 *       "checks": { "vehicle": "parked", "crosswalk": "on", "sidewalk": "none", "hydrant": "none", "noParking": "none" },
 *       "modelVersion": "claude-sonnet-5-5" }
 *
 * @param label         판별 유형 (checks가 있으면 서버가 규칙으로 정한 값)
 * @param confidence    label의 확률 (서버가 다시 계산한 값)
 * @param probabilities 5개 유형의 확률 (합계 1이 되게 서버가 맞춘 값)
 * @param reason        AI가 말한 판단 근거 (없으면 null)
 * @param checks        AI가 사진에서 확인한 것 (AnalysisParser.CHECK_VALUES의 키·값). 제대로 답하지 않았으면 null
 * @param modelVersion  판별한 모델 이름 (application.yaml의 claude.model 또는 nvidia.model에서 만들어요)
 */
public record AnalyzeResponse(
		ViolationType label,
		double confidence,
		Map<ViolationType, Double> probabilities,
		String reason,
		Map<String, String> checks,
		String modelVersion) {
}
