package com.hwanil.illegalcardetector.judgment;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.hwanil.illegalcardetector.common.ConfidenceLevel;
import com.hwanil.illegalcardetector.common.NotFoundException;
import com.hwanil.illegalcardetector.common.ViolationType;
import com.hwanil.illegalcardetector.guide.Guide;
import com.hwanil.illegalcardetector.judgment.dto.FeedbackRequest;
import com.hwanil.illegalcardetector.judgment.dto.JudgmentRequest;
import com.hwanil.illegalcardetector.judgment.dto.JudgmentResponse;

/**
 * JudgmentController 테스트: 서비스는 가짜(Mock)로 두고, 요청 검증과 응답 모양만 확인해요.
 */
@WebMvcTest(JudgmentController.class)
class JudgmentControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private JudgmentService judgmentService;

	private static final String VALID_BODY = """
			{ "label": "CROSSWALK", "confidence": 0.92,
			  "probabilities": { "CROSSWALK": 0.92, "SIDEWALK": 0.05, "HYDRANT": 0.02, "NORMAL": 0.01 },
			  "modelVersion": "v1" }
			""";

	@Test
	void 판별_저장_성공하면_201과_안내를_돌려준다() throws Exception {
		given(judgmentService.create(any(JudgmentRequest.class))).willReturn(new JudgmentResponse(
				42L, ViolationType.CROSSWALK, "횡단보도 위 주정차", true, ConfidenceLevel.HIGH,
				List.of("위험해요"), List.of(new Guide.ChecklistItem("plate", "번호판")),
				List.of("앱을 열어요"), "https://www.safetyreport.go.kr"));

		postJudgment(VALID_BODY)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.judgmentId").value(42))
				.andExpect(jsonPath("$.type").value("CROSSWALK"))
				.andExpect(jsonPath("$.violation").value(true))
				.andExpect(jsonPath("$.confidenceLevel").value("HIGH"))
				.andExpect(jsonPath("$.checklist[0].id").value("plate"))
				.andExpect(jsonPath("$.reportUrl").value("https://www.safetyreport.go.kr"));
	}

	@Test
	void modelVersion은_없어도_된다() throws Exception {
		postJudgment("""
				{ "label": "NORMAL", "confidence": 0.5,
				  "probabilities": { "CROSSWALK": 0.2, "SIDEWALK": 0.2, "HYDRANT": 0.1, "NORMAL": 0.5 } }
				""")
				.andExpect(status().isCreated());
	}

	@Test
	void confidence가_1보다_크면_400() throws Exception {
		postJudgment(VALID_BODY.replace("\"confidence\": 0.92", "\"confidence\": 1.5"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.fieldErrors.length()").value(1))
				.andExpect(jsonPath("$.fieldErrors[0].field").value("confidence"))
				.andExpect(jsonPath("$.fieldErrors[0].message").value("0 이상 1 이하여야 해요."));
		verify(judgmentService, never()).create(any());
	}

	@Test
	void 확률_키가_빠지면_400() throws Exception {
		postJudgment("""
				{ "label": "CROSSWALK", "confidence": 0.95,
				  "probabilities": { "CROSSWALK": 0.95, "SIDEWALK": 0.05, "HYDRANT": 0.0 } }
				""")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.fieldErrors[0].field").value("probabilities"));
	}

	@Test
	void 확률_합계가_1이_아니면_400() throws Exception {
		postJudgment("""
				{ "label": "CROSSWALK", "confidence": 0.9,
				  "probabilities": { "CROSSWALK": 0.9, "SIDEWALK": 0.5, "HYDRANT": 0.1, "NORMAL": 0.1 } }
				""")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.fieldErrors[0].field").value("probabilities"))
				.andExpect(jsonPath("$.fieldErrors[0].message").value("4개 확률의 합이 1이어야 해요."));
	}

	@Test
	void label의_확률이_최댓값이_아니면_400() throws Exception {
		postJudgment("""
				{ "label": "HYDRANT", "confidence": 0.1,
				  "probabilities": { "CROSSWALK": 0.7, "SIDEWALK": 0.1, "HYDRANT": 0.1, "NORMAL": 0.1 } }
				""")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].field").value("probabilities"));
	}

	@Test
	void confidence가_label_확률과_다르면_400() throws Exception {
		postJudgment(VALID_BODY.replace("\"confidence\": 0.92", "\"confidence\": 0.8"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].field").value("confidence"));
	}

	@Test
	void 잘못된_label이면_400_BAD_REQUEST() throws Exception {
		postJudgment(VALID_BODY.replace("\"label\": \"CROSSWALK\"", "\"label\": \"crosswalk\""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("BAD_REQUEST"))
				.andExpect(jsonPath("$.fieldErrors[0].field").value("label"));
	}

	@Test
	void modelVersion에_허용되지_않는_글자가_있으면_400() throws Exception {
		postJudgment(VALID_BODY.replace("\"modelVersion\": \"v1\"", "\"modelVersion\": \"v1 <script>\""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].field").value("modelVersion"));
	}

	@Test
	void 깨진_JSON이면_400_BAD_REQUEST() throws Exception {
		postJudgment("{ \"label\": ")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("BAD_REQUEST"));
	}

	@Test
	void limit가_범위를_벗어나면_400() throws Exception {
		mockMvc.perform(get("/api/judgments").param("limit", "101"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.fieldErrors[0].field").value("limit"));
	}

	@Test
	void 없는_기록에_피드백하면_404() throws Exception {
		willThrow(new NotFoundException("해당 판별 기록을 찾지 못했어요."))
				.given(judgmentService).applyFeedback(anyLong(), any(FeedbackRequest.class));

		mockMvc.perform(post("/api/judgments/999/feedback")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{ \"correct\": false, \"actualLabel\": \"SIDEWALK\" }"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.status").value(404))
				.andExpect(jsonPath("$.code").value("NOT_FOUND"))
				.andExpect(jsonPath("$.message").value("해당 판별 기록을 찾지 못했어요."));
	}

	@Test
	void 피드백_성공하면_204() throws Exception {
		mockMvc.perform(post("/api/judgments/1/feedback")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{ \"correct\": false, \"actualLabel\": null }"))
				.andExpect(status().isNoContent());
	}

	@Test
	void 피드백에_correct가_없으면_400() throws Exception {
		mockMvc.perform(post("/api/judgments/1/feedback")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{ \"actualLabel\": \"SIDEWALK\" }"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].field").value("correct"));
	}

	@Test
	void 서버_오류는_내부_내용을_숨기고_500() throws Exception {
		given(judgmentService.create(any(JudgmentRequest.class)))
				.willThrow(new IllegalStateException("DB password=secret"));

		postJudgment(VALID_BODY)
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
				.andExpect(jsonPath("$.message").value("서버에 문제가 생겼어요. 잠시 후 다시 시도해 주세요."));
	}

	private ResultActions postJudgment(String body) throws Exception {
		return mockMvc.perform(post("/api/judgments")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body));
	}
}
