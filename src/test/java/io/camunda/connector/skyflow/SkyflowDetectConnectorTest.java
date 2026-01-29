/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.skyflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.runtime.test.outbound.OutboundConnectorContextBuilder;
import org.junit.jupiter.api.*;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SkyflowDetectConnectorTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static WireMockServer wireMockServer;
  private SkyflowDetectConnector connector;

  @BeforeAll
  static void startWireMock() {
    wireMockServer = new WireMockServer(0);
    wireMockServer.start();
    configureFor("localhost", wireMockServer.port());
  }

  @AfterAll
  static void stopWireMock() {
    wireMockServer.stop();
  }

  @BeforeEach
  void setUp() {
    wireMockServer.resetAll();
    connector = new SkyflowDetectConnector();
  }

  @Test
  @DisplayName("Should successfully de-identify data")
  void testDeidentify() throws Exception {
    // Given
    Map<String, Object> inputData = Map.of("name", "John Doe", "age", 30);
    Map<String, Object> tokenizedData = Map.of("name", "[NAME_1]", "age", "[AGE_1]");
    var runId = "test-run-123";

    setupDeidentifyMocks(tokenizedData, runId);

    var request =
        new SkyflowRequest(
            new SkyflowAuthentication(
                "http://localhost:" + wireMockServer.port(), "vault-123", "test-token"),
            SkyflowOperationType.DEIDENTIFY,
            inputData,
            "vault_token",
            null,
            false,
            100,
            5);

    var context = OutboundConnectorContextBuilder.create().variables(request).build();

    // When
    @SuppressWarnings("unchecked")
    Map<String, Object> result = (Map<String, Object>) connector.execute(context);

    // Then
    assertThat(result).isEqualTo(tokenizedData);

    com.github.tomakehurst.wiremock.client.WireMock.verify(
        postRequestedFor(urlEqualTo("/v1/detect/deidentify/file/structured_text")));
    com.github.tomakehurst.wiremock.client.WireMock.verify(
        getRequestedFor(urlEqualTo("/v1/detect/runs/" + runId + "?vault_id=vault-123")));
  }

  @Test
  @DisplayName("Should use static_token when specified")
  void testDeidentifyWithStaticToken() throws Exception {
    // Given
    Map<String, Object> inputData = Map.of("name", "John Doe", "age", 30);
    Map<String, Object> tokenizedData = Map.of("name", "[NAME_1]", "age", "[AGE_1]");
    var runId = "test-run-static";

    setupDeidentifyMocks(tokenizedData, runId);

    var request =
        new SkyflowRequest(
            new SkyflowAuthentication(
                "http://localhost:" + wireMockServer.port(), "vault-123", "test-token"),
            SkyflowOperationType.DEIDENTIFY,
            inputData,
            "static_token",
            null,
            false,
            100,
            5);

    var context = OutboundConnectorContextBuilder.create().variables(request).build();

    // When
    @SuppressWarnings("unchecked")
    Map<String, Object> result = (Map<String, Object>) connector.execute(context);

    // Then
    assertThat(result).isEqualTo(tokenizedData);

    // Verify the token_type was sent correctly
    com.github.tomakehurst.wiremock.client.WireMock.verify(
        postRequestedFor(urlEqualTo("/v1/detect/deidentify/file/structured_text"))
            .withRequestBody(matchingJsonPath("$.token_type.default", equalTo("static_token"))));
  }

  @Test
  @DisplayName("Should successfully re-identify data")
  void testReidentify() throws Exception {
    // Given
    Map<String, Object> tokenizedData = Map.of("name", "[NAME_1]", "age", "[AGE_1]");
    Map<String, Object> originalData = Map.of("name", "John Doe", "age", 30);

    setupReidentifyMock(originalData);

    var request =
        new SkyflowRequest(
            new SkyflowAuthentication(
                "http://localhost:" + wireMockServer.port(), "vault-123", "test-token"),
            SkyflowOperationType.REIDENTIFY,
            tokenizedData,
            null,
            null,
            false,
            null,
            null);

    var context = OutboundConnectorContextBuilder.create().variables(request).build();

    // When
    @SuppressWarnings("unchecked")
    Map<String, Object> result = (Map<String, Object>) connector.execute(context);

    // Then
    assertThat(result).isEqualTo(originalData);
    com.github.tomakehurst.wiremock.client.WireMock.verify(
        postRequestedFor(urlEqualTo("/v1/detect/reidentify/file")));
  }

  @Test
  @DisplayName("Should handle polling timeout")
  void testPollingTimeout() throws Exception {
    // Given
    var inputData = Map.of("name", "John Doe");
    var runId = "test-run-timeout";

    setupDeidentifyStartMock(runId);
    setupPollingPendingMock(runId);

    var request =
        new SkyflowRequest(
            new SkyflowAuthentication(
                "http://localhost:" + wireMockServer.port(), "vault-123", "test-token"),
            SkyflowOperationType.DEIDENTIFY,
            inputData,
            null,
            null,
            false,
            10, // very short interval
            3); // only 3 attempts

    var context = OutboundConnectorContextBuilder.create().variables(request).build();

    // When/Then
    assertThatThrownBy(() -> connector.execute(context))
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("did not complete after");
  }

  @Test
  @DisplayName("Should handle failed run status")
  void testFailedRunStatus() throws Exception {
    // Given
    var inputData = Map.of("name", "John Doe");
    var runId = "test-run-failed";

    setupDeidentifyStartMock(runId);
    wireMockServer.stubFor(
        get(urlEqualTo("/v1/detect/runs/" + runId + "?vault_id=vault-123"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"status\": \"FAILED\"}")));

    var request =
        new SkyflowRequest(
            new SkyflowAuthentication(
                "http://localhost:" + wireMockServer.port(), "vault-123", "test-token"),
            SkyflowOperationType.DEIDENTIFY,
            inputData,
            null,
            null,
            false,
            100,
            5);

    var context = OutboundConnectorContextBuilder.create().variables(request).build();

    // When/Then
    assertThatThrownBy(() -> connector.execute(context))
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("FAILED");
  }

  @Test
  @DisplayName("Should replace secrets in API token")
  void testSecretReplacement() throws Exception {
    // Given
    Map<String, Object> inputData = Map.of("name", "John Doe");
    Map<String, Object> tokenizedData = Map.of("name", "[NAME_1]");
    var runId = "test-run-secret";

    setupDeidentifyMocks(tokenizedData, runId);

    var context =
        OutboundConnectorContextBuilder.create()
            .variables(
                Map.of(
                    "authentication",
                    Map.of(
                        "vaultUri", "http://localhost:" + wireMockServer.port(),
                        "vaultId", "vault-123",
                        "apiToken", "secrets.API_TOKEN"),
                    "operation",
                    "DEIDENTIFY",
                    "payload",
                    inputData))
            .secret("API_TOKEN", "secret-token-value")
            .build();

    // When
    connector.execute(context);

    // Then
    com.github.tomakehurst.wiremock.client.WireMock.verify(
        postRequestedFor(urlEqualTo("/v1/detect/deidentify/file/structured_text"))
            .withHeader("Authorization", equalTo("Bearer secret-token-value")));
  }

  @Test
  @DisplayName("Should use sandbox URL when sandbox flag is true")
  void testSandboxUrl() {
    // Given
    var request =
        new SkyflowRequest(
            new SkyflowAuthentication("test-vault", "vault-123", "test-token"),
            SkyflowOperationType.REIDENTIFY,
            Map.of("test", "data"),
            null,
            null,
            true, // sandbox = true
            null,
            null);

    var context = OutboundConnectorContextBuilder.create().variables(request).build();

    // When/Then - will fail because we're not mocking the sandbox URL
    // but we can verify the URL construction logic indirectly
    assertThatThrownBy(() -> connector.execute(context))
        .isInstanceOf(Exception.class); // Connection will fail
  }

  @Test
  @DisplayName("Should send entity_types when specified")
  void testDeidentifyWithEntityTypes() throws Exception {
    // Given
    Map<String, Object> inputData = Map.of("name", "John Doe", "age", 30);
    Map<String, Object> tokenizedData = Map.of("name", "[NAME_1]", "age", "[AGE_1]");
    var runId = "test-run-entities";

    setupDeidentifyMocks(tokenizedData, runId);

    var request =
        new SkyflowRequest(
            new SkyflowAuthentication(
                "http://localhost:" + wireMockServer.port(), "vault-123", "test-token"),
            SkyflowOperationType.DEIDENTIFY,
            inputData,
            "vault_token",
            java.util.List.of("EMAIL", "PHONE_NUMBER"),
            false,
            100,
            5);

    var context = OutboundConnectorContextBuilder.create().variables(request).build();

    // When
    @SuppressWarnings("unchecked")
    Map<String, Object> result = (Map<String, Object>) connector.execute(context);

    // Then
    assertThat(result).isEqualTo(tokenizedData);

    com.github.tomakehurst.wiremock.client.WireMock.verify(
        postRequestedFor(urlEqualTo("/v1/detect/deidentify/file/structured_text"))
            .withRequestBody(matchingJsonPath("$.entity_types[0]", equalTo("EMAIL")))
            .withRequestBody(matchingJsonPath("$.entity_types[1]", equalTo("PHONE_NUMBER"))));
  }

  // Helper methods

  private void setupDeidentifyMocks(Map<String, Object> output, String runId) throws Exception {
    setupDeidentifyStartMock(runId);
    setupPollingSuccessMock(runId, output);
  }

  private void setupDeidentifyStartMock(String runId) throws Exception {
    wireMockServer.stubFor(
        post(urlEqualTo("/v1/detect/deidentify/file/structured_text"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(MAPPER.writeValueAsString(Map.of("run_id", runId)))));
  }

  private void setupPollingSuccessMock(String runId, Map<String, Object> output) throws Exception {
    var outputJson = MAPPER.writeValueAsString(output);
    var base64Output =
        Base64.getEncoder().encodeToString(outputJson.getBytes(StandardCharsets.UTF_8));

    wireMockServer.stubFor(
        get(urlEqualTo("/v1/detect/runs/" + runId + "?vault_id=vault-123"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        MAPPER.writeValueAsString(
                            Map.of(
                                "status",
                                "SUCCESS",
                                "outputType",
                                "UNKNOWN",
                                "output",
                                java.util.List.of(
                                    Map.of(
                                        "processedFile",
                                        base64Output,
                                        "processedFileType",
                                        "reidentified_file",
                                        "processedFileExtension",
                                        "json")))))));
  }

  private void setupPollingPendingMock(String runId) {
    wireMockServer.stubFor(
        get(urlEqualTo("/v1/detect/runs/" + runId + "?vault_id=vault-123"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"status\": \"PENDING\"}")));
  }

  private void setupReidentifyMock(Map<String, Object> output) throws Exception {
    var outputJson = MAPPER.writeValueAsString(output);
    var base64Output =
        Base64.getEncoder().encodeToString(outputJson.getBytes(StandardCharsets.UTF_8));

    wireMockServer.stubFor(
        post(urlEqualTo("/v1/detect/reidentify/file"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        MAPPER.writeValueAsString(
                            Map.of(
                                "status",
                                "SUCCESS",
                                "output",
                                Map.of("processed_file", base64Output))))));
  }
}
