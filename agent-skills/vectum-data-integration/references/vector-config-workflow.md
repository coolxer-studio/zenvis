# Vector Config Workflow

## Requirement Extraction

Before writing config, identify:

- Source type and address/path/topic/table/bucket.
- Input event format: raw text, JSON, syslog, Apache/Nginx log, metrics, traces, or binary payload.
- Required transforms: parsing, filtering, sampling, redaction, field rename, timestamp normalization, routing, or enrichment.
- Sink type and delivery semantics: console, HTTP, Kafka, ClickHouse, Elasticsearch, file, object storage, or another Vector instance.
- Auth/TLS/secret fields and whether they are known, environment-based, or missing.
- Runtime assumptions: container network names, mounted paths, writable directories, and target service health.
- Task name and description for Vectum.

If credentials or endpoints are missing, ask or use explicit placeholders such as `${ES_PASSWORD}`. Never invent secrets.

## YAML Shape

Use this structure unless a user explicitly asks for TOML or JSON:

```yaml
data_dir: /var/lib/vector

sources:
  source_id:
    type: demo_logs
    format: json

transforms:
  transform_id:
    type: remap
    inputs:
      - source_id
    source: |
      .service = "example"

sinks:
  sink_id:
    type: console
    inputs:
      - transform_id
    encoding:
      codec: json
```

Rules:

- `sources`, `transforms`, and `sinks` are maps keyed by component ID.
- Sources do not have `inputs`.
- Every transform and sink must include `inputs`.
- `inputs` must point only to upstream source/transform IDs that exist in the same topology.
- Keep component IDs stable, lowercase, and descriptive, for example `kafka_orders_in`, `normalize_order`, `clickhouse_orders_out`.

## Validation

Preferred strictness:

1. `vector validate --no-environment <file>` for syntax, required fields, field types, and topology without requiring live endpoints.
2. `vector validate --skip-healthchecks <file>` when local paths should be checked but sink health checks may be unreachable.
3. `vector validate <file>` only when the local runtime can reach all endpoints and write all paths.

Use `scripts/validate_vector_config.sh <file>` for the default `--no-environment` path. Set `VECTOR_VALIDATE_MODE=skip-healthchecks` or `VECTOR_VALIDATE_MODE=full` for the other modes.

If validation fails, fix the config before creating/updating a Vectum task unless the failure is only an expected environment check.

In this project, `./vector/bin/vector` may be a development fixture script rather than the real Vector binary. Use a real compatible Vector binary for validation; if the script times out or prints test heartbeat output, treat local validation as unavailable.

## VRL Guidance

- Use `remap` for parsing and field edits.
- Prefer fallible-safe forms when bad input is expected, for example:

```vrl
parsed, err = parse_json(.message)
if err == null {
  . = merge!(., parsed)
} else {
  .parse_error = string!(err)
}
```

- Use bang functions such as `parse_json!` only when invalid data should fail the event.
- Keep destructive field deletion explicit with `del(.field_name)`.
- Add marker fields such as `.pipeline = "task-name"` when useful for sink-side debugging.

## Error Classification

Configuration errors to repair automatically:

- Unknown field, missing required field, wrong type.
- Invalid `inputs` reference.
- Invalid VRL syntax or type error.
- Unsupported codec or component type.
- Sink/source field name changed across Vector versions.

External blockers to report instead of looping:

- Authentication failure or missing secret.
- Connection refused, timeout, DNS failure, TLS certificate failure.
- File path missing, permission denied, or mount absent.
- Kafka topic/ClickHouse table/Elasticsearch index policy missing when creation is not allowed.

## Official References

- Vector configuration: https://vector.dev/docs/reference/configuration/
- Vector validation: https://vector.dev/docs/administration/validating/
- Sources: https://vector.dev/docs/reference/configuration/sources/
- Transforms: https://vector.dev/docs/reference/configuration/transforms/
- Sinks: https://vector.dev/docs/reference/configuration/sinks/
