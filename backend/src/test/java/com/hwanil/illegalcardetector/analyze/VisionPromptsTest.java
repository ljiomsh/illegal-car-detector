package com.hwanil.illegalcardetector.analyze;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 판별 기준 글 테스트: 모델마다 다른 설명이 빈칸 없이 들어갔는지 확인해요.
 */
class VisionPromptsTest {

	@Test
	void 모델별_판별_기준에_빈칸이_남아_있지_않다() {
		for (String prompt : List.of(VisionPrompts.SYSTEM_FOR_CLAUDE, VisionPrompts.SYSTEM_FOR_NVIDIA)) {
			assertThat(prompt).doesNotContain("{vehicle}", "{crosswalk}", "{sidewalk}");
			// 글 앞의 들여쓰기(탭)가 지워졌는지
			assertThat(prompt).startsWith("너는 사진으로 불법 주정차를 판별하는 도우미야.").doesNotContain("\t");
			assertThat(prompt).contains("1. vehicle", "\"parked\"", "2. crosswalk", "\"near\"", "3. sidewalk", "4. hydrant",
					"5. noParking", "반드시 아래 형식의 JSON 하나만 출력");
		}
	}

	@Test
	void 모델마다_횡단보도_설명이_다르다() {
		assertThat(VisionPrompts.SYSTEM_FOR_NVIDIA).contains("차체가 줄무늬를 덮고 있어야 해.");
		assertThat(VisionPrompts.SYSTEM_FOR_CLAUDE).contains("차의 일부라도 횡단보도 줄무늬 위에 있음");
	}
}
