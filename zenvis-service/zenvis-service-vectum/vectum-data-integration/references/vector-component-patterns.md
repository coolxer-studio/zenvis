# Vector Component Patterns

These are starter patterns, not a substitute for validation. Run `scripts/validate_vector_config.sh` before deployment and check the official component page when adding uncommon options.

## Minimal Smoke Test

```yaml
data_dir: /var/lib/vector

sources:
  demo_in:
    type: demo_logs
    format: json

sinks:
  console_out:
    type: console
    inputs:
      - demo_in
    encoding:
      codec: json
```

## File JSON Logs To Console

```yaml
data_dir: /var/lib/vector

sources:
  app_file_in:
    type: file
    include:
      - /var/log/app/*.log
    read_from: beginning

transforms:
  parse_json:
    type: remap
    inputs:
      - app_file_in
    source: |
      parsed, err = parse_json(.message)
      if err == null {
        . = merge!(., parsed)
      } else {
        .parse_error = string!(err)
      }

sinks:
  console_out:
    type: console
    inputs:
      - parse_json
    encoding:
      codec: json
```

## Kafka To Kafka

```yaml
data_dir: /var/lib/vector

sources:
  kafka_in:
    type: kafka
    bootstrap_servers: kafka:9092
    topics:
      - input-topic
    group_id: vectum-consumer
    decoding:
      codec: json

transforms:
  normalize:
    type: remap
    inputs:
      - kafka_in
    source: |
      .pipeline = "vectum"

sinks:
  kafka_out:
    type: kafka
    inputs:
      - normalize
    bootstrap_servers: kafka:9092
    topic: output-topic
    encoding:
      codec: json
```

## Syslog To Console

```yaml
data_dir: /var/lib/vector

sources:
  syslog_in:
    type: syslog
    address: 0.0.0.0:514
    mode: tcp

sinks:
  console_out:
    type: console
    inputs:
      - syslog_in
    encoding:
      codec: json
```

## HTTP Server To HTTP Sink

Validate this against the Vector version in use before deployment because HTTP decoding options have changed across versions.

```yaml
data_dir: /var/lib/vector

sources:
  http_in:
    type: http_server
    address: 0.0.0.0:8080
    decoding:
      codec: json

sinks:
  http_out:
    type: http
    inputs:
      - http_in
    uri: http://receiver:8080/ingest
    method: post
    encoding:
      codec: json
```

## To Elasticsearch

```yaml
data_dir: /var/lib/vector

sources:
  demo_in:
    type: demo_logs
    format: json

sinks:
  es_out:
    type: elasticsearch
    inputs:
      - demo_in
    endpoints:
      - http://elasticsearch:9200
    bulk:
      index: vector-%Y-%m-%d
```

## To ClickHouse

Validate table schema and auth options with the target Vector version and ClickHouse deployment.

```yaml
data_dir: /var/lib/vector

sources:
  demo_in:
    type: demo_logs
    format: json

sinks:
  clickhouse_out:
    type: clickhouse
    inputs:
      - demo_in
    endpoint: http://clickhouse:8123
    database: default
    table: logs
    auth:
      strategy: basic
      user: default
      password: ${CLICKHOUSE_PASSWORD}
```

## Useful Component Docs

- Demo logs source: https://vector.dev/docs/reference/configuration/sources/demo_logs/
- File source: https://vector.dev/docs/reference/configuration/sources/file/
- HTTP server source: https://vector.dev/docs/reference/configuration/sources/http_server/
- Kafka source: https://vector.dev/docs/reference/configuration/sources/kafka/
- Syslog source: https://vector.dev/docs/reference/configuration/sources/syslog/
- Remap transform: https://vector.dev/docs/reference/configuration/transforms/remap/
- Filter transform: https://vector.dev/docs/reference/configuration/transforms/filter/
- Console sink: https://vector.dev/docs/reference/configuration/sinks/console/
- HTTP sink: https://vector.dev/docs/reference/configuration/sinks/http/
- Kafka sink: https://vector.dev/docs/reference/configuration/sinks/kafka/
- ClickHouse sink: https://vector.dev/docs/reference/configuration/sinks/clickhouse/
- Elasticsearch sink: https://vector.dev/docs/reference/configuration/sinks/elasticsearch/
