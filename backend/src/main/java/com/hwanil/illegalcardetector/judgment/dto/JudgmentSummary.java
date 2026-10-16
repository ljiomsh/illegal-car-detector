package com.hwanil.illegalcardetector.judgment.dto;

import java.time.Instant;
import java.util.Map;

import com.hwanil.illegalcardetector.common.ConfidenceLevel;
import com.hwanil.illegalcardetector.common.ViolationType;
import com.hwanil.illegalcardetector.judgment.Judgment;

/**
 * GET /api/judgments 목록의 한 칸.
 * 프론트 mock(frontend/src/mocks/mockApi.js)이 저장하는 모양과 같아요. 기록 화면(HistoryPage)이 이 모양을 써요.
 * 예: { "judgmentId": 42, "label": "CROSSWALK", "confidence": 0.92, "confidenceLevel": "HIGH",
 *       "probabilities": { ... }, "modelVersion": "v1", "createdAt": "2026-10-16T09:12:00Z",
 *       "feedback": null }
 *
 * @param feedback 피드백이 없으면 null
 */
public record JudgmentSummary(
		Long judgmentId,
		ViolationType label,
		double confidence,
		ConfidenceLevel confidenceLevel,
		Map<String, Double> probabilities,
		String modelVersion,
		Instant createdAt,
		Feedback feedback) {

	/** 피드백 내용. actualLabel은 "잘 모르겠어요"를 골랐거나 맞았다고 했으면 null */
	public record Feedback(boolean correct, ViolationType actualLabel, Instant createdAt) {
	}

	public static JudgmentSummary from(Judgment judgment) {
		Feedback feedback = judgment.getFeedbackCorrect() == null
				? null
				: new Feedback(judgment.getFeedbackCorrect(), judgment.getFeedbackLabel(), judgment.getFeedbackAt());

		return new JudgmentSummary(
				judgment.getId(),
				judgment.getLabel(),
				judgment.getConfidence(),
				ConfidenceLevel.from(judgment.getConfidence()),
				judgment.getProbabilities(),
				judgment.getModelVersion(),
				judgment.getCreatedAt(),
				feedback);
	}
}
