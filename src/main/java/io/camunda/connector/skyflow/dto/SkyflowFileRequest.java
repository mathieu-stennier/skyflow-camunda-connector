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
 * Base request shape for Skyflow Detect endpoints that accept a file + vault id.
 *
 * <p>Example:
 *
 * <pre>
 * {
 *   "file": { "base64": "...", "data_format": "json" },
 *   "vault_id": "..."
 * }
 * </pre>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SkyflowFileRequest(SkyflowFile file, @JsonProperty("vault_id") String vaultId) {}

