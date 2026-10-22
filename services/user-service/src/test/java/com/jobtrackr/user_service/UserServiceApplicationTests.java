package com.jobtrackr.user_service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Verifies the Spring application context wires up correctly (all beans, no missing
 * dependencies). Deliberately points to in-memory H2 and disables Eureka and Flyway
 * so the context test runs without requiring real infrastructure.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:userservicetest;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "eureka.client.enabled=false"
})
class UserServiceApplicationTests {

    @Test
    void contextLoads() {
    }

}
