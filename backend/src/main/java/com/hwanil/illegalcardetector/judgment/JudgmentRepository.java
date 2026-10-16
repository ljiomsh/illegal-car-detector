package com.hwanil.illegalcardetector.judgment;

import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * 판별 기록을 DB에서 읽고 쓰는 곳.
 * 메서드 이름만 보고 Spring Data JPA가 SQL을 만들어 줘요. (@Query는 직접 쓴 쿼리)
 */
public interface JudgmentRepository extends JpaRepository<Judgment, Long> {

	/** 최근 기록을 최신순으로 limit개 (같은 시각이면 id가 큰 것이 먼저) */
	List<Judgment> findAllByOrderByCreatedAtDescIdDesc(Limit limit);

	/** 유형별 개수: DB에서 GROUP BY로 바로 세요. (기록 0건인 유형은 결과에 안 나와요) */
	@Query("select new com.hwanil.illegalcardetector.judgment.LabelCount(j.label, count(j)) "
			+ "from Judgment j group by j.label")
	List<LabelCount> countByLabel();

	/** 피드백이 있는 기록 수 */
	long countByFeedbackCorrectIsNotNull();

	/** "판별이 맞았다"는 피드백 수 */
	long countByFeedbackCorrectTrue();
}
