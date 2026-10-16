package com.hwanil.illegalcardetector.stats;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 통계 API
 * - GET /api/stats : 전체 개수, 유형별 개수, 피드백 수와 정확도
 */
@RestController
public class StatsController {

	private final StatsService statsService;

	// 필요한 객체를 스프링이 생성자로 넣어 줘요. (생성자 주입)
	public StatsController(StatsService statsService) {
		this.statsService = statsService;
	}

	@GetMapping("/api/stats")
	public StatsResponse getStats() {
		return statsService.getStats();
	}
}
