package com.hwanil.illegalcardetector.common;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import com.hwanil.illegalcardetector.analyze.AiNotConfiguredException;
import com.hwanil.illegalcardetector.analyze.AiUnavailableException;
import com.hwanil.illegalcardetector.analyze.AnalyzeController.InvalidImageException;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;

/**
 * 컨트롤러에서 생긴 예외를 모두 같은 모양(ErrorResponse)의 JSON으로 바꿔 줘요.
 * - 400 VALIDATION_FAILED: 입력값 검증 실패 (어느 필드가 틀렸는지 fieldErrors에 담아요)
 * - 400 BAD_REQUEST: JSON 형식이 깨졌거나, 없는 유형 이름(enum) 등
 * - 404 NOT_FOUND / 405 METHOD_NOT_ALLOWED / 415 UNSUPPORTED_MEDIA_TYPE
 * - 502 AI_UNAVAILABLE: 외부 AI(NVIDIA) 분석 실패 / 503 AI_NOT_CONFIGURED: 서버에 API 키가 없음
 * - 500 INTERNAL_ERROR: 그 밖의 서버 오류 (자세한 내용은 로그에만 남기고 응답에는 숨겨요)
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final String VALIDATION_MESSAGE = "입력값이 올바르지 않아요.";
	private static final String BAD_FORMAT_MESSAGE = "형식이 올바르지 않아요.";

	/** @Valid 요청 본문(JSON) 검증 실패 */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> handleBodyValidation(MethodArgumentNotValidException ex) {
		List<ErrorResponse.FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
				.map(error -> new ErrorResponse.FieldError(error.getField(), error.getDefaultMessage()))
				.toList();
		return respond(400, "VALIDATION_FAILED", VALIDATION_MESSAGE, fieldErrors);
	}

	/** 주소 뒤 파라미터 검증 실패 (예: ?limit=500) */
	@ExceptionHandler(HandlerMethodValidationException.class)
	public ResponseEntity<ErrorResponse> handleParameterValidation(HandlerMethodValidationException ex) {
		List<ErrorResponse.FieldError> fieldErrors = ex.getParameterValidationResults().stream()
				.map(result -> new ErrorResponse.FieldError(
						result.getMethodParameter().getParameterName(), joinMessages(result)))
				.toList();
		return respond(400, "VALIDATION_FAILED", VALIDATION_MESSAGE, fieldErrors);
	}

	/** JSON이 깨졌거나, 값의 형식이 틀렸을 때 (예: label이 "crosswalk"처럼 없는 유형) */
	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ErrorResponse> handleNotReadable(HttpMessageNotReadableException ex) {
		List<ErrorResponse.FieldError> fieldErrors = List.of();
		if (ex.getCause() instanceof JacksonException jacksonException) {
			String field = jacksonException.getPath().stream()
					.map(reference -> reference.getPropertyName() != null
							? reference.getPropertyName()
							: "[" + reference.getIndex() + "]")
					.collect(Collectors.joining("."));
			if (!field.isEmpty()) {
				fieldErrors = List.of(new ErrorResponse.FieldError(field, BAD_FORMAT_MESSAGE));
			}
		}
		// 요청 본문은 로그에 남기지 않아요.
		log.warn("읽을 수 없는 요청 본문: {}", fieldErrors);
		return respond(400, "BAD_REQUEST", "요청 형식이 올바르지 않아요.", fieldErrors);
	}

	/** 주소의 값 형식이 틀렸을 때 (예: /api/guides/ABC, /api/judgments/abc/feedback) */
	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
		return respond(400, "BAD_REQUEST", "요청 형식이 올바르지 않아요.",
				List.of(new ErrorResponse.FieldError(ex.getName(), BAD_FORMAT_MESSAGE)));
	}

	/** 없는 데이터 (예: 없는 판별 기록 id) */
	@ExceptionHandler(NotFoundException.class)
	public ResponseEntity<ErrorResponse> handleNotFound(NotFoundException ex) {
		return respond(404, "NOT_FOUND", ex.getMessage(), List.of());
	}

	/** 사진 판별: 올린 파일이 규칙(jpeg/png/webp, 5MB 이하)에 맞지 않을 때 */
	@ExceptionHandler(InvalidImageException.class)
	public ResponseEntity<ErrorResponse> handleInvalidImage(InvalidImageException ex) {
		return respond(400, "BAD_REQUEST", ex.getMessage(),
				List.of(new ErrorResponse.FieldError("image", ex.getMessage())));
	}

	/** 사진 판별: 업로드 크기 제한(application.yaml)을 넘었을 때 */
	@ExceptionHandler(MaxUploadSizeExceededException.class)
	public ResponseEntity<ErrorResponse> handleTooLarge(MaxUploadSizeExceededException ex) {
		String message = "사진은 5MB 이하만 올릴 수 있어요.";
		return respond(400, "BAD_REQUEST", message, List.of(new ErrorResponse.FieldError("image", message)));
	}

	/** 사진 판별: 서버에 NVIDIA_API_KEY가 없을 때 */
	@ExceptionHandler(AiNotConfiguredException.class)
	public ResponseEntity<ErrorResponse> handleAiNotConfigured(AiNotConfiguredException ex) {
		log.warn("AI 분석 요청을 받았지만 NVIDIA_API_KEY가 없어요.");
		return respond(503, "AI_NOT_CONFIGURED",
				"지금은 AI 분석을 쓸 수 없어요. 서버에 AI 설정(API 키)이 필요해요.", List.of());
	}

	/** 사진 판별: NVIDIA 오류·시간 초과·이상한 응답 */
	@ExceptionHandler(AiUnavailableException.class)
	public ResponseEntity<ErrorResponse> handleAiUnavailable(AiUnavailableException ex) {
		log.warn("AI 분석 실패: {}", ex.getMessage());
		return respond(502, "AI_UNAVAILABLE", "AI 분석에 실패했어요. 다시 시도해 주세요.", List.of());
	}

	/** 그 밖의 모든 예외 */
	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> handleOthers(Exception ex) {
		// 스프링이 이미 상태 코드를 알고 있는 예외 (없는 주소 404, 허용되지 않은 메서드 405 등)
		if (ex instanceof org.springframework.web.ErrorResponse springError
				&& springError.getStatusCode().is4xxClientError()) {
			HttpStatusCode status = springError.getStatusCode();
			return switch (status.value()) {
				case 404 -> respond(404, "NOT_FOUND", "요청한 주소를 찾을 수 없어요.", List.of());
				case 405 -> respond(405, "METHOD_NOT_ALLOWED", "이 주소에서 지원하지 않는 요청 방식이에요.", List.of());
				case 415 -> respond(415, "UNSUPPORTED_MEDIA_TYPE", "JSON(application/json)으로 보내 주세요.", List.of());
				default -> respond(status.value(), "BAD_REQUEST", "요청이 올바르지 않아요.", List.of());
			};
		}

		// 진짜 서버 오류: 자세한 내용은 로그에만 남기고, 응답에는 숨겨요.
		log.error("처리하지 못한 서버 오류", ex);
		return respond(500, "INTERNAL_ERROR", "서버에 문제가 생겼어요. 잠시 후 다시 시도해 주세요.", List.of());
	}

	private static ResponseEntity<ErrorResponse> respond(int status, String code, String message,
			List<ErrorResponse.FieldError> fieldErrors) {
		return ResponseEntity.status(status).body(new ErrorResponse(status, code, message, fieldErrors));
	}

	private static String joinMessages(ParameterValidationResult result) {
		return result.getResolvableErrors().stream()
				.map(error -> error.getDefaultMessage())
				.collect(Collectors.joining(" "));
	}
}
