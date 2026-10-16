package com.hwanil.illegalcardetector;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 앱 전체가 문제없이 켜지는지 확인해요. (guides.json 로딩 포함)
 * 개발용 DB 파일(./data)을 건드리지 않도록 테스트용 메모리 DB를 써요.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:contextloads;DB_CLOSE_DELAY=-1")
class IllegalcardetectorApplicationTests {

	@Test
	void contextLoads() {
	}

}
