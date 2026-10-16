package com.hwanil.illegalcardetector.judgment.dto;

import java.util.Map;

import com.hwanil.illegalcardetector.common.ViolationType;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * POST /api/judgments 요청 본문 (브라우저 안의 AI가 낸 판별 결과)
 * 예: { "label": "CROSSWALK", "confidence": 0.92,
 *       "probabilities": { "CROSSWALK": 0.92, "SIDEWALK": 0.05, "HYDRANT": 0.02, "NORMAL": 0.01 },
 *       "modelVersion": "v1" }
 *
 * 하나씩 보는 검사는 각 필드의 어노테이션이 하고,
 * 여러 필드를 함께 보는 검사(확률 합계, label이 최댓값인지 등)는 @ValidProbabilities가 해요.
 * label에 없는 유형 이름(예: "crosswalk")이 오면 JSON을 읽는 단계에서 400 BAD_REQUEST가 돼요.
 */
@ValidProbabilities
public record JudgmentRequest(
		@NotNull(message = "판별 유형(label)이 필요해요.")
		ViolationType label,

		@NotNull(message = "확신도(confidence)가 필요해요.")
		@DecimalMin(value = "0.0", message = "0 이상 1 이하여야 해요.")
		@DecimalMax(value = "1.0", message = "0 이상 1 이하여야 해요.")
		Double confidence,

		@NotNull(message = "4가지 유형의 확률(probabilities)이 필요해요.")
		Map<String, Double> probabilities,

		@Size(max = 20, message = "20자 이하여야 해요.")
		@Pattern(regexp = "^[A-Za-z0-9.-]*$", message = "영문, 숫자, 점(.), 빼기(-)만 쓸 수 있어요.")
		String modelVersion) {
}
