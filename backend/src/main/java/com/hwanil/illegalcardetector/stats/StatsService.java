package com.hwanil.illegalcardetector.stats;

import java.util.EnumMap;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hwanil.illegalcardetector.common.ViolationType;
import com.hwanil.illegalcardetector.judgment.JudgmentRepository;
import com.hwanil.illegalcardetector.judgment.LabelCount;

/**
 * 판별 통계를 계산해요.
 * 기록을 전부 불러와서 세지 않고, DB의 집계 쿼리(GROUP BY, COUNT)로 숫자만 받아 와요.
 * 기록이 많아져도 빠르게 동작해요.
 */
@Service
public class StatsService {

	private final JudgmentRepository judgmentRepository;

	// 필요한 객체를 스프링이 생성자로 넣어 줘요. (생성자 주입)
	public StatsService(JudgmentRepository judgmentRepository) {
		this.judgmentRepository = judgmentRepository;
	}

	@Transactional(readOnly = true)
	public StatsResponse getStats() {
		// 0건인 유형도 응답에 나오도록 4개를 모두 0으로 채워 두고 시작해요.
		Map<ViolationType, Long> byType = new EnumMap<>(ViolationType.class);
		for (ViolationType type : ViolationType.values()) {
			byType.put(type, 0L);
		}

		long total = 0;
		for (LabelCount row : judgmentRepository.countByLabel()) {
			byType.put(row.label(), row.count());
			total += row.count();
		}

		long feedbackCount = judgmentRepository.countByFeedbackCorrectIsNotNull();
		Double feedbackAccuracy = feedbackCount == 0
				? null
				: (double) judgmentRepository.countByFeedbackCorrectTrue() / feedbackCount;

		return new StatsResponse(total, byType, feedbackCount, feedbackAccuracy);
	}
}
