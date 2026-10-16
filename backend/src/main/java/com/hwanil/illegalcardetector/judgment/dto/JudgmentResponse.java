package com.hwanil.illegalcardetector.judgment.dto;

import java.util.List;

import com.hwanil.illegalcardetector.common.ConfidenceLevel;
import com.hwanil.illegalcardetector.common.ViolationType;
import com.hwanil.illegalcardetector.guide.Guide;
import com.hwanil.illegalcardetector.judgment.Judgment;

/**
 * POST /api/judgments 응답: 저장된 기록 번호 + 유형별 안내.
 * 프론트 mock(frontend/src/mocks/guides.js의 buildGuideResponse)과 필드 이름이 같아야 해요.
 */
public record JudgmentResponse(
		Long judgmentId,
		ViolationType type,
		String title,
		boolean violation,
		ConfidenceLevel confidenceLevel,
		List<String> dangerReasons,
		List<Guide.ChecklistItem> checklist,
		List<String> reportSteps,
		String reportUrl) {

	public static JudgmentResponse of(Judgment judgment, Guide guide) {
		return new JudgmentResponse(
				judgment.getId(),
				judgment.getLabel(),
				guide.title(),
				guide.violation(),
				ConfidenceLevel.from(judgment.getConfidence()),
				guide.dangerReasons(),
				guide.checklist(),
				guide.reportSteps(),
				guide.reportUrl());
	}
}
