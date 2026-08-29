package com.coolxer;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
		"vectum.auth.token=test-token",
		"zenvis.business-service.enabled=false"
})
class ApplicationTests {

	@Test
	void contextLoads() {
	}

}
