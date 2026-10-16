package com.hwanil.illegalcardetector.judgment.dto;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.hwanil.illegalcardetector.common.ViolationType;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * 판별 요청의 확률이 앞뒤가 맞는지 검사해요.
 * 1) probabilities에 CROSSWALK, SIDEWALK, HYDRANT, NORMAL 4개 키가 정확히 있는지
 * 2) 각 확률이 0 이상 1 이하인지
 * 3) 4개 확률의 합이 1인지 (반올림 오차를 생각해서 ±0.02까지 허용)
 * 4) label의 확률이 가장 큰지
 * 5) confidence가 label의 확률과 같은지 (±0.001까지 허용)
 * 문제가 있으면 어느 필드인지(probabilities / confidence) 알려 줘요.
 */
public class ProbabilitiesValidator implements ConstraintValidator<ValidProbabilities, JudgmentRequest> {

	static final double SUM_TOLERANCE = 0.02;
	static final double CONFIDENCE_TOLERANCE = 0.001;

	private static final Set<String> REQUIRED_KEYS = Arrays.stream(ViolationType.values())
			.map(Enum::name)
			.collect(Collectors.toUnmodifiableSet());

	@Override
	public boolean isValid(JudgmentRequest request, ConstraintValidatorContext context) {
		Map<String, Double> probabilities = request.probabilities();
		// 값이 아예 없거나 confidence가 0~1을 벗어나면 각 필드의 어노테이션이 알려 주니까 여기서는 넘어가요.
		// (같은 문제로 에러가 두 번 나오지 않게)
		if (probabilities == null || request.label() == null || request.confidence() == null
				|| request.confidence() < 0 || request.confidence() > 1) {
			return true;
		}

		if (!probabilities.keySet().equals(REQUIRED_KEYS)) {
			return fail(context, "probabilities",
					"CROSSWALK, SIDEWALK, HYDRANT, NORMAL 4개 키가 정확히 있어야 해요.");
		}

		double sum = 0;
		for (Double value : probabilities.values()) {
			if (value == null || value < 0 || value > 1) {
				return fail(context, "probabilities", "각 확률은 0 이상 1 이하여야 해요.");
			}
			sum += value;
		}

		if (Math.abs(sum - 1.0) > SUM_TOLERANCE) {
			return fail(context, "probabilities", "4개 확률의 합이 1이어야 해요.");
		}

		double labelProbability = probabilities.get(request.label().name());
		for (double value : probabilities.values()) {
			if (value > labelProbability) {
				return fail(context, "probabilities", "판별 유형(label)의 확률이 가장 커야 해요.");
			}
		}

		if (Math.abs(request.confidence() - labelProbability) > CONFIDENCE_TOLERANCE) {
			return fail(context, "confidence", "확신도는 판별 유형(label)의 확률과 같아야 해요.");
		}

		return true;
	}

	// 기본 메시지 대신, 문제가 있는 필드 이름과 메시지를 직접 넣어요.
	private static boolean fail(ConstraintValidatorContext context, String field, String message) {
		context.disableDefaultConstraintViolation();
		context.buildConstraintViolationWithTemplate(message)
				.addPropertyNode(field)
				.addConstraintViolation();
		return false;
	}
}
