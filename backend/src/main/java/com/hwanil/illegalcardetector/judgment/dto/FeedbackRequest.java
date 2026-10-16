package com.hwanil.illegalcardetector.judgment.dto;

import com.hwanil.illegalcardetector.common.ViolationType;

import jakarta.validation.constraints.NotNull;

/**
 * POST /api/judgments/{id}/feedback 요청 본문
 * 예: { "correct": false, "actualLabel": "SIDEWALK" }
 *
 * @param correct     판별이 맞았는지 (필수)
 * @param actualLabel 실제 유형. 틀렸을 때도 비워 둘 수 있어요.
 *                    (프론트의 "잘 모르겠어요" 버튼이 null을 보내요) 맞았으면 무시해요.
 */
public record FeedbackRequest(
		@NotNull(message = "판별이 맞았는지(correct)가 필요해요.")
		Boolean correct,

		ViolationType actualLabel) {
}
