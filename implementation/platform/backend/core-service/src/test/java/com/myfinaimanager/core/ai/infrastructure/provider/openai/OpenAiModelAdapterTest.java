package com.myfinaimanager.core.ai.infrastructure.provider.openai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myfinaimanager.core.ai.domain.exceptions.AiInvalidResponseException;
import com.myfinaimanager.core.ai.domain.exceptions.AiProviderAuthenticationFailedException;
import com.myfinaimanager.core.ai.domain.exceptions.AiProviderNotConfiguredException;
import com.myfinaimanager.core.ai.domain.exceptions.AiProviderRateLimitedException;
import com.myfinaimanager.core.ai.domain.exceptions.AiProviderUnavailableException;
import com.myfinaimanager.core.ai.domain.model.AiRequest;
import com.myfinaimanager.core.ai.domain.model.AiResponse;
import com.myfinaimanager.core.ai.domain.model.OutputSchema;
import com.myfinaimanager.core.ai.domain.model.OutputSchema.FieldSpec;
import com.myfinaimanager.core.ai.domain.model.OutputSchema.FieldType;
import com.myfinaimanager.core.ai.domain.model.PromptReference;
import com.myfinaimanager.core.ai.infrastructure.provider.openai.client.OpenAiRestClient;
import com.myfinaimanager.core.ai.infrastructure.provider.openai.config.OpenAiProperties;
import com.myfinaimanager.core.ai.infrastructure.provider.openai.mapper.OpenAiChatMapper;

/**
 * {@code OpenAiModelAdapter} against a stubbed HTTP boundary — request shape (auth header, model,
 * {@code response_format}), response mapping (incl. usage/cost, structured content), every failure
 * status, and the blank-key no-call path (contract {@code openai-provider-contract.md}).
 */
class OpenAiModelAdapterTest {

    private static final String BASE = "https://api.openai.test/v1";
    private static final String KEY = "test-key-abc123";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-05T10:00:00Z"), ZoneOffset.UTC);

    private record Fixture(OpenAiModelAdapter adapter, MockRestServiceServer server) {
    }

    private static OpenAiProperties props(String apiKey) {
        return new OpenAiProperties(
                apiKey, URI.create(BASE), "gpt-4o-mini", Duration.ofSeconds(2), Duration.ofSeconds(5),
                new OpenAiProperties.Pricing(new BigDecimal("0.00015"), new BigDecimal("0.0006")));
    }

    private static Fixture fixture(String apiKey) {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenAiProperties properties = props(apiKey);
        OpenAiRestClient client = new OpenAiRestClient(builder.build(), properties);
        OpenAiChatMapper mapper = new OpenAiChatMapper(new ObjectMapper(), properties);
        return new Fixture(new OpenAiModelAdapter(client, mapper, CLOCK), server);
    }

    private static AiRequest request(Optional<OutputSchema> outputSchema) {
        PromptReference systemPrompt = new PromptReference("portfolio-analysis", "v1", "Be careful.");
        return new AiRequest(
                "portfolio-analysis", systemPrompt, "Assess this portfolio.", "Positions: AAPL 100%.",
                outputSchema, 300, Optional.empty(), java.util.Map.of(), "corr-1");
    }

    @Test
    void request_shape_carries_the_bearer_header_model_and_json_object_response_format() {
        Fixture f = fixture(KEY);
        f.server().expect(requestTo(BASE + "/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer " + KEY))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().string(Matchers.containsString("\"model\":\"gpt-4o-mini\"")))
                .andExpect(content().string(Matchers.containsString("\"response_format\":{\"type\":\"json_object\"}")))
                .andExpect(content().string(Matchers.containsString("\"role\":\"system\"")))
                .andExpect(content().string(Matchers.containsString("Be careful.")))
                .andExpect(content().string(Matchers.containsString("Assess this portfolio.")))
                .andExpect(content().string(Matchers.containsString("Positions: AAPL 100%.")))
                .andRespond(withSuccess(
                        "{\"id\":\"chatcmpl-1\",\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"All good.\"},\"finish_reason\":\"stop\"}],"
                                + "\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":5,\"total_tokens\":15}}",
                        MediaType.APPLICATION_JSON));

        AiResponse response = f.adapter().generate(request(Optional.empty()));

        assertThat(response.content()).isEqualTo("All good.");
        assertThat(response.provider()).isEqualTo(OpenAiModelAdapter.PROVIDER);
        assertThat(response.model()).isEqualTo("gpt-4o-mini");
        assertThat(response.requestId()).isEqualTo("chatcmpl-1");
        assertThat(response.finishReason()).contains("stop");
        assertThat(response.usage().inputTokens()).isEqualTo(10);
        assertThat(response.usage().outputTokens()).isEqualTo(5);
        assertThat(response.usage().totalTokens()).isEqualTo(15);
        // 10/1000*0.00015 = 0.0000015 -> rounds to 0.000002 at scale 6; 5/1000*0.0006 = 0.000003 (exact); sum 0.000005
        assertThat(response.usage().estimatedCost()).isEqualByComparingTo("0.000005");
        assertThat(response.structuredContent()).isEmpty();
        f.server().verify();
    }

    @Test
    void a_conforming_json_content_is_parsed_into_structured_content_when_a_schema_was_requested() {
        Fixture f = fixture(KEY);
        OutputSchema schema = new OutputSchema(java.util.List.of(new FieldSpec("summary", FieldType.STRING, true)));
        f.server().expect(requestTo(BASE + "/chat/completions"))
                .andRespond(withSuccess(
                        "{\"id\":\"chatcmpl-2\",\"choices\":[{\"message\":{\"content\":\"{\\\"summary\\\":\\\"ok\\\"}\"},\"finish_reason\":\"stop\"}],"
                                + "\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":1,\"total_tokens\":2}}",
                        MediaType.APPLICATION_JSON));

        AiResponse response = f.adapter().generate(request(Optional.of(schema)));

        assertThat(response.structuredContent()).isPresent();
        assertThat(response.structuredContent().get()).containsEntry("summary", "ok");
    }

    @Test
    void non_json_content_with_a_requested_schema_raises_invalid_response() {
        Fixture f = fixture(KEY);
        OutputSchema schema = new OutputSchema(java.util.List.of(new FieldSpec("summary", FieldType.STRING, true)));
        f.server().expect(requestTo(BASE + "/chat/completions"))
                .andRespond(withSuccess(
                        "{\"id\":\"chatcmpl-3\",\"choices\":[{\"message\":{\"content\":\"not json\"},\"finish_reason\":\"stop\"}],"
                                + "\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":1,\"total_tokens\":2}}",
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> f.adapter().generate(request(Optional.of(schema))))
                .isInstanceOf(AiInvalidResponseException.class);
    }

    @Test
    void http_401_maps_to_authentication_failed_without_leaking_the_key() {
        Fixture f = fixture(KEY);
        f.server().expect(requestTo(BASE + "/chat/completions"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("{\"error\":\"invalid api key\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        Throwable t = org.assertj.core.api.Assertions.catchThrowable(() -> f.adapter().generate(request(Optional.empty())));

        assertThat(t).isInstanceOf(AiProviderAuthenticationFailedException.class);
        assertThat(t.getMessage()).doesNotContain(KEY);
    }

    @Test
    void http_403_maps_to_authentication_failed() {
        Fixture f = fixture(KEY);
        f.server().expect(requestTo(BASE + "/chat/completions"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> f.adapter().generate(request(Optional.empty())))
                .isInstanceOf(AiProviderAuthenticationFailedException.class);
    }

    @Test
    void http_429_maps_to_rate_limited() {
        Fixture f = fixture(KEY);
        f.server().expect(requestTo(BASE + "/chat/completions"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> f.adapter().generate(request(Optional.empty())))
                .isInstanceOf(AiProviderRateLimitedException.class);
    }

    @Test
    void http_500_maps_to_provider_unavailable() {
        Fixture f = fixture(KEY);
        f.server().expect(requestTo(BASE + "/chat/completions"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> f.adapter().generate(request(Optional.empty())))
                .isInstanceOf(AiProviderUnavailableException.class);
    }

    @Test
    void a_network_error_maps_to_provider_unavailable() {
        Fixture f = fixture(KEY);
        f.server().expect(requestTo(BASE + "/chat/completions"))
                .andRespond(req -> {
                    throw new IOException("connection reset");
                });

        assertThatThrownBy(() -> f.adapter().generate(request(Optional.empty())))
                .isInstanceOf(AiProviderUnavailableException.class);
    }

    @Test
    void an_empty_choices_array_maps_to_invalid_response() {
        Fixture f = fixture(KEY);
        f.server().expect(requestTo(BASE + "/chat/completions"))
                .andRespond(withSuccess("{\"id\":\"c\",\"choices\":[],\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":0,\"total_tokens\":1}}",
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> f.adapter().generate(request(Optional.empty())))
                .isInstanceOf(AiInvalidResponseException.class);
    }

    @Test
    void a_missing_choices_field_maps_to_invalid_response() {
        Fixture f = fixture(KEY);
        f.server().expect(requestTo(BASE + "/chat/completions"))
                .andRespond(withSuccess("{\"id\":\"c\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> f.adapter().generate(request(Optional.empty())))
                .isInstanceOf(AiInvalidResponseException.class);
    }

    @Test
    void an_empty_200_body_maps_to_invalid_response() {
        Fixture f = fixture(KEY);
        f.server().expect(requestTo(BASE + "/chat/completions"))
                .andRespond(withStatus(HttpStatus.OK)); // no body

        assertThatThrownBy(() -> f.adapter().generate(request(Optional.empty())))
                .isInstanceOf(AiInvalidResponseException.class);
    }

    @Test
    void a_plain_4xx_other_than_401_403_429_maps_to_provider_unavailable() {
        Fixture f = fixture(KEY);
        f.server().expect(requestTo(BASE + "/chat/completions"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> f.adapter().generate(request(Optional.empty())))
                .isInstanceOf(AiProviderUnavailableException.class);
    }

    @Test
    void a_malformed_top_level_response_body_maps_to_provider_unavailable() {
        Fixture f = fixture(KEY);
        f.server().expect(requestTo(BASE + "/chat/completions"))
                .andRespond(withSuccess("<<<not json>>>", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> f.adapter().generate(request(Optional.empty())))
                .isInstanceOf(AiProviderUnavailableException.class);
    }

    @Test
    void a_literally_null_api_key_short_circuits_with_not_configured_and_makes_no_call() {
        Fixture f = fixture(null);

        assertThatThrownBy(() -> f.adapter().generate(request(Optional.empty())))
                .isInstanceOf(AiProviderNotConfiguredException.class);
        f.server().verify();
    }

    @Test
    void a_blank_api_key_short_circuits_with_not_configured_and_makes_no_call() {
        Fixture f = fixture("   ");
        // no server.expect(...) — any request would fail verification

        assertThatThrownBy(() -> f.adapter().generate(request(Optional.empty())))
                .isInstanceOf(AiProviderNotConfiguredException.class);
        f.server().verify();
    }

    @Test
    void the_matches_startsWith_query_string_is_never_used_for_the_key() {
        Fixture f = fixture(KEY);
        f.server().expect(requestTo(Matchers.not(Matchers.containsString(KEY))))
                .andRespond(withSuccess(
                        "{\"id\":\"c\",\"choices\":[{\"message\":{\"content\":\"ok\"},\"finish_reason\":\"stop\"}],"
                                + "\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":1,\"total_tokens\":2}}",
                        MediaType.APPLICATION_JSON));

        f.adapter().generate(request(Optional.empty()));

        f.server().verify();
    }
}
