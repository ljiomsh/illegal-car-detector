package com.hwanil.illegalcardetector.guide;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

import com.hwanil.illegalcardetector.common.ViolationType;

import tools.jackson.databind.json.JsonMapper;

/**
 * guides.json 로딩 테스트
 */
class GuideServiceTest {

	private final JsonMapper jsonMapper = JsonMapper.builder().build();

	@Test
	void 실제_guides_json에_4개_유형이_모두_로딩된다() {
		GuideService service = new GuideService(jsonMapper);

		assertThat(service.getAllGuides())
				.extracting(Guide::type)
				.containsExactly(ViolationType.values());

		for (ViolationType type : ViolationType.values()) {
			Guide guide = service.getGuide(type);
			assertThat(guide.title()).isNotBlank();
			assertThat(guide.dangerReasons()).isNotEmpty();
			assertThat(guide.reportUrl()).isEqualTo("https://www.safetyreport.go.kr");
			assertThat(guide.violation()).isEqualTo(type != ViolationType.NORMAL);
			if (guide.violation()) {
				assertThat(guide.checklist()).isNotEmpty();
				assertThat(guide.reportSteps()).isNotEmpty();
			}
		}

		// 소화전은 "5m 이내" 항목이 더 있어요.
		assertThat(service.getGuide(ViolationType.HYDRANT).checklist())
				.extracting(Guide.ChecklistItem::id)
				.contains("plate", "scene", "interval", "hydrant-distance");
	}

	@Test
	void 유형이_하나라도_없으면_시작할_때_실패한다() {
		String json = """
				{
				  "reportUrl": "https://www.safetyreport.go.kr",
				  "guides": {
				    "CROSSWALK": { "title": "횡단보도", "violation": true, "dangerReasons": ["a"],
				                   "checklist": [{ "id": "plate", "text": "t" }], "reportSteps": ["s"] }
				  }
				}
				""";

		assertThatThrownBy(() -> new GuideService(jsonMapper, resource(json)))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("SIDEWALK 안내가 없어요");
	}

	@Test
	void 필수_필드가_비어_있으면_시작할_때_실패한다() {
		String json = """
				{
				  "reportUrl": "https://www.safetyreport.go.kr",
				  "guides": {
				    "CROSSWALK": { "title": "", "violation": true, "dangerReasons": ["a"],
				                   "checklist": [{ "id": "plate", "text": "t" }], "reportSteps": ["s"] },
				    "SIDEWALK": { "title": "인도", "violation": true, "dangerReasons": ["a"],
				                  "checklist": [], "reportSteps": ["s"] },
				    "HYDRANT": { "title": "소화전", "violation": true, "dangerReasons": ["a"],
				                 "checklist": [{ "id": "plate", "text": "t" }], "reportSteps": ["s"] },
				    "NORMAL": { "title": "정상", "violation": false, "dangerReasons": ["a"],
				                "checklist": [], "reportSteps": [] },
				    "HYDRNAT": { "title": "오타" }
				  }
				}
				""";

		assertThatThrownBy(() -> new GuideService(jsonMapper, resource(json)))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("CROSSWALK: title이 비어 있어요")
				.hasMessageContaining("SIDEWALK: checklist가 비어 있어요")
				.hasMessageContaining("알 수 없는 유형 이름이에요: HYDRNAT");
	}

	private static ByteArrayResource resource(String json) {
		return new ByteArrayResource(json.getBytes(StandardCharsets.UTF_8));
	}
}
