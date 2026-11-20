package com.ganzithon.wool.global.config;

import com.ganzithon.wool.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 기본(로컬·테스트) 설정에서는 API 명세가 열려 있다. 개발자가 로컬에서 Swagger를 쓰는 것은 그대로 유지한다.
@AutoConfigureMockMvc
class SwaggerLocalEnabledTest extends IntegrationTestSupport {

    @Autowired private MockMvc mockMvc;

    @Test
    void 기본_설정에서는_API_명세와_Swagger_화면이_열린다() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }
}
