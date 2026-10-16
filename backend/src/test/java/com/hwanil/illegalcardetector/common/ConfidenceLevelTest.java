package com.hwanil.illegalcardetector.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * ConfidenceLevel.from()의 경계값 테스트 (0.4, 0.7이 정확히 어느 등급인지)
 */
class ConfidenceLevelTest {

	@ParameterizedTest(name = "{0} → {1}")
	@CsvSource({
			"1.0, HIGH",
			"0.7, HIGH",       // 0.7 이상은 HIGH
			"0.6999, MEDIUM",
			"0.4, MEDIUM",     // 0.4 이상은 MEDIUM
			"0.3999, LOW",
			"0.0, LOW",
	})
	void from(double confidence, ConfidenceLevel expected) {
		assertThat(ConfidenceLevel.from(confidence)).isEqualTo(expected);
	}
}
