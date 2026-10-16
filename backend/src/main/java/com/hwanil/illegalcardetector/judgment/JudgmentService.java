package com.hwanil.illegalcardetector.judgment;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hwanil.illegalcardetector.common.NotFoundException;
import com.hwanil.illegalcardetector.common.ViolationType;
import com.hwanil.illegalcardetector.guide.GuideService;
import com.hwanil.illegalcardetector.judgment.dto.FeedbackRequest;
import com.hwanil.illegalcardetector.judgment.dto.JudgmentRequest;
import com.hwanil.illegalcardetector.judgment.dto.JudgmentResponse;
import com.hwanil.illegalcardetector.judgment.dto.JudgmentSummary;

import lombok.extern.slf4j.Slf4j;

/**
 * 판별 기록 저장·조회·피드백을 처리해요.
 * 입력값 검사는 컨트롤러에 들어오기 전에 끝나 있어요. (JudgmentRequest의 어노테이션)
 */
@Slf4j
@Service
public class JudgmentService {

	private final JudgmentRepository judgmentRepository;
	private final GuideService guideService;

	// 필요한 객체를 스프링이 생성자로 넣어 줘요. (생성자 주입)
	public JudgmentService(JudgmentRepository judgmentRepository, GuideService guideService) {
		this.judgmentRepository = judgmentRepository;
		this.guideService = guideService;
	}

	/** 판별 결과를 저장하고, 그 유형의 안내를 함께 돌려줘요. LOW 확신도도 모델 개선 자료로 저장해요. */
	@Transactional
	public JudgmentResponse create(JudgmentRequest request) {
		Map<ViolationType, Double> probabilities = new EnumMap<>(ViolationType.class);
		for (ViolationType type : ViolationType.values()) {
			probabilities.put(type, request.probabilities().get(type.name()));
		}

		Judgment judgment = Judgment.create(
				request.label(), request.confidence(), probabilities, request.modelVersion());
		judgmentRepository.save(judgment);

		// 요청 본문 전체는 로그에 남기지 않아요.
		log.info("판별 기록 저장: id={}, label={}", judgment.getId(), judgment.getLabel());
		return JudgmentResponse.of(judgment, guideService.getGuide(judgment.getLabel()));
	}

	/** 최근 기록 limit개 (최신순) */
	@Transactional(readOnly = true)
	public List<JudgmentSummary> findRecent(int limit) {
		return judgmentRepository.findAllByOrderByCreatedAtDescIdDesc(Limit.of(limit)).stream()
				.map(JudgmentSummary::from)
				.toList();
	}

	/** 피드백 저장. 이미 있으면 덮어써요. 없는 기록이면 404 */
	@Transactional
	public void applyFeedback(long judgmentId, FeedbackRequest request) {
		Judgment judgment = judgmentRepository.findById(judgmentId)
				.orElseThrow(() -> new NotFoundException("해당 판별 기록을 찾지 못했어요."));
		// 엔티티 값을 바꾸면 트랜잭션이 끝날 때 JPA가 알아서 DB에 반영해요.
		judgment.applyFeedback(request.correct(), request.actualLabel());
		log.info("피드백 저장: id={}, correct={}", judgmentId, request.correct());
	}
}
