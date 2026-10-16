package com.hwanil.illegalcardetector.stats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import com.hwanil.illegalcardetector.common.ViolationType;
import com.hwanil.illegalcardetector.judgment.Judgment;
import com.hwanil.illegalcardetector.judgment.JudgmentRepository;

/**
 * 통계 집계 쿼리 테스트 (테스트용 메모리 DB에서 실제 쿼리를 실행해요)
 */
@DataJpaTest
@Import(StatsService.class)
class StatsQueryTest {

	@Autowired
	private JudgmentRepository judgmentRepository;

	@Autowired
	private StatsService statsService;

	@Test
	void 기록이_없으면_4개_유형이_모두_0이고_정확도는_null() {
		StatsResponse stats = statsService.getStats();

		assertThat(stats.total()).isZero();
		assertThat(stats.byType()).containsOnlyKeys(ViolationType.values());
		assertThat(stats.byType().values()).containsOnly(0L);
		assertThat(stats.feedbackCount()).isZero();
		assertThat(stats.feedbackAccuracy()).isNull();
	}

	@Test
	void 유형별_개수와_피드백_정확도를_계산한다() {
		Judgment crosswalk1 = save(ViolationType.CROSSWALK);
		Judgment crosswalk2 = save(ViolationType.CROSSWALK);
		Judgment normal = save(ViolationType.NORMAL);
		save(ViolationType.CROSSWALK);
		// SIDEWALK, HYDRANT는 0건

		crosswalk1.applyFeedback(true, null);
		crosswalk2.applyFeedback(false, ViolationType.SIDEWALK);
		normal.applyFeedback(true, null);
		judgmentRepository.flush();

		StatsResponse stats = statsService.getStats();

		assertThat(stats.total()).isEqualTo(4);
		assertThat(stats.byType()).containsExactlyInAnyOrderEntriesOf(Map.of(
				ViolationType.CROSSWALK, 3L,
				ViolationType.SIDEWALK, 0L,
				ViolationType.HYDRANT, 0L,
				ViolationType.NORMAL, 1L));
		assertThat(stats.feedbackCount()).isEqualTo(3);
		assertThat(stats.feedbackAccuracy()).isCloseTo(2.0 / 3, within(1e-9));
	}

	@Test
	void 피드백을_다시_보내면_덮어쓴다() {
		Judgment judgment = save(ViolationType.HYDRANT);
		judgment.applyFeedback(false, ViolationType.NORMAL);
		judgment.applyFeedback(true, ViolationType.NORMAL); // 맞았다면 실제 유형은 저장하지 않아요
		judgmentRepository.flush();

		Judgment saved = judgmentRepository.findById(judgment.getId()).orElseThrow();
		assertThat(saved.getFeedbackCorrect()).isTrue();
		assertThat(saved.getFeedbackLabel()).isNull();
		assertThat(statsService.getStats().feedbackCount()).isEqualTo(1);
	}

	private Judgment save(ViolationType label) {
		Map<ViolationType, Double> probabilities = Map.of(
				ViolationType.CROSSWALK, label == ViolationType.CROSSWALK ? 0.85 : 0.05,
				ViolationType.SIDEWALK, label == ViolationType.SIDEWALK ? 0.85 : 0.05,
				ViolationType.HYDRANT, label == ViolationType.HYDRANT ? 0.85 : 0.05,
				ViolationType.NORMAL, label == ViolationType.NORMAL ? 0.85 : 0.05);
		return judgmentRepository.save(Judgment.create(label, 0.85, probabilities, "test"));
	}
}
