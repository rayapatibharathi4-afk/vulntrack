package com.bharathi.vulntrack;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "vulntrack.enrichment.enabled=false",
        "vulntrack.scan-consumer.enabled=false",
        "spring.kafka.admin.auto-create=false"
})
class VulntrackApplicationTests {

	@Test
	void contextLoads() {
	}

}
