package com.hwanil.illegalcardetector.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * React 화면 주소(/check, /history, /about)에서 새로고침해도 화면이 나오게 해요.
 * 이 주소들은 서버에 실제 파일이 없어서, React의 index.html을 대신 보내 줘요.
 * (index.html이 열리면 React Router가 주소를 보고 알맞은 화면을 그려요)
 *
 * /api/** 주소는 여기서 건드리지 않아요. 새 화면을 만들면 아래 목록에 주소를 추가해 주세요.
 */
@Controller
public class SpaForwardController {

	@GetMapping({ "/check", "/history", "/about" })
	public String forwardToIndex() {
		return "forward:/index.html";
	}
}
