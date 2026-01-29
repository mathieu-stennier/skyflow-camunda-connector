/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.skyflow.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Request body for POST /v1/detect/reidentify/file. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SkyflowReidentifyFileRequest(
    SkyflowFile file,
    @JsonProperty("vault_id") String vaultId) {}
