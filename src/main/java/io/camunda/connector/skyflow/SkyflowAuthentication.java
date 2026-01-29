/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.skyflow;

import io.camunda.connector.generator.dsl.Property;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import jakarta.validation.constraints.NotBlank;

public record SkyflowAuthentication(
    @TemplateProperty(
            id = "vaultUri",
            label = "Vault URI",
            group = "authentication",
            description = "The subdomain/identifier of your vault (e.g. ebfc9bee4242)",
            feel = Property.FeelMode.disabled)
        @NotBlank
        String vaultUri,
    @TemplateProperty(
            id = "vaultId",
            label = "Vault ID",
            group = "authentication",
            feel = Property.FeelMode.disabled)
        @NotBlank
        String vaultId,
    @TemplateProperty(
            id = "apiToken",
            label = "API Token",
            group = "authentication",
            description = "Bearer token for authentication",
            feel = Property.FeelMode.optional)
        @NotBlank
        String apiToken) {}
