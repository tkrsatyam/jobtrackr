package com.jobtrackr.user_service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Verifies the Spring application context wires up correctly (all beans, no missing
 * dependencies). Deliberately excludes autoconfiguration for pieces that need real
 * infrastructure not available in CI (Postgres, Redis, Eureka) - this is a wiring
 * check, not a full integration/smoke test. Real infra coverage is tracked separately
 * as a Testcontainers-based follow-up (see JD-26 "Related follow-ups").
 */
@SpringBootTest(properties = {
        // Real Postgres isn't available in CI, so point the DataSource at an
        // in-memory H2 database instead. Excluding JPA autoconfiguration outright
        // isn't an option here: AuthService and UserService both need a working
        // UserRepository, which needs a real DataSource to exist.
        "spring.datasource.url=jdbc:h2:mem:userservicetest;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        // Flyway's migration scripts target real Postgres syntax, so skip them
        // against H2 - ddl-auto above creates the schema instead for this test.
        "spring.flyway.enabled=false",
        // No Eureka server running in CI; this just stops it from trying to
        // register with one. Flyway and Eureka both expose a clean boolean
        // switch, unlike DataSource/JPA, so a property is enough here.
        "eureka.client.enabled=false"
})
class UserServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
