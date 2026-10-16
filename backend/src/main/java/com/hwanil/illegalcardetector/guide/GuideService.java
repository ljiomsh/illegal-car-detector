package com.hwanil.illegalcardetector.guide;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hwanil.illegalcardetector.common.ViolationType;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.json.JsonMapper;

/**
 * 서버가 시작할 때 src/main/resources/guides.json을 한 번 읽어서 메모리에 들고 있어요.
 * 4개 유형 중 하나라도 없거나 필수 내용이 비어 있으면 서버가 시작되지 않게 막아요.
 * (잘못된 안내가 사용자에게 나가는 것보다, 시작할 때 바로 알아차리는 게 안전해서요)
 */
@Slf4j
@Service
public class GuideService {

	private final Map<ViolationType, Guide> guides;

	// 생성자가 두 개라서, 스프링이 이 생성자를 쓰도록 @Autowired로 알려 줘요.
	@Autowired
	public GuideService(JsonMapper jsonMapper) {
		this(jsonMapper, new ClassPathResource("guides.json"));
	}

	// 테스트에서 다른 파일을 넣어 볼 수 있게 열어 둔 생성자
	GuideService(JsonMapper jsonMapper, Resource resource) {
		this.guides = load(jsonMapper, resource);
		log.info("안내 문구를 불러왔어요: {}", guides.keySet());
	}

	/** 유형 하나의 안내 */
	public Guide getGuide(ViolationType type) {
		return guides.get(type);
	}

	/** 4개 유형 안내 전체 (CROSSWALK, SIDEWALK, HYDRANT, NORMAL 순서) */
	public List<Guide> getAllGuides() {
		return List.copyOf(guides.values());
	}

	// ───────────── guides.json 읽기 + 검사 ─────────────

	/** guides.json 파일 전체 모양 */
	record GuideFile(
			@JsonProperty("_comment") List<String> comment,
			String reportUrl,
			Map<String, GuideData> guides) {
	}

	/** guides.json 안의 유형 하나 */
	record GuideData(
			String title,
			Boolean violation,
			List<String> dangerReasons,
			List<Guide.ChecklistItem> checklist,
			List<String> reportSteps) {
	}

	private static Map<ViolationType, Guide> load(JsonMapper jsonMapper, Resource resource) {
		GuideFile file;
		try (InputStream in = resource.getInputStream()) {
			file = jsonMapper.readValue(in, GuideFile.class);
		} catch (IOException | RuntimeException e) {
			throw new IllegalStateException("guides.json을 읽지 못했어요: " + e.getMessage(), e);
		}

		List<String> problems = new ArrayList<>();
		if (isBlank(file.reportUrl())) {
			problems.add("reportUrl이 비어 있어요.");
		}
		if (file.guides() == null) {
			throw new IllegalStateException("guides.json에 guides가 없어요.");
		}

		// 오타 방지: 4개 유형 이름이 아닌 키가 있으면 알려 줘요.
		for (String key : file.guides().keySet()) {
			if (!isViolationType(key)) {
				problems.add("알 수 없는 유형 이름이에요: " + key);
			}
		}

		Map<ViolationType, Guide> result = new EnumMap<>(ViolationType.class);
		for (ViolationType type : ViolationType.values()) {
			GuideData data = file.guides().get(type.name());
			if (data == null) {
				problems.add(type + " 안내가 없어요.");
				continue;
			}
			checkGuide(type, data, problems);
			result.put(type, new Guide(
					type,
					data.title(),
					Boolean.TRUE.equals(data.violation()),
					nullToEmpty(data.dangerReasons()),
					nullToEmpty(data.checklist()),
					nullToEmpty(data.reportSteps()),
					file.reportUrl()));
		}

		if (!problems.isEmpty()) {
			throw new IllegalStateException("guides.json에 문제가 있어요: " + String.join(" / ", problems));
		}
		return Collections.unmodifiableMap(result);
	}

	// 필수 내용 검사. NORMAL(위반 아님)은 체크리스트·신고 단계가 비어 있어도 돼요.
	private static void checkGuide(ViolationType type, GuideData data, List<String> problems) {
		if (isBlank(data.title())) {
			problems.add(type + ": title이 비어 있어요.");
		}
		if (data.violation() == null) {
			problems.add(type + ": violation(true/false)이 없어요.");
		}
		if (isEmptyOrHasBlank(data.dangerReasons())) {
			problems.add(type + ": dangerReasons가 비어 있어요.");
		}
		boolean violation = Boolean.TRUE.equals(data.violation());
		if (violation && (data.checklist() == null || data.checklist().isEmpty())) {
			problems.add(type + ": checklist가 비어 있어요.");
		}
		if (data.checklist() != null) {
			for (Guide.ChecklistItem item : data.checklist()) {
				if (item == null || isBlank(item.id()) || isBlank(item.text())) {
					problems.add(type + ": checklist에 id나 text가 빈 항목이 있어요.");
				}
			}
		}
		if (violation && isEmptyOrHasBlank(data.reportSteps())) {
			problems.add(type + ": reportSteps가 비어 있어요.");
		}
	}

	private static boolean isViolationType(String name) {
		for (ViolationType type : ViolationType.values()) {
			if (type.name().equals(name)) {
				return true;
			}
		}
		return false;
	}

	private static boolean isBlank(String text) {
		return text == null || text.isBlank();
	}

	private static boolean isEmptyOrHasBlank(List<String> texts) {
		return texts == null || texts.isEmpty() || texts.stream().anyMatch(GuideService::isBlank);
	}

	private static <T> List<T> nullToEmpty(List<T> list) {
		return list == null ? List.of() : List.copyOf(list);
	}
}
