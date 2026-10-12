---
name: create-zenvis-plugin
description: Create, update, review, validate, and package Zenvis plugins from vendor data dictionaries or integration specifications. Use when an agent needs to build or modify a project under zenvis-plugin, define one-to-one entities and Meta attributes, create Vector YAML ingestion services, build a 03_api thin dynamic-API JAR with versioned MySQL migrations, register MCP connections, ship 07_skill runtime skills, add low-code applications, record-detail or IP statistics pages, configure static HTML or low-code dashboards and menus, update plugin documentation and marketplace registration, or verify a Zenvis plugin archive against the current backend and frontend contracts.
---

# Create Zenvis Plugin

Build Zenvis plugins from source specifications while keeping entity models, Vector transforms, dynamic APIs, migrations, UI pages, dashboards, MCP, skills, documentation, and packaged artifacts mutually consistent.

## Establish Ground Truth

1. Locate the Zenvis workspace and intended plugin directory. Prefer `rg --files` and `rg`.
2. Read every applicable `AGENTS.md` completely before editing files below it. In this workspace they live at `zenvis-backend/AGENTS.md`, `zenvis-frontend/AGENTS.md`, `zenvis-service/zenvis-service-vectum/AGENTS.md`, and `zenvis-service/zenvis-service-analyzer/AGENTS.md` (there is none under `zenvis-plugin/`).
3. Inspect the current platform contracts instead of relying on remembered schemas. The authoritative documents are under `doc/03-插件开发与集成/`:
   - `插件包规范.md` — directory layout, identities, package boundaries
   - `Meta与数据建模.md` — entity/attribute/operator and ClickHouse schema
   - `数据接入与推送任务.md` — push tasks, Vector, DLQ
   - `动态API与数据迁移.md` — thin JAR, class loading, MySQL migrations
   - `UI看板与菜单.md` — low-code layout, dashboards, menus
   - `MCP与Skill集成.md` — 06_mcp connections and 07_skill manifests
   - `生命周期与发布验证.md` — install/upgrade/uninstall and archive validation
   When a contract detail matters, also inspect the current code: `DashboardDto`, `PluginServiceImpl`, `MenuDto`, push-task DTOs, retrieval Meta models such as `DataEntity`/`DataAttribute`, and frontend dashboard, low-code, table, copy, and URL handling.
4. Read the complete input data dictionary and any cited supplements. Preserve its section order and field order.
5. Inspect nearby plugins for packaging conventions, but treat current platform documentation and code as authoritative when examples are stale. Existing plugins mix current and legacy layouts on purpose.
6. Check the working tree before editing. Each `zenvis-plugin/zenvis-plugin-*` directory is typically an independent repository. Preserve unrelated user changes.

If a requested configuration conflicts with the checked-out platform contract, report the exact incompatibility before changing platform code. Do not expand a config-only request into backend or frontend changes without authorization.

## Choose the Plugin Archetype and Plan

Decide the archetype before creating directories; capability directories are optional and installers process them in numeric order:

| Archetype | Typical directories | Reference plugin |
| --- | --- | --- |
| Full data pipeline | all of 00–08, often plus 03_api/06_mcp/07_skill | `zenvis-plugin-btw`, `zenvis-plugin-onesoc` |
| Lightweight data ingestion | 00/01/02/04/08, empty 05/06 arrays | `zenvis-plugin-nginx` |
| Integration-only (no data of its own) | 00_doc, 03_api, 04_ui/app, 08_menu | `zenvis-plugin-lubinsun` |
| Static HTML tool | 00_doc, 04_ui/html-page, 08_menu (`HTML_PAGE`) | `zenvis-plugin-rzweb` |
| Legacy sample | TOML push task, flat 04_ui layout | `zenvis-plugin-user-event` (maintenance only) |

Create a contract matrix before implementation:

| Data definition | Code | Entity | Table | Kafka topic | UI page | Structured |
| --- | --- | --- | --- | --- | --- | --- |
|

Apply these rules:

- Map each structured data-definition table to exactly one entity and one ClickHouse table.
- Never merge semantically different definitions into a generic entity such as “command result”.
- Keep non-structured attachments such as PCAP, archives, and sample files outside ClickHouse unless explicitly required; archive them to object storage instead.
- Record compatibility aliases, such as a historical directory code, without changing the canonical entity/table identity.
- Use stable, namespaced identifiers and avoid collisions with existing plugins.

Treat these as resource identities that must stay stable across upgrades (they preserve runtime state, role grants, and dashboard/MCP records): `package_name`; entity `name`; table/column names; dashboard `code`; MCP `code`; menu match key (`type + params`, else `type + route + name`); push task `name`; skill `id`; UI config indexes. Display labels and descriptions may iterate; identities may not.

Use this standard layout, including only the directories the archetype needs:

```text
plugin-name/
├── index.json
├── README.md
├── icon.png
├── 00_doc/
│   └── README.md                # required: documentation entry point
├── 01_meta/
├── 02_push-task/
│   └── config.json
├── 03_api/
│   ├── <artifact>-<version>.jar # at most one thin JAR
│   └── migrations/mysql/        # V00x__*.sql, append-only
├── 04_ui/
│   ├── app/                     # multi-config bundle: site.json + pages
│   ├── detail-<meaning>/        # one record-detail config per entity
│   ├── ip-statistics/
│   └── html-page/               # reserved: static HTML, no low-code validation
├── 05_dashboard/
│   ├── config.json
│   └── low-code/<config_index>_config/
├── 06_mcp/
│   └── config.json              # [] when the plugin registers no MCP server
├── 07_skill/
│   └── <skill-id>/              # skill.json + SKILL.md per skill
└── 08_menu/
    └── config.json
```

## index.json

Require `name`, `package_name`, `version`, `description`, `author`, and `icon`.

- `package_name` is a stable Java-package-style identity (e.g. `com.coolxer.plugin.<domain>`) and cannot change after release.
- `version` must be legal SemVer and an upgrade candidate must be strictly higher than the installed version.
- `icon` is a plugin-relative path (`"icon.png"`) or a platform-supported image Data URI; the referenced file must exist.
- Namespace every dashboard code, MCP code, skill ID, entity, table, topic, and config index with the package/business domain.

A plugin may additionally declare a UI contract:

```json
"ui_contract": {
  "schema_version": "1",
  "min_host_contract": "1.0.0",
  "renderer": "amis@6.7",
  "default_profile": "STANDARD"
}
```

When present, every listed low-code page must satisfy that contract (see Build UI).

## Build Meta Definitions

### Use the Meta contract

- Produce valid JSON with exactly the top-level arrays `entity`, `attribute`, and `operator`. Prefer one Meta file per plugin; when splitting (for example per source family), the backend recursively merges all Meta JSON files and rejects cross-file duplicates of IDs and names.
- Use snake_case identifiers matching `[A-Za-z_][A-Za-z0-9_]*`. Allow `table_name` and `column_name` one optional database qualifier, such as `zenvis.event`.
- Give every entity and attribute a stable positive integer `id`. Keep entity IDs globally unique and attribute IDs globally unique across the loaded Meta directory. Check existing plugins before allocating IDs.
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

- Prefer genuine business identifiers and business time in `order_by`. Do not use the high-cardinality generated `zenvis_id`, and do not partition by optional or vendor-controlled time. Stateful master data may use a project-specific engine such as `ReplacingMergeTree(<version_column>)`; follow the closest existing entity and keep upgrade compatibility.
- Optionally add a table-level row TTL when retention is specified:

```json
"ttl": { "column": "zenvis_insert_time", "expire_after": 90, "unit": "DAY" }
```

`column` must reference a non-nullable `Date`/`Date32`/`DateTime`/`DateTime64` physical column (the injected `zenvis_insert_time` is allowed); `expire_after` must be positive; `unit` is one of `HOUR`, `DAY`, `WEEK`, `MONTH`, `YEAR`. TTL changes trigger `MODIFY TTL` and can permanently delete history, so document the retention decision.

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

- Define every referenced operator in the top-level `operator` array even when the current backend can supplement defaults. Use only operators supported by the current query engine. The standard operator catalog is `equal`, `notequal`, `isnull`, `isnotnull`, `in`, `match`, `greatthan`, `greatequalthan`, `lessthan`, `lessequalthan`, `between` with positive integer IDs and Chinese labels.
- Use these normal sets as a starting point:
  - strings and JSON: `equal`, `notequal`, `isnull`, `isnotnull`, `in`, with `match` only when text matching is meaningful;
  - arrays: `equal`, `notequal`, `isnull`, `isnotnull`, `in`, `match`;
  - numbers: `equal`, `notequal`, `isnull`, `isnotnull`, `in`, `greatthan`, `greatequalthan`, `lessthan`, `lessequalthan`, `between`;
  - dates: `equal`, `notequal`, `isnull`, `isnotnull`, `greatthan`, `greatequalthan`, `lessthan`, `lessequalthan`, `between`.
- Set `copyable: true` on reusable IDs, IPs, domains, URLs, rule/task/command numbers, file paths, and hashes. Verify the copy control remains available when long values are visually truncated.
- Order each entity’s attributes by business importance. Put the best business identifier for the record first, followed by primary time, rule/task identity, source/destination, status, and detail payloads as appropriate.

### Configure links and record details

- Default to **one standalone record-detail config per entity** (`04_ui/detail-<meaning>/index.json`, config index `<package_name>.detail-<meaning>`). Put `link_template` on the first identifying attribute:

```json
"link_template": "/#/service/low-code-page/<package_name>.detail-event?record_id={zenvis_id}"
```

- The detail page reads with `GET /api/v1/entity/{entity}/{record_id}/view`. Use a business identifier as the API parameter only when explicitly required.
- Use a single shared detail page (for example `<package_name>.detail?entity=<entity_name>&record_id={zenvis_id}`) only when the business explicitly requires one shared config with strict entity isolation; it is not the default for new plugins.
- Use only string `link_template` values. Allow placeholders only in `{logical_attribute_name}` form and only for attributes in the same entity; `{zenvis_id}` is available because Zenvis injects it before validation.
- Use only relative URLs or absolute `http/https` URLs. Reject protocol-relative URLs and `javascript:`, `data:`, `blob:`, or `file:` targets.
- Let the backend decide which link dependencies are returned. It automatically adds `zenvis_id` as a hidden result field when a displayed link references it. The frontend does not require placeholders to be visible columns, but it still requires the value to exist in the returned row and the resolved URL to be safe.
- Optionally gate a link with `link_condition` (must accompany `link_template`):

```json
"link_condition": { "field": "status", "operator": "equal", "value": "success" }
```

`field` must be a same-entity logical attribute (including `zenvis_id`); supported operators are `equal`, `notequal`, `isnull`, `isnotnull`, `greatthan`, `greatequalthan`, `lessthan`, `lessequalthan`. When the condition is false the cell renders as plain text.

- Add IP statistics links to actual IP address fields when the plugin provides that page:

```json
"link_template": "/#/service/low-code-page/<package_name>.ip-statistics?ip={field}"
```

### Respect upgrade restrictions

Meta upgrades are additive-only: existing entities and attributes cannot be deleted or renamed, and entity IDs, table names, data source, engine, `order_by`, `partition_by`, attribute IDs, physical column names, and column types cannot change. New entities and attributes must be globally unique and SQL-safe. Design a new entity/table plus migration for genuinely breaking changes instead of overwriting old Meta.

### Validate Meta as a contract

- Parse every Meta JSON file and load it with the current backend validator when possible.
- Confirm entity count equals the structured-definition count and every source field maps to exactly one attribute.
- Confirm IDs and entity-local names are unique; all identifiers are legal; every attribute references an existing entity and every operator reference resolves.
- Confirm every `sort_column`, `order_by`, TTL column, and Vector output field resolves to the intended physical column.
- Confirm all partitions use `toYYYYMM(zenvis_insert_time)` and no plugin explicitly defines either reserved field.
- Confirm type, display type, description, copy behavior, ordering, mappings, operators, and links against the source document rather than only against generated JSON syntax.

## Build Vector Push Tasks

### Use the required architecture

Default to YAML (`.yaml`/`.yml`). Do not generate TOML or JSON unless explicitly requested or maintaining a proven legacy task; stable TOML tasks may stay as-is and should not be rewritten merely for format uniformity.

Create two services for a standard data pipeline:

1. Source-to-Kafka:
   - Follow the source mechanism declared by the specification, such as SFTP-landed files, UDP syslog, or an existing vendor Kafka topic.
   - Forward each raw record unchanged using Kafka `raw_message` encoding.
   - Do not parse, decode, rename, enrich, or drop business fields.
2. Kafka-to-ClickHouse:
   - Consume one topic or route per structured definition, using a dedicated consumer group per definition.
   - Split and map fields in the documented order.
   - Transform only what the data contract requires.
   - Write to the table linked by Meta; route deterministic failures to the plugin DLQ.

Additional side-band services are allowed when the specification requires them, and each is a separately registered task: command extraction from vendor logs, watched-IP side consumption that creates analysis tasks through the Zenvis API, or non-structured file archival to object storage. Do not create a demo push service unless the user explicitly requests one; if present, document that it is for demos only and how to disable it.

### Transform safely

- Treat VRL cast/parse functions (`to_string`, `to_int`, `to_float`, `to_bool`, `to_timestamp`, `parse_json`, `parse_regex`, `parse_timestamp`, and similar) as fallible whenever the argument's type cannot be statically verified — for example any event field from a dynamic source such as a syslog-decoded `.appname`. Always coalesce or handle the error: `appname = to_string(.appname) ?? ""` or `appname, err = to_string(.appname)`. Never write a bare `x = to_string(.field)`; it fails `vector validate` with `error[E103]: unhandled fallible assignment`, which then cascades into `error[E701]: undefined variable` for every later reference to that variable. Choose the `??` fallback (empty string, `0`, `{}`) so the downstream contract stays valid.
- Prefer `assert!`/`assert_eq!!` for contract violations (wrong field count, missing required field, bad enum) together with `drop_on_error: true` and `reroute_dropped: true`, and connect the `<transform>.dropped` stream to a dedicated Kafka DLQ sink. DLQ records retain the raw message plus `metadata.dropped.component_id`, `metadata.dropped.message`, and `metadata.dropped.reason`.
- Ensure each transform emits exactly the Meta attribute names for its entity — no missing fields, no extras.
- Validate the expected field count before indexing split arrays.
- Convert documented times with the specified timezone (normally `Asia/Shanghai`) and exact output format; normalize to `yyyy-MM-dd HH:mm:ss` for `DateTime` sinks.
- Parse arrays only where the document declares repeated values.
- Convert numeric and boolean fields deliberately; do not silently turn all invalid values into `0` or `false`. Contract violations belong in the DLQ, not in defaulted rows.
- Decode Base64 only when the description explicitly says the decoded content is JSON or readable text, URL, email content, or another named string form.
- Keep opaque Base64 payloads, packets, rule fragments, and responses encoded unless the document explicitly requires decoding.
- On readable-string decode failure, preserve the original value (or follow the documented DLQ contract).
- For explicit JSON, decode if required, parse JSON, and preserve traceable raw content on parse failure according to project convention.
- Use environment variables for Kafka, ClickHouse, credentials, source paths, polling, and tunables with the `${VAR:-default}` form. Give non-sensitive settings sane defaults; never commit real passwords, tokens, or customer addresses as defaults.
- Configure acknowledgements, retries, disk buffers, batching, and health checks in proportion to delivery requirements.
- Make historical-file age limits, processed-file markers, and checkpoint behavior explicit when delayed delivery or backfill is possible.
- For non-structured attachments (PCAP, tar.gz samples), use an `exec`-triggered upload to object storage (for example MinIO via `curl --aws-sigv4`) that keeps files byte-identical, organizes objects by type/date, and moves processed files aside; do not pipe binary content through Vector events.

### Configure sinks like the deployed contract

ClickHouse sinks use the platform deployment convention: `endpoint`/`database`/`table`, `format: json_each_row`, `compression: gzip`, `date_time_best_effort: true`, `skip_unknown_fields: false`, basic auth from environment, disk `buffer` with `when_full: block`, bounded `request` retries/timeouts, and `healthcheck.enabled: true`. DLQ Kafka sinks use JSON encoding, zstd compression, acks, retries, and disk buffers. Keep topic, group, and DLQ names configurable via environment variables.

### Add embedded Vector tests

Add a `tests:` block to Kafka-to-ClickHouse YAML covering at least: one valid record asserting mapped outputs, each documented failure mode asserting delivery to `<transform>.dropped`, and tricky conversions (timezones, float edge values, optional regex groups). Run them under the production Vector version or container alongside `vector validate`; a config that has never passed validation must not be handed off.

Register every YAML/TOML/JSON file in `02_push-task/config.json` with unique, descriptive `name` and `description` fields. The `name` preserves the task’s enable/disable state on upgrade, so never casually rename it.

## Build Dynamic API and MySQL Migrations

Use `03_api` only when the plugin needs server-side behavior: external-system adapters, deterministic aggregation, work-order flows, or MCP tools backed by Java.

### Source layout and build coordinates

Two source layouts exist; the current `zenvis-plugin/zenvis-plugin-*` repositories use the second:

- In-repo `plugin-name/api-src/` Maven module (preferred for new root-managed plugins, excluded from the archive).
- Sibling `extend-<name>/` independent Maven project (current convention in these repositories), wired via `build.conf`; only the built JAR ships in `03_api`.

Project rules:

- Parent `org.springframework.boot:spring-boot-starter-parent:3.2.0`, Java 17, `groupId: com.coolxer.plugin`, `artifactId: extend-<name>`.
- The POM version must equal `plugin-name/index.json` version exactly; the build script enforces this.
- All runtime dependencies are `provided` (Spring Web/Context/JDBC/Transaction, Jackson, Jakarta Validation, MCP SDK) so the artifact is a thin JAR. Test-only dependencies (JUnit, H2, ClickHouse JDBC) use `test` scope.
- No Spring Boot application class and no repackaged fat JAR: the published JAR must not contain `BOOT-INF/` and must contain `com/coolxer/plugin/**/*.class`.

### Class loading, routes, and data access

- The backend creates one ClassLoader per plugin and scans `com.coolxer.plugin` for `@RestController`, `@Service`, `@Repository`, and `@Component`. Place all business classes under that root (sub-packages such as `com.coolxer.plugin.<domain>.controller` are fine); the Java package does not have to literally equal `package_name`.
- Controller routes are automatically prefixed with `/api/v1/plugin/{package_name}`. Declare only plugin-relative paths (`@RequestMapping("/tickets")`), never the prefix or another plugin’s namespace. The browser-facing base includes the context path: `/zenvis/api/v1/plugin/{package_name}/...`.
- Do not depend on backend internal entities, repositories, services, DTOs, or utilities. Use only platform-stable beans and the lightweight `api-common` models (`ResponseWrap`, `PageRows`, `PageQuery`).
- For MySQL, inject `@Qualifier("pluginMysqlJdbcTemplate") NamedParameterJdbcTemplate` and annotate multi-write transactions with `@Transactional("pluginMysqlTransactionManager")`. Never create a private connection pool or rely on core JPA scanning.
- The authenticated user is available as request attributes `zenvis.authenticated.userId` and `zenvis.authenticated.userName`. Do authorization against the user ID; do not parse sessions, cookies, or tokens yourself.
- Validate inputs (length, enum, format), parameterize all SQL, strip stack traces/SQL/credentials from errors, and bound every outbound HTTP call with connect/request timeouts and limited retries. Keep secrets out of logs and responses.

### Exposing MCP from the extension JAR

When the extension provides agent tools, expose SSE in-process with the MCP SDK: define constants `PLUGIN_API_PREFIX = "/api/v1/plugin/{package_name}"`, `MCP_SSE_ENDPOINT = prefix + "/mcp/sse"`, `MCP_MESSAGE_ENDPOINT = prefix + "/mcp/message"`, build a `WebMvcSseServerTransportProvider` and `McpServer.sync(...)`, register tools with explicit JSON Schemas and `ToolAnnotations` (read-only vs write), and delegate both endpoints to the transport router. Register the loopback connection through `06_mcp/config.json` (see Register MCP Connections). Write tools default to `ASK` approval — document this and never design a skill that assumes auto-approval.

### MySQL migrations

Put versioned SQL at `03_api/migrations/mysql/V<version>__<description>.sql` (for example `V001__create_btw_work_order.sql`; versions may be multi-segment such as `V1.2`; descriptions use only letters, digits, underscore, hyphen). Name plugin tables with a stable `t_<plugin>_<entity>` convention.

Rules:

- Migrations run in numeric order and are tracked in `t_sys_plugin_migration` with a SHA-256 checksum.
- No duplicate versions; executed files must be retained verbatim (name, description, checksum immutable); new versions must be higher.
- Always forward and idempotent (`CREATE TABLE IF NOT EXISTS`); never rolled back by uninstall or failed upgrade. Design every migration so the previous plugin version keeps running after it.
- ClickHouse analytics tables are managed by Meta `auto_create`, never by MySQL migrations.

### Test the extension

Run `mvn clean test` and cover: repository parameter binding/pagination, service transactions and failure compensation, controller validation/wrapping/path prefix, migration empty-db run and skip-already-applied, and packaging (single JAR, classes present, no `BOOT-INF`). Add a package contract test (for example `*PluginPackageContractTest`) that reads the shipped plugin files and asserts cross-file consistency: `index.json` version equals POM and `skill.json` versions, MCP codes match skill tool scopes, Meta type/input hints are consistent, and `site.json` references every required page.

## Build UI and IP Investigation

### Prefer the multi-config directory layout

Each first-level subdirectory of `04_ui/` (except reserved `html-page`) is one independent config with index `<package_name>.<dir>`:

| Directory | Requires | Config index example |
| --- | --- | --- |
| `app/` | `site.json` (bundle shell, may contain pages) | `com.example.plugin.app` |
| `detail-event/` | `index.json` | `com.example.plugin.detail-event` |
| `ip-statistics/` | `index.json` | `com.example.plugin.ip-statistics` |

Subdirectory names are safe logical names without the `_config` suffix. The legacy flat layout (files directly in `04_ui/`, installed to `<package_name>_config`) remains installable but is not the default for new plugins.

### Low-code application shell

`app/site.json` must be the app envelope; every navigation entry needs `url`, optional `icon` (for example Font Awesome classes), optional nested `children`, and `schemaApi`:

```json
{
  "status": 0,
  "msg": "",
  "data": {
    "pages": [
      {
        "label": "事件列表",
        "url": "/events",
        "icon": "fa-solid fa-table-list",
        "schemaApi": "get:/zenvis/api/v1/config/com.example.plugin.app/get?file_name=event-list.json"
      }
    ]
  }
}
```

Include a navigation entry for every structured entity’s list page, plus overview/work-order/IP pages as applicable.

### List pages (AMIS CRUD)

Create one list page per structured entity. Use the entity logical name in the standard list API — never a table name or merged endpoint:

```json
{
  "type": "page",
  "title": "事件列表",
  "body": [
    {
      "type": "crud",
      "api": "/zenvis/api/v1/entity/example_event/list",
      "autoGenerateFilter": true,
      "defaultParams": { "orderBy": "event_time", "orderDir": "desc" },
      "affixHeader": true,
      "columnsTogglable": "auto",
      "placeholder": "暂无事件数据",
      "columns": []
    }
  ]
}
```

Column conventions observed in shipped plugins:

- `searchable: true` for filterable text; use an object for time ranges: `"searchable": { "type": "input-datetime-range", "name": "found_time" }`; `sortable: true` on business times.
- `copyable: true` on IDs, IPs, hashes, URLs, and paths.
- Hide payload/JSON/rare columns by default with `"toggled": false`.
- Link columns use `type: "link"` with `href`, `"blank": true`, and a `tpl` body; URL-encode interpolated values:

```json
{
  "name": "src_ip",
  "label": "源IP",
  "copyable": true,
  "type": "link",
  "href": "/#/service/low-code-page/com.example.plugin.ip-statistics?ip=${src_ip|url_encode}",
  "blank": true,
  "body": { "type": "tpl", "tpl": "${src_ip}" }
}
```

- Render JSON cells with a truncated `tpl` plus a `popOver` containing a `json` component.

Keep API entity names aligned with Meta; do not use a combined endpoint as a shortcut.

### Record detail and IP investigation

- Build `detail-<meaning>/index.json` per entity, querying `GET /zenvis/api/v1/entity/{entity}/{record_id}/view` and keying off the `record_id` query parameter.
- A cross-entity IP investigation page (`04_ui/ip-statistics/index.json`) should: provide an IP input with optional `ip` query parameter; match source/destination IP fields with OR semantics within each entity so a row counts once; return zero (or explicit N/A) for entities without IP fields while retaining them in the overview when required; pass multi-entity parameters as a comma-separated scalar (avoid bracket notation such as `entities[0]` that Tomcat may reject); and link actual IP attributes to this page.
- Detail and IP pages usually have no menu entry; they are reached through Meta/column links.

### Static HTML directory

`04_ui/html-page/` is reserved and excluded from low-code bundle validation (no `site.json`/`index.json` required). The installer copies the whole directory, including multi-level static sites, to `html-page/<package_name>/` during UI installation independently of dashboard config.

### Plugin UI Contract

When `index.json.ui_contract` is declared (current version: schema `1`, renderer `amis@6.7`, profile `STANDARD`), the listed pages are strict contract resources and extension tests validate them recursively:

- Root pages use host semantic classes (`zv-page`, `zv-workspace`, `zv-crud-workspace`, `zv-summary-bar`, `zv-summary-metric`, `zv-summary-copy`, `zv-metric-card`, `zv-table-shell`, `zv-truncate`, `zv-wrap`).
- No plugin-private class prefixes, no hex/RGB theme colors, and no inline `color`/`background`/`box-shadow`/`border-radius`; use native AMIS components such as `alert`/`tag` for states.
- Every grid node with `md` also declares `sm` and `xs`; use `zv-truncate` for long identifiers and `zv-wrap` for body text.
- Ship a `04_ui/UI_CONTRACT.md` describing the strict resources when the plugin opts in.

## Configure Menus

`08_menu/config.json` is an array of first-level menu entries. Menus are created last during install and are **not** granted to any role automatically; role assignment is a post-install step.

| Type | `params` | Matching UI entry |
| --- | --- | --- |
| `LOW_CODE_APP` | App config index, e.g. `com.example.plugin.app` | Requires `04_ui/app/site.json` with `data.pages` |
| `LOW_CODE_PAGE` | Page config index (package for root file, `<package>.<subdir>` for bundles) | Standalone `index.json` only |
| `HTML_PAGE` | Full deployment path `/zenvis/html-page/<package>/<relative-path>` | File under `04_ui/html-page/` |
| `EXTERNAL_APP` | Controlled external `http(s)` address | External site |
| `POLICY_CONFIG` | Policy/config index (for example a device-policy key or `<package>`) | Installs a policy configuration entry |
| `BUILT_APP` | Configured platform route | Built-in platform page, normally not for plugins |

Observed patterns: data apps ship a pair of `POLICY_CONFIG` + `LOW_CODE_APP` entries with the same app index; plugins with an opted-in UI contract may add `"ui_profile": "STANDARD"`; pure tool plugins (RzWeb) ship a single `HTML_PAGE` entry and no dashboard.

The `08_menu` type and the `04_ui` layout must match, otherwise the frontend iframe crashes at render time:

- `LOW_CODE_APP` renders through `/amis/app.html`, which fetches `file_name=site.json`. When that file is missing the backend returns an error payload without `data`, amis throws `Cannot read properties of undefined (reading 'pages')`, and the page stays broken.
- `LOW_CODE_PAGE` renders through `/amis/page.html`, which fetches `file_name=index.json` directly; no `site.json` is needed.
- Never declare `LOW_CODE_APP` when only a standalone `index.json` exists. Either change the menu to `LOW_CODE_PAGE`, or add a `site.json` whose `data.pages` navigation references every page via `schemaApi`.
- `HTML_PAGE` `params` must include the `/zenvis` context prefix. The html-page view Base64-decodes `params` and sets it directly as the iframe `src` via `new URL(candidate, window.location.origin)` — it does not call `withBaseUrl`. A params value of `/html-page/...` would resolve to `<origin>/html-page/...` and 404. Always use `/zenvis/html-page/<package_name>/<relative-path>`.
- Never construct protocol-relative, `javascript:`, `data:`, `blob:`, or `file:` URLs.

Validate the low-code schema shape expected by the current SDK, distinguishing an app envelope (`data.pages`) from a standalone page schema.

## Build Dashboards

`05_dashboard/config.json` is a JSON array compatible with the current `DashboardDto`; use `[]` for plugins without dashboards. Supported types:

| Type | Key fields | Resource location |
| --- | --- | --- |
| `HTML_PAGE` | `code`, `html_path` | File under `04_ui/html-page/` |
| `LOW_CODE_PAGE` | `code`, `config_index` | `05_dashboard/low-code/<config_index>_config/` |
| `LINK` | `code`, `url` | Controlled external link |
| `BUILT` | platform built-in | Normally not created by plugins |

For `HTML_PAGE`, make `html_path` plugin-relative to `04_ui/html-page/` only — multi-level static sites are allowed:

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

- Place that file at `04_ui/html-page/security-overview.html`. Do not put `/html-page/`, `/zenvis/html-page/`, the package name, or a deployment URL in `html_path`.
- Do not use the legacy `05_dashboard/html-page/` location for new work; the installer only keeps it for backward compatibility.
- The installer copies the entire `04_ui/html-page/` directory regardless of dashboard entries; `html_path` only registers the dashboard record and normalizes the stored path.
- For pure static-HTML tool plugins, leave the dashboard config as `[]` and point an `08_menu` `HTML_PAGE` entry directly at `/zenvis/html-page/<package_name>/<relative-path>`.
- Dashboard `code` must be globally unique, package-namespaced, and stable on upgrade; an upgrade cannot delete the current default dashboard.

For the HTML page:

- Use actual Zenvis APIs and current login-session behavior; do not present demo values as real data.
- Keep CSS and JavaScript self-contained unless external assets are explicitly allowed.
- Include loading, empty, error, last-updated, manual refresh, and automatic refresh (with interval/pause indication) states.
- Design for at least 16:9 full-screen use and verify a representative viewport such as 1920×1080.
- Use security-domain metrics, not only generic record totals, when supported: event severity, top IPs, protocol/port distribution, device freshness, command failure rate, rule status, and sample trends.
- Do not label historical presence as pipeline “health”. Use recent insert time, service heartbeat, Kafka lag, or sink status for health claims.
- Avoid browser-dependent parsing of non-standard date strings.
- Resolve the API base from the current deployment convention and handle unauthenticated/error responses explicitly; never embed credentials in HTML, JSON, or browser logs.

## Register MCP Connections

`06_mcp/config.json` is an array of MCP server connection descriptors; use `[]` when the plugin registers none.

```json
[
  {
    "code": "example",
    "name": "示例分析 MCP",
    "description": "由示例扩展 Jar 提供的只读查询与幂等提交工具",
    "base_url": "http://127.0.0.1:${server.port}",
    "sse_endpoint": "/api/v1/plugin/com.example.plugin/mcp/sse",
    "headers": "{\"Authorization\":\"Bearer ${app.security.api.bearer-token}\"}",
    "enabled": true,
    "request_timeout_seconds": 30,
    "connect_timeout_seconds": 10
  }
]
```

- `code` is a globally stable identity (letters, digits, dot, underscore, hyphen; max 64 chars), unique within the plugin; `name`/`description` explain purpose, data scope, and risk.
- Use the loopback form above when the descriptor targets the plugin’s own `03_api` MCP endpoints. For genuinely external servers, use environment placeholders such as `${EXAMPLE_MCP_BASE_URL}` and default `enabled` to false for administrator setup.
- Never commit real tokens; `headers` is a JSON string using placeholders. README explains variable names and how to obtain credentials.
- On upgrade, administrator-modified values are preserved; fields still equal to old defaults adopt new defaults. Deleting a code removes that plugin-sourced connection.
- Registration never bypasses permissions: tool visibility requires the global MCP switch, agent scope, the skill’s server/tool allowlist, and the tool’s `ALLOW`/`ASK`/`DENY` policy. Do not duplicate external tool schemas in docs beyond what stays stable.

## Ship Runtime Skills (07_skill)

A plugin may install one or more DIH runtime skills, each in its own subdirectory:

```text
07_skill/
└── example-investigation/
    ├── skill.json
    ├── SKILL.md
    └── agents/           # optional client presentation
        └── openai.yaml
```

This research-side skill (`agent-skills/create-zenvis-plugin`) is never installed via `07_skill`; do not confuse the two.

### skill.json

```json
{
  "id": "example-investigation",
  "name": "示例事件研判",
  "description": "在受控工具范围内分析示例事件并输出证据化结论",
  "version": "1.0.0",
  "author": "Example Team",
  "agentTypes": ["agent_skill"],
  "tags": ["example", "investigation"],
  "chat": {
    "enabled": true,
    "label": "示例研判",
    "icon": "data-analysis",
    "order": 100,
    "agentType": "agent_skill",
    "prologue": "我可以在受控工具范围内分析示例事件。",
    "promptSuggestions": [{ "label": "分析事件", "prompt": "请分析事件 ID：" }]
  },
  "runtime": {
    "promptMode": "skill_only",
    "tools": {
      "local": { "retrieval": ["retrieval_search"] },
      "mcp": { "example": ["lookup_indicator"] }
    },
    "limits": {
      "maxToolCalls": 12,
      "maxRepeatedFailures": 2,
      "maxToolResultChars": 12000,
      "maxAccumulatedToolResultChars": 48000
    }
  },
  "enabled": true,
  "entry": "SKILL.md"
}
```

Rules:

- `id` starts with a letter/digit, may contain dots/underscores/hyphens (max 128), and is globally stable. New tool-using skills must declare `agentTypes` explicitly (omission falls back to legacy ambiguous behavior).
- `chat.enabled=true` plus `label/icon/order/agentType/prologue/promptSuggestions` creates the DIH entry; `enabled` alone does not.
- `runtime.tools.local` maps a built-in service code (for example `retrieval`) to original tool names; `"*"` alone selects all tools of one service. `runtime.tools.mcp` maps MCP server code to that server’s current tool names. Legacy array forms, unknown services/tools, and mixing `"*"` with explicit names fail skill loading.
- With no `runtime.tools` declared the platform is fail-closed: the model gets no tools. Availability still depends on global switch, agent scope, server health, discovered tool names, and approval policy. Multiple selected skills union allowlists and take the strictest numeric limits. Positive integers only; `maxAccumulatedToolResultTokens` is also supported.
- Keep `version` aligned with `index.json`; enforce it with the extension’s package contract test when one exists.

### SKILL.md content

The prompt entry must define: goal and trigger scenarios; accepted inputs, required context, and rejection conditions; fixed steps and optional branches; the exact allowlist and per-tool usage conditions; evidence/output format and completion criteria; approval requirements for risky actions; and behavior for no-data, tool failure, invisible optional MCP, and budget exhaustion. Never claim allowlist-external tools and never coach bypassing approvals. When the skill emits structured cards or JSON, state the exact fenced format, required fields, and rules against fabricating IDs, times, or tool results.

## Create the Plugin Icon

Every plugin must ship an `icon.png` referenced by `index.json` (`"icon": "icon.png"`). The icon appears in the plugin management list and alongside menu entries, so it must remain legible at small sizes.

- **Format and size:** 100×100 PNG. Match the existing plugin icons (RGB or RGBA, 8-bit).
- **Content:** The icon must visually reflect the plugin's domain and purpose — do not reuse a generic placeholder. For example:
  - A security/RE tool → magnifying glass over hex digits, shield, or circuit traces.
  - A network/asset plugin → network nodes, device icons, or topology.
  - A data/analytics plugin → charts, tables, or data streams.
- **Generation workflow:**
  1. Use the available AI image generation tool (e.g. `GenerateImage`) with `image_size: "square"` and a prompt formatted as `"Plugin application icon for <domain>: <detailed visual description>, <style>, no text, suitable for a small software icon"`.
  2. AI-generated images usually carry a watermark in the bottom-right corner. Crop it out before resizing — e.g. with `sips -c 1820 1820 raw.jpg --out cropped.jpg` (centered crop) on macOS.
  3. Resize to 100×100 PNG: `sips -z 100 100 -s format png cropped.jpg --out icon.png`.
  4. Delete the intermediate raw/cropped files.
- **Verification:** Open the final `icon.png` and confirm the subject is still recognizable at 100×100 and no watermark remains.

## Document and Version

- **`00_doc/README.md` is required.** It introduces the plugin, lists the directory’s documents, and summarizes the data model (entity/table/topic mapping), push-task architecture including DLQ/replay/retention, UI pages, dashboards, menus, MCP/skills, environment variables and credential injection, install/upgrade ordering, and uninstall impact. Use the same language as the plugin's user-facing labels.
- Root `README.md` targets installers/operators: scope and unsupported scenarios; data source → entity/table/topic/page/menu mapping; env vars, timezone, encoding, network dependencies; DLQ/replay/retention; install order, upgrade limits, validation results, and known issues. Keep it describing the shipped package.
- `00_doc/` is ingested by RAG when available: put complete data dictionaries, protocols, error codes, compatibility notes, and operational runbooks there. Never place passwords, tokens, customer addresses, PCAPs, archives, raw logs, or large binaries in it.
- Follow proven companion-doc patterns when useful: a contract-matrix and operations doc (`契约矩阵与运维.md`), ClickHouse DDL reference (`数据库建表.md`), type mapping notes, dashboard configuration notes, and test-data generation scripts with manifest output.
- Copy or reference the authoritative source specification in `00_doc/` when permitted; update it when the user adds a new definition.
- Maintain a changelog: either `CHANGELOG.md` or an “升级说明” README section, stating whether each release changes Meta, tables, migrations, tasks, or only UI/docs.
- Keep README claims synchronized with actual Meta, Vector, UI, dashboard, MCP, skill, and menu files.
- Bump the plugin version for material distributable changes unless the user explicitly limits the request to a single file. Version linkage must be exact across `index.json`, the extension POM and built JAR name, every shipped `skill.json`, and the archive name.
- Do not modify version, documentation, platform code, or archives when the user explicitly says to change only one configuration file.

## Build and Package

Each `zenvis-plugin-*` repository owns its build via:

- `build.conf` declaring `PLUGIN_DIR` (for example `plugin-btw`), and optionally `API_DIR` (`extend-btw`) plus `API_ARTIFACT` (`extend-btw`).
- `build.sh` (macOS/Linux; prefers `gtar`) and `build.ps1` (Windows), behaviorally equivalent.

The script:

1. Reads version and package name from `index.json`; when `API_DIR` is set, builds Maven (`mvn clean package`), enforces POM version == plugin version, verifies the artifact is a thin JAR (no `BOOT-INF`, contains `com.coolxer.plugin` classes), deletes old JARs, and copies the new one into `03_api/`.
2. Enforces at most one `*.jar` at the `03_api` root.
3. Produces `<package_name with dots replaced by dashes>.tar.gz` at the repository root, with entries at the archive root (no extra top-level directory, no `./` entries), excluding `.DS_Store`, `.git`, `api-src`, `target`, `build.log`, and `*.tar`/`*.tar.gz`.

Build only inside the target plugin work tree and treat the generated JAR/archive as build outputs. When a plugin is released through the marketplace, also register it in `zenvis-plugin/market-plugins.json` with a unique numeric `id`, `name`, `package_name`, `version`, `description`, `author`, an embedded PNG Data URI `icon`, `download_url`, and `detail_url`.

## Validate Before Handoff

Run validation proportional to the change, organized in layers:

1. **Static contract**
   - Parse every JSON file (including skill.json, site.json, all config.json files).
   - Parse YAML/TOML and run `vector validate` (including embedded `tests:`) using the production Vector version or container when available. Every remap VRL block must compile.
   - Confirm entity count equals the structured-definition count.
   - Confirm entity names, tables, Kafka topics, transforms, sinks, UI pages, dashboard entries, MCP codes, skill IDs, and menu params are aligned and collision-free.
   - Confirm every `08_menu` entry matches its `04_ui` entry file (`LOW_CODE_APP` → `site.json` shell; `LOW_CODE_PAGE` → standalone `index.json`; `HTML_PAGE` → `/zenvis/html-page/<package>/...`).
   - Compare every transform output field with the corresponding Meta attributes; report missing and extra fields; confirm every `.dropped` stream has a DLQ sink.
   - Run the complete “Validate Meta as a contract” checklist: first-attribute detail links, IP links, `link_condition` references, copy flags, TTL columns, and reserved-field exclusion.
2. **API and migrations**
   - Run `mvn clean test` for the extension, including package contract tests; confirm a single thin JAR with classes and no `BOOT-INF`.
   - Confirm controller paths resolve under `/api/v1/plugin/{package}` and MySQL migrations apply on an empty database and skip already-applied versions.
3. **Ingestion pipeline**
   - Generate representative data for every definition and exercise source → Kafka → ClickHouse when infrastructure is available; confirm bad field counts/times/types land in the DLQ, not the table.
4. **Pages, dashboards, and agents**
   - Open app shell, every list, detail, and IP page; verify links and copy controls.
   - Compile inline dashboard JavaScript and perform browser visual QA (1920×1080) for material dashboard changes, including loading/empty/error/refresh states.
   - Confirm MCP connects and discovers the expected tools, and each 07_skill allowlist resolves to existing local/MCP tool names with correct budgets.
   - For opted-in UI contracts, run the extension’s strict-class/style contract checks.
5. **Archive and release**
   - Remove packaging noise such as `.DS_Store`; build with the repository’s build script.
   - Inspect the archive itself (`tar -tzf <archive>.tar.gz`), not only the source tree:
     - `index.json` at the archive root with correct package/version;
     - latest Meta, push tasks, UI, dashboard, MCP, skill, and menu configs;
     - `00_doc/README.md`, `icon.png` (100×100, matching `index.json`);
     - at most one thin JAR plus complete migrations;
     - static HTML and documentation present;
     - no `api-src`/`target`/`.git`/logs/old archives, no stale configuration, no secrets.
   - Verify the version linkage (`index.json` ↔ POM/JAR ↔ skill.json ↔ archive) and, when applicable, the `market-plugins.json` entry.
6. Run targeted backend/frontend tests for any platform changes.

If an official validator is unavailable, state that limitation and identify the exact validation still required in the target environment.

## Handoff

Report:

- plugin path, package name, and archive path;
- plugin version and the full version-linkage result;
- archetype and the capability directories actually shipped;
- entity and table counts;
- push-task architecture, DLQ topics, and Vector validation/test outcomes;
- dynamic API: JAR, endpoints, MCP tools, and MySQL migrations;
- UI, record-detail, IP investigation, dashboard, menu, and role-assignment notes;
- MCP connection codes and runtime skills (IDs, tool scopes, budgets);
- documentation, changelog, and marketplace registration status;
- validation commands and outcomes;
- unresolved compatibility, upgrade, or deployment risks (including that uninstall drops Meta-owned ClickHouse tables but never rolls back MySQL).

Lead with confirmed outcomes. Do not claim live integration success when only static validation was performed.
