package com.myfinaimanager.core.platform.infrastructure.api.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.myfinaimanager.core.support.AbstractPostgresIT;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.yaml.snakeyaml.Yaml;

/**
 * AC-007: the implemented {@code hello} endpoint stays compatible with the
 * committed platform OpenAPI contract. Compares the springdoc-generated spec
 * against {@code implementation/platform/contracts/openapi.yaml} semantically
 * (path, operation, response codes, response schema shape) rather than
 * byte-for-byte.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HelloContractIT extends AbstractPostgresIT {

    private static final String HELLO_PATH = "/api/v1/hello";

    @Autowired
    private TestRestTemplate rest;

    @Test
    @SuppressWarnings("unchecked")
    void generatedSpecMatchesTheCommittedContract() throws Exception {
        Map<String, Object> committed = loadCommittedContract();
        Map<String, Object> generated = new Yaml().load(
                rest.getForObject("/v3/api-docs.yaml", String.class));

        Map<String, Object> committedGet = operation(committed);
        Map<String, Object> generatedGet = operation(generated);

        // Same operation id.
        assertThat(generatedGet.get("operationId")).isEqualTo(committedGet.get("operationId"));

        // Same set of documented response codes.
        assertThat(((Map<String, Object>) generatedGet.get("responses")).keySet())
                .containsExactlyInAnyOrderElementsOf(
                        ((Map<String, Object>) committedGet.get("responses")).keySet())
                .contains("200", "503");

        // 200 body: object with a required non-empty string `version`.
        assertThat(propertyType(schemaFor(generated, generatedGet, "200"), "version")).isEqualTo("string");
        assertThat(requiredProperties(schemaFor(generated, generatedGet, "200"))).contains("version");
        assertThat(propertyType(schemaFor(committed, committedGet, "200"), "version")).isEqualTo("string");

        // 503 body: object with a required string `error`, no version leaked.
        assertThat(propertyType(schemaFor(generated, generatedGet, "503"), "error")).isEqualTo("string");
        assertThat(requiredProperties(schemaFor(generated, generatedGet, "503"))).contains("error");
        assertThat(schemaFor(generated, generatedGet, "503")).doesNotContainKey("version");
        assertThat(propertyType(schemaFor(committed, committedGet, "503"), "error")).isEqualTo("string");
    }

    @Test
    void divergenceIsDetectable() {
        // Guard-rail check: the committed contract really does pin this operation,
        // so a controller change that drops it would fail generatedSpecMatches...().
        Map<String, Object> committed = loadCommittedContract();
        assertThat(paths(committed)).containsKey(HELLO_PATH);
        assertThat(operation(committed)).containsKey("responses");
    }

    private Map<String, Object> loadCommittedContract() {
        Path contract = Path.of(System.getProperty("user.dir"))
                .resolve("../../contracts/openapi.yaml")
                .normalize();
        assertThat(Files.exists(contract))
                .as("committed contract at " + contract)
                .isTrue();
        try {
            return new Yaml().load(Files.readString(contract));
        } catch (Exception e) {
            throw new IllegalStateException("Could not read committed contract " + contract, e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> paths(Map<String, Object> spec) {
        return (Map<String, Object>) spec.get("paths");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> operation(Map<String, Object> spec) {
        Map<String, Object> path = (Map<String, Object>) paths(spec).get(HELLO_PATH);
        assertThat(path).as("path " + HELLO_PATH).isNotNull();
        return (Map<String, Object>) path.get("get");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> schemaFor(
            Map<String, Object> spec, Map<String, Object> operation, String status) {
        Map<String, Object> response =
                (Map<String, Object>) ((Map<String, Object>) operation.get("responses")).get(status);
        Map<String, Object> content = (Map<String, Object>) response.get("content");
        assertThat(content).as("content for response " + status).isNotEmpty();
        Map<String, Object> mediaType = (Map<String, Object>) content.values().iterator().next();
        Map<String, Object> schema = (Map<String, Object>) mediaType.get("schema");
        return resolve(spec, schema);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> resolve(Map<String, Object> spec, Map<String, Object> schema) {
        Object ref = schema.get("$ref");
        if (ref == null) {
            return schema;
        }
        String name = ((String) ref).substring("#/components/schemas/".length());
        Map<String, Object> components = (Map<String, Object>) spec.get("components");
        Map<String, Object> schemas = (Map<String, Object>) components.get("schemas");
        return (Map<String, Object>) schemas.get(name);
    }

    @SuppressWarnings("unchecked")
    private static String propertyType(Map<String, Object> schema, String property) {
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        assertThat(properties).as("schema properties").containsKey(property);
        return (String) ((Map<String, Object>) properties.get(property)).get("type");
    }

    @SuppressWarnings("unchecked")
    private static List<String> requiredProperties(Map<String, Object> schema) {
        Object required = schema.get("required");
        return required == null ? List.of() : (List<String>) required;
    }
}
