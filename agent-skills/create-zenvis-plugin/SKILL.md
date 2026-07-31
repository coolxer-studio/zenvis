---
name: create-zenvis-plugin
description: Create, update, review, validate, and package Zenvis plugins from vendor data dictionaries or integration specifications. Use when an agent needs to build or modify a project under zenvis-plugin, define one-to-one entities and Meta attributes, create Vector YAML ingestion services, add low-code applications or IP statistics pages, configure static HTML dashboards, update plugin documentation, or verify a Zenvis plugin archive against the current backend and frontend contracts.
---

# Create Zenvis Plugin

Build Zenvis plugins from source specifications while keeping entity models, Vector transforms, UI pages, dashboards, documentation, and packaged artifacts mutually consistent.

## Establish Ground Truth

1. Locate the Zenvis workspace and intended plugin directory. Prefer `rg --files` and `rg`.
2. Read every applicable `AGENTS.md` completely before editing files below it.
3. Inspect the current platform contracts instead of relying on remembered schemas:
   - `doc/03-插件开发与集成/plugin-development.md`
   - `DashboardDto`, `PluginServiceImpl`, `MenuDto`, push-task DTOs
   - retrieval Meta models such as `DataEntity` and `DataAttribute`
   - frontend dashboard, low-code, table, copy, and URL handling
4. Read the complete input data dictionary and any cited supplements. Preserve its section order and field order.
5. Inspect nearby plugins for packaging conventions, but treat current backend code as authoritative when examples are stale.
6. Check the working tree before editing. Preserve unrelated user changes.

If a requested configuration conflicts with the checked-out platform code, report the exact incompatibility before changing platform code. Do not expand a config-only request into backend or frontend changes without authorization.

## Plan the Plugin

Create a contract matrix before implementation:

| Data definition | Code | Entity | Table | Kafka topic | UI page | Structured |
| --- | --- | --- | --- | --- | --- | --- |

Apply these rules:

- Map each structured data-definition table to exactly one entity and one ClickHouse table.
- Never merge semantically different definitions into a generic entity such as “command result”.
- Keep non-structured attachments such as PCAP, archives, and sample files outside ClickHouse unless explicitly requested.
- Record compatibility aliases, such as a historical directory code, without changing the canonical entity/table identity.
- Use stable, namespaced identifiers and avoid collisions with existing plugins.

Use this standard layout unless the current repository contract differs:

```text
plugin-name/
├── index.json
├── README.md
├── icon.png
├── 00_doc/
├── 01_meta/
├── 02_push-task/
├── 03_api/
├── 04_ui/
├── 05_dashboard/
│   ├── config.json
│   └── html-page/
├── 06_mcp/
├── 07_skill/
└── 08_menu/
```

## Build Meta Definitions

### Use the Meta contract

- Produce valid JSON with exactly the top-level arrays `entity`, `attribute`, and `operator`. Prefer one Meta file per plugin unless the repository already splits definitions; the backend recursively merges all Meta JSON files and rejects cross-file duplicates.
- Use snake_case identifiers matching `[A-Za-z_][A-Za-z0-9_]*`. Allow `table_name` and `column_name` one optional database qualifier, such as `zenvis.event`.
- Give every entity and attribute a stable positive integer `id`. Keep entity IDs globally unique and attribute IDs globally unique across the loaded Meta directory.
- Do not generate deprecated or invented keys such as `aggregate_link`. `search_type` is optional and only accepts `date` (day precision), `datetime` (second precision), `number`, or `string`; omit it when the default string input is sufficient. Inspect `DataEntity`, `DataAttribute`, `DataOperator`, and current Meta validation before using other fields not listed here.

### Preserve one-to-one modeling

- Define exactly one entity and one ClickHouse table for every structured source definition.
- Define only the fields declared by that source definition. Do not merge different command results, events, reports, or samples into one generic entity, and do not copy fields from adjacent definitions.
- Keep source field order when it is meaningful, then reorder only to improve record usability as described below. Keep every Vector sink’s output set exactly equal to its entity’s business attributes.
- Keep binary attachments, PCAPs, archives, and sample files outside Meta unless the specification explicitly defines a structured record for them.

### Configure entities and storage

- Require `id`, `name`, `label`, `description`, `table_name`, and `data_source` on every entity. Use `data_source: "clickhouse"` for plugin data tables.
- Use stable namespaced names such as `<domain>_<code>_<meaning>`. Check existing Meta and table names for collisions before choosing them.
- Set `sort_column` to an existing physical `column_name`, normally the primary business time or record identifier. The backend rejects a sort column that is not defined by the entity.
- Generate `auto_create` unless the plugin deliberately uses a pre-existing table. Use the current project engine convention, a non-empty physical-column `order_by` list, and:

```json
"auto_create": {
  "engine": "MergeTree()",
  "order_by": ["event_id", "event_time"],
  "partition_by": "toYYYYMM(zenvis_insert_time)"
}
```

- Prefer genuine business identifiers and business time in `order_by`. Do not use the high-cardinality generated `zenvis_id`, and do not partition by optional or vendor-controlled time.
- Let Zenvis inject `zenvis_id` as `Nullable(UUID)` and `zenvis_insert_time` as `DateTime64(3)`. Never define, ingest, transform, or write either reserved field. Use `zenvis_insert_time` only in storage expressions and platform queries.

### Define attributes completely

- Require `id`, `entity`, `name`, `label`, `description`, `column_name`, `column_type`, `operators`, and `display_selected` on every business attribute.
- Point `entity` to an existing `entity.name`. Keep each `name` unique within its entity and every positive attribute ID unique globally.
- Use logical `name` for API and template references and physical `column_name` for ClickHouse. Keep them equal unless an existing schema requires an alias.
- Omit `display_name` by default. If required, use a valid SQL-select alias rather than a Chinese label.
- Set `display_selected` deliberately so default results remain useful without making very large payload or JSON fields default columns.
- Set `must_candidate: true` only when input must be restricted to a supplied `mapping`; otherwise set it to `false`. Enable `auto_complete` only for fields with useful, bounded distinct values.
- Write `description` for users, not implementers: explain meaning, format, units, enumerations, separators, encoding, examples, and conditional requirements such as “required when ack=2”. Do not repeat only the label.

### Derive types from the source document

Inspect current backend and ClickHouse support, then use the narrowest semantically correct type:

| Source meaning | Meta configuration |
| --- | --- |
| Text, IDs, hashes, URLs, domains, mixed IPv4/IPv6, opaque Base64 | `column_type: "String"` |
| Signed integer | The smallest safe `Int8`/`Int16`/`Int32`/`Int64` |
| Non-negative enum or count | The smallest safe `UInt8`/`UInt16`/`UInt32`/`UInt64` |
| Decimal rate, ratio, or measurement | `Float64`, or a documented `Decimal` when exact precision is required |
| Boolean semantics | `Bool` |
| Second-precision business time | `DateTime` |
| Millisecond-precision business time | `DateTime64(3)` |
| Explicit repeated values | `column_type: "Array(String)"`, `display_type: "array"` |
| Explicit JSON object/value | `column_type: "json"`, `display_type: "json"` |

- Do not infer JSON or arrays from punctuation in one example. Require the specification to declare JSON semantics or repeated values.
- Preserve mixed IPv4/IPv6 as `String` unless the checked-out platform explicitly supports the required mixed IP type.
- Convert documented timestamps in Vector to the chosen ClickHouse time representation. Use `retrieval_type: "date"` only when the stored representation or current frontend contract requires date-input conversion; do not add it mechanically to ordinary `DateTime` fields.
- Decode Base64 only under the ingestion rules below. Store explicitly decoded JSON as `json`, explicitly decoded readable text as `String`, and unspecified or opaque Base64 as `String`. State the chosen behavior in `description`.

### Assign operators and display behavior

- Define every referenced operator in the top-level `operator` array even when the current backend can supplement defaults. Use only operators supported by the current query engine.
- Use these normal sets as a starting point:
  - strings and JSON: `equal`, `notequal`, `isnull`, `isnotnull`, `in`, with `match` only when text matching is meaningful;
  - arrays: `equal`, `notequal`, `isnull`, `isnotnull`, `in`, `match`;
  - numbers: `equal`, `notequal`, `isnull`, `isnotnull`, `in`, `greatthan`, `greatequalthan`, `lessthan`, `lessequalthan`, `between`;
  - dates: `equal`, `notequal`, `isnull`, `isnotnull`, `greatthan`, `greatequalthan`, `lessthan`, `lessequalthan`, `between`.
- Set `copyable: true` on reusable IDs, IPs, domains, URLs, rule/task/command numbers, file paths, and hashes. Verify the copy control remains available when long values are visually truncated.
- Order each entity’s attributes by business importance. Put the best business identifier for the record first, followed by primary time, rule/task identity, source/destination, status, and detail payloads as appropriate.

### Configure links and record details

- Unless explicitly overridden, add `link_template` to the first attribute and open a standalone low-code record-detail page using the built-in record ID:

```json
"link_template": "/#/service/low-code-page/<package_name>?detail_entity=<entity_name>&record_id={zenvis_id}"
```

- Query the detail record with `GET /api/v1/entity/{entity}/{record_id}/view`. Use a business identifier as the API parameter only when explicitly required.
- Use only string `link_template` values. Allow placeholders only in `{logical_attribute_name}` form and only for attributes in the same entity; `{zenvis_id}` is available because Zenvis injects it before validation.
- Use only relative URLs or absolute `http/https` URLs. Reject protocol-relative URLs and `javascript:`, `data:`, `blob:`, or `file:` targets.
- Let the backend decide which link dependencies are returned. It automatically adds `zenvis_id` as a hidden result field when a displayed link references it. The frontend does not require placeholders to be visible columns, but it still requires the value to exist in the returned row and the resolved URL to be safe.
- Add IP statistics links to actual IP address fields when the plugin provides that page:

```json
"link_template": "/#/service/low-code-page/<package_name>?ip={field}"
```

### Validate Meta as a contract

- Parse every Meta JSON file and load it with the current backend validator when possible.
- Confirm entity count equals the structured-definition count and every source field maps to exactly one attribute.
- Confirm IDs and entity-local names are unique; all identifiers are legal; every attribute references an existing entity and every operator reference resolves.
- Confirm every `sort_column`, `order_by`, and Vector output field resolves to the intended physical column.
- Confirm all partitions use `toYYYYMM(zenvis_insert_time)` and no plugin explicitly defines either reserved field.
- Confirm type, display type, description, copy behavior, ordering, mappings, operators, and links against the source document rather than only against generated JSON syntax.

## Build Vector Push Tasks

### Use the required architecture

Default to YAML. Do not generate TOML or JSON unless explicitly requested.

Create two services:

1. Source-to-Kafka:
   - Follow the source mechanism declared by the specification, such as SFTP-landed files.
   - Forward each raw record unchanged.
   - Use Kafka `raw_message` encoding.
   - Do not parse, decode, rename, enrich, or drop business fields.
2. Kafka-to-ClickHouse:
   - Consume one topic or route per structured definition.
   - Split and map fields in the documented order.
   - Transform only what the data contract requires.
   - Write to the table linked by Meta.

Do not create a demo push service unless the user explicitly requests one.

### Transform safely

- Ensure each transform emits exactly the Meta attribute names for its entity.
- Validate the expected field count before indexing split arrays.
- Route malformed rows to a DLQ with source code, raw record, and error reason.
- Convert documented times with the specified timezone and exact output format.
- Parse arrays only where the document declares repeated values.
- Convert numeric and boolean fields deliberately; do not silently turn all invalid values into `0` or `false`.
- Decode Base64 only when the description explicitly says the decoded content is JSON or readable text, URL, email content, or another named string form.
- Keep opaque Base64 payloads, packets, rule fragments, and responses encoded unless the document explicitly requires decoding.
- On readable-string decode failure, preserve the original value.
- For explicit JSON, decode if required, parse JSON, and preserve traceable raw content on parse failure according to project convention.
- Use environment variables for Kafka, ClickHouse, credentials, and tunables. Do not commit real or default production passwords.
- Configure acknowledgements, retries, buffers, batching, and health checks in proportion to delivery requirements.
- Make historical-file age limits configurable when delayed delivery or backfill is possible.

Register both YAML files in `02_push-task/config.json` with clear names and descriptions.

## Build UI and IP Investigation

- Create a distinct list page for every structured entity.
- Include every entity in the low-code app navigation.
- Keep API entity names aligned with Meta; do not use a combined endpoint as a shortcut.
- Add appropriate copy controls and IP jump links to list columns.

When the plugin requires cross-entity IP investigation:

1. Create a standalone low-code page with an IP input.
2. Accept an optional `ip` query parameter and allow manual entry when absent.
3. Add a dedicated menu entry.
4. Match source/destination IP fields with OR semantics within each entity so one row counts once.
5. Return zero for entities without IP fields and retain them in the type overview when required.
6. Pass multi-entity query parameters as a comma-separated scalar unless the current server explicitly supports bracket notation. Avoid `entities[0]` style URLs that Tomcat may reject.
7. Configure IP Meta attributes with `link_template` to this page.

Validate the low-code schema shape expected by the current SDK. In particular, distinguish a low-code app with `data.pages` from a standalone low-code page schema.

## Build Dashboards

Use a self-contained static HTML single page by default unless the user requests low-code or an external link.

For `05_dashboard/config.json`:

- Use a JSON array compatible with the current `DashboardDto`.
- Use snake_case fields such as `config_index` and `html_path`.
- Provide a globally unique, package-namespaced `code`.
- For `HTML_PAGE`, make `html_path` represent only the plugin-package-relative file path:

```json
[
  {
    "name": "安全态势总览",
    "code": "com.example.plugin.dashboard.security-overview",
    "type": "HTML_PAGE",
    "html_path": "security-overview.html"
  }
]
```

- Place that file at `05_dashboard/html-page/security-overview.html`.
- Do not put `/html-page/`, `/zenvis/html-page/`, the package name, or a deployment URL in `html_path` unless the checked-out installer contract explicitly says otherwise.
- Allow the installer to copy the file and derive its runtime public URL.

For the HTML page:

- Use actual Zenvis APIs and current login-session behavior; do not present demo values as real data.
- Keep CSS and JavaScript self-contained unless external assets are explicitly allowed.
- Include loading, empty, error, last-updated, manual refresh, and automatic refresh states.
- Design for at least 16:9 full-screen use and verify a representative viewport such as 1920×1080.
- Use security-domain metrics, not only generic record totals, when supported: event severity, top IPs, protocol/port distribution, device freshness, command failure rate, rule status, and sample trends.
- Do not label historical presence as pipeline “health”. Use recent insert time, service heartbeat, Kafka lag, or sink status for health claims.
- Avoid browser-dependent parsing of non-standard date strings.
- Resolve the API base from the current deployment convention and avoid generating a failing request before trying the known base path.

## Document and Version

- Copy or reference the authoritative source specification in `00_doc/` when permitted.
- Update the source document when the user adds a new definition.
- Document entity mapping, type mapping, directory conventions, environment variables, Base64 policy, and operational limitations.
- Keep README claims synchronized with the actual Meta, Vector, UI, and dashboard files.
- Bump the plugin version for material distributable changes unless the user explicitly limits the request to a single file.
- Do not modify version, documentation, platform code, or archives when the user explicitly says to change only one configuration file.

## Validate Before Handoff

Run validation proportional to the change:

1. Parse every JSON file.
2. Parse YAML and run `vector validate` using the production Vector version or container when available.
3. Confirm entity count equals the structured-definition count.
4. Confirm entity names, tables, Kafka topics, transforms, sinks, UI pages, and dashboard entity lists are aligned.
5. Compare every transform output field with the corresponding Meta attributes; report missing and extra fields.
6. Run the complete “Validate Meta as a contract” checklist, including first-attribute detail links, IP links, copy flags, and reserved-field exclusions.
7. Generate representative data for every definition and exercise source → Kafka → ClickHouse when infrastructure is available.
8. Run targeted backend/frontend tests for any platform changes.
9. Compile inline dashboard JavaScript and perform browser visual QA for material dashboard changes.
10. Remove packaging noise such as `.DS_Store`.
11. Build the plugin with the repository’s build script.
12. Inspect the archive itself, not only the source tree:
    - verify `index.json` and version;
    - verify the latest Meta, push-task, UI, dashboard, and menu configs;
    - verify static HTML and documentation are present;
    - verify the archive does not contain stale configuration from a previous build.

If an official validator is unavailable, state that limitation and identify the exact validation still required in the target environment.

## Handoff

Report:

- plugin path and package path;
- plugin version;
- entity and table counts;
- push-task architecture;
- UI, IP investigation, and dashboard entry points;
- validation commands and outcomes;
- unresolved compatibility or deployment risks.

Lead with confirmed outcomes. Do not claim live integration success when only static validation was performed.
