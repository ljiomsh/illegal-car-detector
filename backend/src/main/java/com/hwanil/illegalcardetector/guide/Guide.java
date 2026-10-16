package com.hwanil.illegalcardetector.guide;

import java.util.List;

import com.hwanil.illegalcardetector.common.ViolationType;

/**
 * 유형 하나의 안내 데이터 (guides.json에서 읽어 와요).
 *
 * @param type          유형 (CROSSWALK 등)
 * @param title         제목 (예: "횡단보도 위 주정차")
 * @param violation     불법 주정차인지 (NORMAL만 false)
 * @param dangerReasons 위험한 이유
 * @param checklist     신고 요건 체크리스트
 * @param reportSteps   안전신문고 신고 단계
 * @param reportUrl     안전신문고 주소
 */
public record Guide(
		ViolationType type,
		String title,
		boolean violation,
		List<String> dangerReasons,
		List<ChecklistItem> checklist,
		List<String> reportSteps,
		String reportUrl) {

	/** 체크리스트 한 줄 (id는 프론트에서 체크 상태를 구분할 때 써요) */
	public record ChecklistItem(String id, String text) {
	}
}
