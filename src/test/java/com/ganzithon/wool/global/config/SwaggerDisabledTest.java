package com.ganzithon.wool.global.config;

import com.ganzithon.wool.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 운영 설정(application-prod.yml)과 같은 값으로 껐을 때 API 명세와 Swagger 화면이 실제로 막히고, 나머지 API는 그대로인지 확인한다.
@AutoConfigureMockMvc
@TestPropertySource(properties = {"springdoc.api-docs.enabled=false", "springdoc.swagger-ui.enabled=false"})
class SwaggerDisabledTest extends IntegrationTestSupport {

    @Autowired private MockMvc mockMvc;

    @Test
    void 끄면_API_명세와_Swagger_화면은_열리지_않고_다른_API는_정상이다() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isNotFound());
        mockMvc.perform(get("/swagger-ui.html")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/health")).andExpect(status().isOk());
    }
}
