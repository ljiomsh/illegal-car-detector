package com.hwanil.illegalcardetector.judgment;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.hwanil.illegalcardetector.judgment.dto.FeedbackRequest;
import com.hwanil.illegalcardetector.judgment.dto.JudgmentRequest;
import com.hwanil.illegalcardetector.judgment.dto.JudgmentResponse;
import com.hwanil.illegalcardetector.judgment.dto.JudgmentSummary;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 판별 기록 API
 * - POST /api/judgments                : 판별 결과 저장 + 안내 응답 (201)
 * - GET  /api/judgments?limit=20       : 최근 기록, 최신순 (limit 1~100)
 * - POST /api/judgments/{id}/feedback  : 피드백 저장 (204)
 */
@RestController
@RequestMapping("/api/judgments")
public class JudgmentController {

	private final JudgmentService judgmentService;

	// 필요한 객체를 스프링이 생성자로 넣어 줘요. (생성자 주입)
	public JudgmentController(JudgmentService judgmentService) {
		this.judgmentService = judgmentService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public JudgmentResponse create(@Valid @RequestBody JudgmentRequest request) {
		return judgmentService.create(request);
	}

	@GetMapping
	public List<JudgmentSummary> findRecent(
			@RequestParam(defaultValue = "20")
			@Min(value = 1, message = "1 이상 100 이하여야 해요.")
			@Max(value = 100, message = "1 이상 100 이하여야 해요.")
			int limit) {
		return judgmentService.findRecent(limit);
	}

	@PostMapping("/{id}/feedback")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void feedback(@PathVariable long id, @Valid @RequestBody FeedbackRequest request) {
		judgmentService.applyFeedback(id, request);
	}
}
