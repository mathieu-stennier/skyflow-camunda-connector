/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.skyflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.connector.skyflow.dto.SkyflowDetectRunStartResponse;
import io.camunda.connector.skyflow.dto.SkyflowDetectRunStatusResponse;
import io.camunda.connector.skyflow.dto.SkyflowReidentifyResponse;
import io.camunda.connector.skyflow.dto.SkyflowDeidentifyStructuredTextRequest;
import io.camunda.connector.skyflow.dto.SkyflowFile;
import io.camunda.connector.skyflow.dto.SkyflowReidentifyFileRequest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.net.URLEncoder;
import java.util.Base64;
import java.util.Map;

@OutboundConnector(
    name = "Skyflow Detect",
    inputVariables = {"authentication", "operation", "payload", "entityTypes"},
    type = "io.camunda:skyflow-detect:1")
@ElementTemplate(
    id = "io.camunda.connectors.SkyflowDetect.v1",
    name = "Skyflow Detect Connector",
    version = 1,
    description = "De-identify and re-identify sensitive data via Skyflow Detect APIs",
    documentationRef = "https://docs.skyflow.com",
    icon = "icon.png",
    inputDataClass = SkyflowRequest.class,
    propertyGroups = {
      @ElementTemplate.PropertyGroup(id = "authentication", label = "Authentication"),
      @ElementTemplate.PropertyGroup(id = "operation", label = "Operation"),
      @ElementTemplate.PropertyGroup(id = "advanced", label = "Advanced")
    })
public class SkyflowDetectConnector implements OutboundConnectorFunction {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final int DEFAULT_POLL_INTERVAL_MS = 1500;
  private static final int DEFAULT_MAX_POLL_ATTEMPTS = 40;

  private static final String AUTHORIZATION_HEADER = "Authorization";
  private static final String BEARER_PREFIX = "Bearer ";
  private static final String CONTENT_TYPE_HEADER = "Content-Type";
  private static final String CONTENT_TYPE_JSON = "application/json";

  private static final String DATA_FORMAT_JSON = "json";

  @Override
  public Object execute(OutboundConnectorContext context) throws Exception {
    var request = context.bindVariables(SkyflowRequest.class);
    var cfg = toConfig(request);

    String base64Payload = encodePayloadAsBase64(cfg.payload);

    var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();

    return switch (cfg.operation) {
      case DEIDENTIFY -> {
        String runId = startDeidentify(http, cfg, base64Payload);
        yield pollForResult(http, cfg, runId);
      }
      case REIDENTIFY -> reidentify(http, cfg, base64Payload);
    };
  }

  private Config toConfig(SkyflowRequest request) {
    var auth = request.authentication();
    return new Config(
        request.operation(),
        normalizeBaseUrl(auth.vaultUri(), Boolean.TRUE.equals(request.sandbox())),
        auth.vaultId(),
        auth.apiToken(),
        request.payload(),
        request.tokenType() != null ? request.tokenType() : "vault_token",
        request.entityTypes(),
        request.pollIntervalMs() != null ? request.pollIntervalMs() : DEFAULT_POLL_INTERVAL_MS,
        request.maxPollAttempts() != null ? request.maxPollAttempts() : DEFAULT_MAX_POLL_ATTEMPTS);
  }

  private String encodePayloadAsBase64(Object payload) throws Exception {
    var payloadMap = coercePayload(payload);
    var json = MAPPER.writeValueAsString(payloadMap);
    return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
  }

  private String normalizeBaseUrl(String vaultUri, boolean sandbox) {
    if (vaultUri.startsWith("http")) {
      return vaultUri.replaceAll("/+$", "");
    }
    String host =
        sandbox
            ? vaultUri + ".vault.skyflowapis-preview.com"
            : vaultUri + ".vault.skyflowapis.com";
    return "https://" + host;
  }

  private String startDeidentify(HttpClient http, Config cfg, String base64) throws Exception {
    SkyflowDeidentifyStructuredTextRequest body =
        new SkyflowDeidentifyStructuredTextRequest(
            new SkyflowFile(base64, DATA_FORMAT_JSON),
            cfg.vaultId,
            new SkyflowDeidentifyStructuredTextRequest.TokenType(cfg.tokenType),
            (cfg.entityTypes == null || cfg.entityTypes.isEmpty()) ? null : cfg.entityTypes);

    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create(cfg.baseUrl + "/v1/detect/deidentify/file/structured_text"))
            .header(AUTHORIZATION_HEADER, BEARER_PREFIX + cfg.apiToken)
            .header(CONTENT_TYPE_HEADER, CONTENT_TYPE_JSON)
            .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)))
            .build();

    HttpResponse<String> resp = send(http, request, "SKYFLOW_DEIDENTIFY_START_FAILED");

    SkyflowDetectRunStartResponse parsed =
        MAPPER.readValue(resp.body(), SkyflowDetectRunStartResponse.class);
    if (parsed.runId() == null || parsed.runId().isBlank()) {
      throw new ConnectorException(
          "SKYFLOW_MISSING_RUN_ID", "Skyflow response did not include run_id");
    }
    return parsed.runId();
  }

  private Map<String, Object> pollForResult(HttpClient http, Config cfg, String runId)
      throws Exception {
    int attempts = 0;
    while (attempts++ < cfg.maxPollAttempts) {
      HttpRequest req =
          HttpRequest.newBuilder()
              .uri(URI.create(runStatusUri(cfg, runId)))
              .header(AUTHORIZATION_HEADER, BEARER_PREFIX + cfg.apiToken)
              .GET()
              .build();

      HttpResponse<String> resp = send(http, req, "SKYFLOW_POLL_FAILED");

      SkyflowDetectRunStatusResponse parsed =
          MAPPER.readValue(resp.body(), SkyflowDetectRunStatusResponse.class);
      String status = parsed.status() != null ? parsed.status() : "";

      if ("SUCCESS".equalsIgnoreCase(status)) {
        return decodeProcessedFileFromRunStatus(parsed);
      }
      if ("FAILED".equalsIgnoreCase(status) || "ERROR".equalsIgnoreCase(status)) {
        throw new ConnectorException(
            "SKYFLOW_RUN_FAILED", "Skyflow run status: " + status + " - " + resp.body());
      }
      Thread.sleep(cfg.pollIntervalMs);
    }
    throw new ConnectorException(
        "SKYFLOW_POLL_TIMEOUT",
        "Skyflow run did not complete after " + cfg.maxPollAttempts + " attempts");
  }

  /**
   * Build the run status URL.
   *
   * <p>Skyflow run status requires the vault id as a query param.
   */
  private String runStatusUri(Config cfg, String runId) {
    return cfg.baseUrl
        + "/v1/detect/runs/"
        + urlEncode(runId)
        + "?vault_id="
        + urlEncode(cfg.vaultId);
  }

  private String urlEncode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  private Map<String, Object> reidentify(HttpClient http, Config cfg, String base64)
      throws Exception {
    SkyflowReidentifyFileRequest body =
        new SkyflowReidentifyFileRequest(new SkyflowFile(base64, DATA_FORMAT_JSON), cfg.vaultId);

    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create(cfg.baseUrl + "/v1/detect/reidentify/file"))
            .header(AUTHORIZATION_HEADER, BEARER_PREFIX + cfg.apiToken)
            .header(CONTENT_TYPE_HEADER, CONTENT_TYPE_JSON)
            .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)))
            .build();

    HttpResponse<String> resp = send(http, request, "SKYFLOW_REIDENTIFY_FAILED");

    SkyflowReidentifyResponse parsed = MAPPER.readValue(resp.body(), SkyflowReidentifyResponse.class);
    return decodeProcessedFileBase64(parsed.output() != null ? parsed.output().processedFile() : null);
  }

  private Map<String, Object> decodeProcessedFileFromRunStatus(SkyflowDetectRunStatusResponse resp)
      throws Exception {
    String base64 = null;
    if (resp.output() != null && !resp.output().isEmpty()) {
      base64 = resp.output().getFirst().processedFile();
    }
    return decodeProcessedFileBase64(base64);
  }

  private Map<String, Object> decodeProcessedFileBase64(String base64ProcessedFile)
      throws Exception {
    if (base64ProcessedFile == null || base64ProcessedFile.isBlank()) {
      throw new ConnectorException(
          "SKYFLOW_MISSING_PROCESSED_FILE", "Missing processed_file in Skyflow response");
    }

    byte[] decoded = Base64.getDecoder().decode(base64ProcessedFile);
    @SuppressWarnings("unchecked")
    Map<String, Object> result = MAPPER.readValue(decoded, Map.class);
    return result;
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> coercePayload(Object payload) {
    if (payload == null) {
      throw new ConnectorException("SKYFLOW_EMPTY_PAYLOAD", "Payload (FEEL → JSON) is required");
    }
    if (payload instanceof Map) {
      return (Map<String, Object>) payload;
    }
    try {
      return MAPPER.readValue(String.valueOf(payload), Map.class);
    } catch (Exception e) {
      throw new ConnectorException(
          "SKYFLOW_BAD_PAYLOAD", "Payload must be a JSON object; got: " + payload, e);
    }
  }

  private HttpResponse<String> send(HttpClient http, HttpRequest request, String errorCode)
      throws Exception {
    HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString());
    if (resp.statusCode() / 100 != 2) {
      throw new ConnectorException(
          errorCode,
          "Skyflow request failed: HTTP " + resp.statusCode() + " - " + resp.body());
    }
    return resp;
  }

  private record Config(
      SkyflowOperationType operation,
      String baseUrl,
      String vaultId,
      String apiToken,
      Object payload,
      String tokenType,
      java.util.List<String> entityTypes,
      int pollIntervalMs,
      int maxPollAttempts) {}
}
