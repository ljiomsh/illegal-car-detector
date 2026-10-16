package com.hwanil.illegalcardetector.analyze;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.hwanil.illegalcardetector.common.ViolationType;

/**
 * AnalyzeController 테스트: AI 서비스는 가짜(Mock)로 두고, 파일 검사와 응답 코드만 확인해요.
 */
@WebMvcTest(AnalyzeController.class)
class AnalyzeControllerTest {

	// JPEG 파일의 앞부분 (FF D8 FF)
	private static final byte[] JPEG = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F' };

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private AnalyzeService analyzeService;

	@Test
	void 정상_사진이면_200과_판별_결과() throws Exception {
		given(analyzeService.analyze(any(), eq("image/jpeg"))).willReturn(new AnalyzeResponse(
				ViolationType.CROSSWALK, 0.86,
				Map.of(ViolationType.CROSSWALK, 0.86, ViolationType.SIDEWALK, 0.08,
						ViolationType.HYDRANT, 0.02, ViolationType.NORMAL, 0.04),
				"흰 줄 위에 있어요.", "deepseek-v4.1-flash"));

		mockMvc.perform(multipart("/api/analyze").file(new MockMultipartFile("image", "a.jpg", "image/jpeg", JPEG)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.label").value("CROSSWALK"))
				.andExpect(jsonPath("$.probabilities.SIDEWALK").value(0.08))
				.andExpect(jsonPath("$.reason").value("흰 줄 위에 있어요."))
				.andExpect(jsonPath("$.modelVersion").value("deepseek-v4.1-flash"));
	}

	@Test
	void 사진이_아니면_400() throws Exception {
		mockMvc.perform(multipart("/api/analyze")
				.file(new MockMultipartFile("image", "a.jpg", "image/jpeg", "not an image".getBytes())))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].field").value("image"));
		verify(analyzeService, never()).analyze(any(), any());
	}

	@Test
	void 키가_없으면_503() throws Exception {
		given(analyzeService.analyze(any(), any())).willThrow(new AiNotConfiguredException());

		mockMvc.perform(multipart("/api/analyze").file(new MockMultipartFile("image", "a.jpg", "image/jpeg", JPEG)))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.code").value("AI_NOT_CONFIGURED"));
	}

	@Test
	void AI_분석이_실패하면_502() throws Exception {
		given(analyzeService.analyze(any(), any())).willThrow(new AiUnavailableException("timeout"));

		mockMvc.perform(multipart("/api/analyze").file(new MockMultipartFile("image", "a.jpg", "image/jpeg", JPEG)))
				.andExpect(status().isBadGateway())
				.andExpect(jsonPath("$.code").value("AI_UNAVAILABLE"))
				.andExpect(jsonPath("$.message").value("AI 분석에 실패했어요. 다시 시도해 주세요."));
	}
}
