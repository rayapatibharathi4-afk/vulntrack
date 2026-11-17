package com.bharathi.vulntrack;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "vulntrack.enrichment.enabled=false")
class VulntrackApplicationTests {

	@Test
	void contextLoads() {
	}

}
