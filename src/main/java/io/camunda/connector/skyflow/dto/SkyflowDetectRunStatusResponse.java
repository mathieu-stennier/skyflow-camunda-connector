/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.skyflow.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Response for GET /v1/detect/runs/{runId}.
 *
 * <p>Based on Skyflow Detect API examples.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SkyflowDetectRunStatusResponse(
    String status,
    String outputType,
    List<OutputItem> output,
    String message,
    Double size,
    WordCharacterCount wordCharacterCount,
    Double duration,
    Integer pages,
    Integer slides) {

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record OutputItem(String processedFile, String processedFileType, String processedFileExtension) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record WordCharacterCount(Integer wordCount, Integer characterCount) {}
}

