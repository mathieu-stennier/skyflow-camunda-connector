/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.skyflow.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response for POST /v1/detect/reidentify/file.
 *
 * <p>Note the API uses snake_case for some fields.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SkyflowReidentifyResponse(
    String status,
    @JsonProperty("output_type") String outputType,
    Output output) {

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Output(
      @JsonProperty("processed_file") String processedFile,
      @JsonProperty("processed_file_type") String processedFileType,
      @JsonProperty("processed_file_extension") String processedFileExtension) {}
}

