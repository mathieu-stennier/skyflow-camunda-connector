/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.skyflow;

import io.camunda.connector.api.annotation.FEEL;
import io.camunda.connector.generator.dsl.Property;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record SkyflowRequest(
        @TemplateProperty(id = "authentication", label = "Authentication", group = "authentication")
        @Valid
        @NotNull
        SkyflowAuthentication authentication,
        @TemplateProperty(
                id = "operation",
                label = "Operation",
                group = "operation",
                type = PropertyType.Dropdown,
                defaultValue = "DEIDENTIFY",
                choices = {
                        @TemplateProperty.DropdownPropertyChoice(value = "DEIDENTIFY", label = "De-identify"),
                        @TemplateProperty.DropdownPropertyChoice(value = "REIDENTIFY", label = "Re-identify")
                })
        @NotNull
        SkyflowOperationType operation,
        @TemplateProperty(
                id = "payload",
                label = "Payload",
                group = "operation",
                description =
                        "FEEL expression evaluating to a JSON object with data to process. Example: { name: name, age: age }",
                type = PropertyType.Text,
                feel = Property.FeelMode.required)
        @NotNull
        Object payload,
        @TemplateProperty(
                id = "tokenType",
                label = "Token type",
                group = "operation",
                description =
                        "Type of tokens to generate during de-identification (only applies to DEIDENTIFY operation)",
                type = PropertyType.Dropdown,
                defaultValue = "vault_token",
                optional = true,
                choices = {
                        @TemplateProperty.DropdownPropertyChoice(
                                value = "vault_token",
                                label = "Vault Token (stores tokens in vault)"),
                        @TemplateProperty.DropdownPropertyChoice(
                                value = "static_token",
                                label = "Static Token (hides data without vaulting)")
                })
        String tokenType,
        @FEEL
        @TemplateProperty(
                id = "entityTypes",
                label = "Entity types",
                group = "operation",
                description =
                        "Optional FEEL expression resolving to a list of entity types to de-identify (strings). Example: [\"EMAIL\", \"PHONE_NUMBER\"]. If omitted/empty, Skyflow will de-identify all supported entities.",
                optional = true,
                feel = Property.FeelMode.required,
                condition =
                @TemplateProperty.PropertyCondition(property = "operation", equals = "DEIDENTIFY"))
        List<String> entityTypes,
        @TemplateProperty(
                id = "sandbox",
                label = "Use Sandbox (preview) API",
                group = "advanced",
                type = PropertyType.Boolean,
                optional = true)
        Boolean sandbox,
        @TemplateProperty(
                id = "pollIntervalMs",
                label = "Poll interval (ms)",
                group = "advanced",
                type = PropertyType.Number,
                optional = true)
        Integer pollIntervalMs,
        @TemplateProperty(
                id = "maxPollAttempts",
                label = "Max poll attempts",
                group = "advanced",
                type = PropertyType.Number,
                optional = true)
        Integer maxPollAttempts) {
}
