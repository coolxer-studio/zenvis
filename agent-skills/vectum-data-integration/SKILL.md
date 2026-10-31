---
name: vectum-data-integration
description: Generate, validate, deploy, monitor, and repair Vectum MCP data push tasks backed by Vector. Use when Codex must turn a data integration request into a strict Vector configuration, create or update the task through Vectum MCP tools, start it, inspect status/logs, iterate on configuration errors, and report overall task health.
---

# Vectum MCP Data Push

## Overview

Use this skill to operate Vectum as an MCP-driven Vector pipeline manager. The expected outcome is a valid Vector config deployed as a Vectum task, running normally or stopped with a clear external blocker.

Default to YAML because Vector recommends YAML. Always pass the format explicitly as `yaml`, `toml`, or `json`; Vectum does not infer it for MCP create, update, or validate calls. Do not invent Vector fields; verify unfamiliar components with official Vector docs or `scripts/validate_vector_config.sh`.

## Workflow

1. Extract the task contract: task name, source, sink, input format, parsing/transforms, filters, field mapping, credentials, endpoints, expected volume, and success signal.
2. Build a Vector topology with at least one `source` and one `sink`. Every `inputs` entry must reference an existing upstream source or transform.
3. Validate locally when possible: save the config to a temp file and run `scripts/validate_vector_config.sh <file>`. If `vector` is unavailable, state that local prevalidation was skipped and rely on Vectum runtime logs.
4. Deploy through MCP when the Vectum MCP tools are available:
   - `validateConfig(config, format, testsRequired)`
   - `listTasks(source, mark, nameContains, status, offset, limit)` to find an existing task
   - `getTask(id)` for the complete config and current `runId`
   - `createTask(name, description, config, format)` or `updateTask(id, name, description, config, format)`
   - `startTask(id)` / `stopTask(id)` / `restartTask(id)`
   - `diagnoseTask(id)` and `getTaskLog(id, logType)` after every start.
5. Repair configuration errors from diagnostics and repeat validate/update/restart/diagnose. Use at most 5 automatic repair rounds and stop when the same failure fingerprint repeats twice.
6. Stop automatic repair when the failure is external: missing secrets, DNS/network unreachable, authentication denied, target service unavailable, permission denied, or a path/volume missing in the runtime environment.
7. Finish with task ID, status, config summary, validation outcome, repair rounds, and any remaining action needed.

## References

Load only the relevant reference:

- `references/vector-config-workflow.md` for requirement extraction, topology rules, validation modes, and log-driven repair.
- `references/vectum-mcp-runbook.md` for exact Vectum MCP tool behavior and the deployment/repair loop.
- `references/vector-component-patterns.md` for common starter templates and links to official Vector component docs.

## Vectum-Specific Rules

- Prefer MCP over REST for task operations when MCP tools are available.
- Pass the complete `name`, `description`, `config`, and explicit `format` to `updateTask`; ownership fields are preserved by the backend.
- Use task ID for lifecycle and deletion. Do not infer a target from a mark and do not request batch deletion.
- Treat `console` logs as Vector stdout and `system` logs as Vector stderr.
- Consider success only when `diagnoseTask` reports `running`, the current `runId` has its startup marker, and that run contains no `ERROR/FATAL/PANIC`.
- Do not claim end-to-end success from config validation alone; success requires Vectum task status/log checks after startup.
