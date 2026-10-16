package com.hwanil.illegalcardetector.common;

import java.util.List;

/**
 * 모든 에러(4xx, 5xx) 응답의 공통 모양.
 * 예: { "status": 400, "code": "VALIDATION_FAILED", "message": "입력값이 올바르지 않아요.",
 *       "fieldErrors": [{ "field": "confidence", "message": "0 이상 1 이하여야 해요." }] }
 */
public record ErrorResponse(int status, String code, String message, List<FieldError> fieldErrors) {

	/** 어느 입력값이 왜 잘못됐는지 */
	public record FieldError(String field, String message) {
	}
}
