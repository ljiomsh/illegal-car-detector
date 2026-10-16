package com.hwanil.illegalcardetector.judgment;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import com.hwanil.illegalcardetector.common.ViolationType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 판별 기록 한 건 (테이블: judgment).
 * 사진·위치·IP처럼 사람을 알아볼 수 있는 정보는 저장하지 않아요. AI가 낸 숫자와 피드백만 저장해요.
 * 확신도 등급(HIGH/MEDIUM/LOW)은 저장하지 않고 조회할 때 계산해요. (기준이 바뀌어도 과거 기록이 맞게 나오도록)
 */
@Entity
@Table(name = "judgment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // JPA가 쓰는 빈 생성자
public class Judgment {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ViolationType label;

	@Column(nullable = false)
	private double confidence;

	// 4가지 확률을 고정된 4개 칸에 저장해요. (H2·PostgreSQL 모두 같은 방식으로 동작)
	@Column(nullable = false)
	private double probCrosswalk;

	@Column(nullable = false)
	private double probSidewalk;

	@Column(nullable = false)
	private double probHydrant;

	@Column(nullable = false)
	private double probNormal;

	@Column(length = 20)
	private String modelVersion;

	// null이면 아직 피드백이 없다는 뜻이에요.
	private Boolean feedbackCorrect;

	@Enumerated(EnumType.STRING)
	@Column(length = 20)
	private ViolationType feedbackLabel;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	private Instant feedbackAt;

	/** 새 판별 기록을 만들어요. probabilities는 4개 유형의 확률이에요. */
	public static Judgment create(ViolationType label, double confidence,
			Map<ViolationType, Double> probabilities, String modelVersion) {
		Judgment judgment = new Judgment();
		judgment.label = label;
		judgment.confidence = confidence;
		judgment.probCrosswalk = probabilities.get(ViolationType.CROSSWALK);
		judgment.probSidewalk = probabilities.get(ViolationType.SIDEWALK);
		judgment.probHydrant = probabilities.get(ViolationType.HYDRANT);
		judgment.probNormal = probabilities.get(ViolationType.NORMAL);
		judgment.modelVersion = modelVersion;
		return judgment;
	}

	/**
	 * "판별이 맞았다/틀렸다" 피드백을 기록해요. 다시 보내면 덮어써요.
	 * 맞았다(correct=true)면 실제 유형은 판별 결과와 같으니 따로 저장하지 않아요(null).
	 */
	public void applyFeedback(boolean correct, ViolationType actualLabel) {
		this.feedbackCorrect = correct;
		this.feedbackLabel = correct ? null : actualLabel;
		this.feedbackAt = Instant.now();
	}

	/** 4개 확률을 { "CROSSWALK": 0.9, ... } 모양으로 돌려줘요. */
	public Map<String, Double> getProbabilities() {
		Map<String, Double> probabilities = new LinkedHashMap<>();
		probabilities.put(ViolationType.CROSSWALK.name(), probCrosswalk);
		probabilities.put(ViolationType.SIDEWALK.name(), probSidewalk);
		probabilities.put(ViolationType.HYDRANT.name(), probHydrant);
		probabilities.put(ViolationType.NORMAL.name(), probNormal);
		return probabilities;
	}

	// 처음 저장될 때 저장 시각을 자동으로 넣어요.
	@PrePersist
	void onCreate() {
		if (createdAt == null) {
			createdAt = Instant.now();
		}
	}
}
