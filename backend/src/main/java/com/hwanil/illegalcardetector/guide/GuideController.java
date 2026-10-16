package com.hwanil.illegalcardetector.guide;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hwanil.illegalcardetector.common.ViolationType;

/**
 * 유형별 안내 조회 API
 * - GET /api/guides         : 4개 유형 전체
 * - GET /api/guides/{type}  : 유형 하나 (없는 유형 이름이면 400)
 */
@RestController
@RequestMapping("/api/guides")
public class GuideController {

	private final GuideService guideService;

	// 필요한 객체를 스프링이 생성자로 넣어 줘요. (생성자 주입)
	public GuideController(GuideService guideService) {
		this.guideService = guideService;
	}

	@GetMapping
	public List<Guide> getAll() {
		return guideService.getAllGuides();
	}

	@GetMapping("/{type}")
	public Guide getOne(@PathVariable ViolationType type) {
		return guideService.getGuide(type);
	}
}
