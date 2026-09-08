package com.myfinaimanager.core.platform.infrastructure.api.rest;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.myfinaimanager.core.platform.business.GetPlatformVersion;
import com.myfinaimanager.core.platform.domain.exceptions.PlatformVersionUnavailableException;
import com.myfinaimanager.core.platform.domain.model.PlatformVersion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(HelloController.class)
class HelloControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GetPlatformVersion getPlatformVersion;

    @Test
    void returnsTheVersionWithHttp200() throws Exception {
        given(getPlatformVersion.execute()).willReturn(PlatformVersion.of("0.1.0"));

        mockMvc.perform(get("/api/v1/hello"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"version\":\"0.1.0\"}", true));
    }

    @Test
    void returnsHttp503WithAStableErrorCodeWhenVersionUnavailable() throws Exception {
        doThrow(new PlatformVersionUnavailableException("db down: relation \"platform_version\" does not exist"))
                .when(getPlatformVersion).execute();

        mockMvc.perform(get("/api/v1/hello"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("PLATFORM_VERSION_UNAVAILABLE"))
                .andExpect(jsonPath("$.version").doesNotExist())
                .andExpect(jsonPath("$.message").doesNotExist())
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("does not exist"))));
    }
}
