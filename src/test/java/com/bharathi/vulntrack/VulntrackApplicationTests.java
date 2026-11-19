package com.bharathi.vulntrack;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest(properties = {
        "vulntrack.enrichment.enabled=false",
        "vulntrack.scan-consumer.enabled=false",
        "vulntrack.outbox.enabled=false",
        "vulntrack.risk-expiry.enabled=false",
        "spring.kafka.admin.auto-create=false"
})
@Import(PostgresContainerConfig.class)
class VulntrackApplicationTests {

	@Test
	void contextLoads() {
	}

}
