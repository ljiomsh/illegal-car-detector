package com.hwanil.illegalcardetector.judgment.dto;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * JudgmentRequest의 확률이 앞뒤가 맞는지 검사하는 어노테이션.
 * 실제 검사 내용은 ProbabilitiesValidator에 있어요.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ProbabilitiesValidator.class)
public @interface ValidProbabilities {

	String message() default "확률 값이 올바르지 않아요.";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};
}
