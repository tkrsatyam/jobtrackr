package com.jobtrackr.user_service.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:userservicetest;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "eureka.client.enabled=false",
        "spring.security.oauth2.client.registration.google.client-id=mock-client-id",
        "spring.security.oauth2.client.registration.google.client-secret=mock-client-secret"
})
class OAuth2AuthorizationEndpointTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    @DisplayName("GET /api/auth/oauth2/authorize/google should redirect to Google with offline access, prompt=select_account consent, and set auth cookie")
    void shouldRedirectToGoogleWithRequiredParams() throws Exception {
        mockMvc.perform(get("/api/auth/oauth2/authorize/google"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("accounts.google.com/o/oauth2/v2/auth")))
                .andExpect(header().string("Location", containsString("access_type=offline")))
                .andExpect(header().string("Location", containsString("prompt=select_account")))
                .andExpect(header().string("Location", containsString("consent")))
                .andExpect(header().string("Location", containsString("client_id=mock-client-id")))
                .andExpect(header().string("Location", containsString("gmail.readonly")))
                .andExpect(cookie().exists(HttpCookieOAuth2AuthorizationRequestRepository.OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME));
    }
}
