# Skyflow Detect Connector (Camunda 8)

A **Camunda 8 outbound connector** that integrates with **Skyflow Detect** to:

- **De-identify** (tokenize) sensitive fields in structured JSON data.
- **Re-identify** (reveal) previously tokenized values.

This connector is implemented using the Camunda Connectors SDK (`OutboundConnectorFunction`) and ships with an element template for Camunda Modeler.

## What this connector does

The connector accepts a JSON object payload (typically built from process variables via FEEL), sends it to Skyflow Detect APIs, and returns the processed JSON back to the workflow.

### Supported operations

#### 1) De-identify (`DEIDENTIFY`)

1. Calls Skyflow Detect **de-identify structured text file** endpoint:
   `POST /v1/detect/deidentify/file/structured_text`
2. Reads `run_id` from the response.
3. Polls Skyflow run status until completion:
   `GET /v1/detect/runs/{runId}?vault_id={vaultId}`
4. On `SUCCESS`, decodes the returned `processedFile` (base64 encoded JSON) and returns it as the connector result.

#### 2) Re-identify (`REIDENTIFY`)

1. Calls Skyflow Detect **re-identify file** endpoint:
   `POST /v1/detect/reidentify/file`
2. Decodes the `output.processed_file` (base64 encoded JSON) and returns it as the connector result.

## How it works (implementation notes)

- The connector always wraps the payload as a Skyflow `file` object:
  - `base64`: base64-encoded JSON
  - `data_format`: `"json"`
- Authentication is performed by sending `Authorization: Bearer <apiToken>`.
- `vaultUri` can be either:
  - a full URL (e.g. `https://...` or `http://...`), useful for testing, or
  - a vault subdomain/identifier (e.g. `ebfc9bee4242`), which will be expanded to:
    - production: `https://{vaultUri}.vault.skyflowapis.com`
    - sandbox/preview: `https://{vaultUri}.vault.skyflowapis-preview.com`

## Connector type

`io.camunda:skyflow-detect:1`

## Element template

An element template is included in `element-templates/skyflow-detect-connector.json`.

- Template id: `io.camunda.connectors.SkyflowDetect.v1`
- Name: **Skyflow Detect Connector**

To use templates in Desktop Modeler, place the JSON file in a directory configured as an element template folder (see Camunda docs).

## Configuration (input)

All fields below are available when applying the element template in Camunda Modeler.

### Authentication

| Field | Required | Description |
|------|----------|-------------|
| `authentication.vaultUri` | yes | Skyflow vault base identifier or full URL. If it does not start with `http`, the connector treats it as a vault subdomain. |
| `authentication.vaultId` | yes | Skyflow vault ID. Used as `vault_id` query parameter when polling run status. |
| `authentication.apiToken` | yes | Skyflow bearer token. Can be provided as a Camunda secret (recommended). |

### Operation

| Field | Required | Description |
|------|----------|-------------|
| `operation` | yes | `DEIDENTIFY` or `REIDENTIFY`. |
| `payload` | yes | FEEL expression that evaluates to a JSON object. Example: `{ name: customer.name, ssn: customer.ssn }` |
| `tokenType` | no | Only for `DEIDENTIFY`. Defaults to `vault_token`. Supported: `vault_token`, `static_token`. |
| `entityTypes` | no | Only for `DEIDENTIFY`. Optional list of Skyflow entity type identifiers to de-identify (strings). If omitted/empty, Skyflow de-identifies all entities. |

### Advanced

| Field | Required | Default | Description |
|------|----------|---------|-------------|
| `sandbox` | no | `false` | If `true`, uses the Skyflow preview host (`skyflowapis-preview.com`) when `vaultUri` is not a full URL. |
| `pollIntervalMs` | no | `1500` | Poll interval for `DEIDENTIFY` run status checks. |
| `maxPollAttempts` | no | `40` | Maximum number of polling attempts before timing out. |

## Output

On success, the connector returns a JSON object (a `Map<String, Object>` in Java terms). In BPMN, map it into process variables using the standard connector output mapping.

Typical patterns:

- store entire response into a variable (e.g. `resultVariable = skyflowResult`)
- or use a result expression to map parts of it

## Error handling

If Skyflow returns a non-2xx response, or if the run fails/times out, the connector throws a `ConnectorException`.

Known error codes emitted by this implementation:

- `SKYFLOW_EMPTY_PAYLOAD` – payload missing
- `SKYFLOW_BAD_PAYLOAD` – payload isn’t a JSON object
- `SKYFLOW_DEIDENTIFY_START_FAILED` – start request failed (HTTP error)
- `SKYFLOW_POLL_FAILED` – polling request failed (HTTP error)
- `SKYFLOW_POLL_TIMEOUT` – run didn’t finish in time
- `SKYFLOW_RUN_FAILED` – run finished with FAILED/ERROR status
- `SKYFLOW_REIDENTIFY_FAILED` – re-identify request failed (HTTP error)
- `SKYFLOW_MISSING_RUN_ID` – start response didn’t include `run_id`
- `SKYFLOW_MISSING_PROCESSED_FILE` – response didn’t include an expected processed file

In Camunda, you can handle connector errors using incident handling, retries, and/or an Error Boundary Event with an error expression (see Camunda connector documentation for error handling patterns).

## Secrets

The connector supports Camunda secrets for the API token.

Example: set `authentication.apiToken` to `secrets.SKYFLOW_API_TOKEN` and configure the secret in your runtime.

> Note: In the unit tests, secret replacement is validated by providing `apiToken = "secrets.API_TOKEN"`.

## Build

```bash
mvn clean package
```

This produces:

- a thin JAR
- a shaded (fat) JAR in `target/` suitable for running with a connector runtime

## Run tests

```bash
mvn clean verify
```

## Run locally (connector runtime)

This repository includes a small launcher that boots the Camunda connector runtime in-process:

- Main class: `io.camunda.connector.LocalConnectorRuntime`

It uses the Camunda connectors Spring Boot runtime (test-scoped dependency) and activates the `local` Spring profile.

### Configuration

Check `src/test/resources/application.properties` and update it with either:

- your **Camunda SaaS** cluster connection details (Zeebe + OAuth), or
- your **local** Camunda distribution connection details.

## References

### Camunda

- Camunda 8 Connectors documentation: https://docs.camunda.io/docs/components/connectors/
- Using outbound connectors (retries, etc.): https://docs.camunda.io/docs/next/components/connectors/use-connectors/outbound

### Skyflow

- Skyflow documentation home: https://docs.skyflow.com
- Skyflow authentication overview: https://docs.skyflow.com/docs/fundamentals/api-authentication