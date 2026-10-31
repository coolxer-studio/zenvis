# Vectum MCP Runbook

## MCP Tools

Vectum exposes these tools through `McpTaskTools`:

- `listTasks(source, mark, nameContains, status, offset, limit)` returns a paged summary without full config. `limit` is capped at 100.
- `getTask(id)` returns one task by ID, including complete config, format, status, and current `runId`.
- `createTask(name, description, config, format)` creates a task with source `MCP`; format is required.
- `updateTask(id, name, description, config, format)` updates the task while preserving source and mark; format is required.
- `deleteTask(id)` deletes exactly one task and removes its Vector process files.
- `startTask(id)` starts a stopped or crashed task; it is idempotent while running.
- `stopTask(id)` is idempotent while stopped and keeps the last `runId`.
- `restartTask(id)` always starts a new run and produces a new `runId`.
- `getTaskLog(id, logType)` returns plain text logs; `logType` is `console` or `system`.
- `diagnoseTask(id)` analyzes only the current `runId` and returns structured diagnostics.
- `validateConfig(config, format, testsRequired)` runs isolated Vector validation.

The removed tools are `getTasks`, `toggleTask`, and `batchDeleteTasks`; there are no aliases.

The MCP server is normally at `/sse` and uses the same bearer token as REST when auth is enabled.

## Create And Start

1. Call `validateConfig(config, format, testsRequired)` and stop on `passed=false` or `blocked=true`.
2. Call `listTasks` with a stable mark before creating when idempotency matters; call `getTask(id)` to compare the complete config and format.
3. Call `createTask(name, description, config, format)`.
4. Parse the returned JSON. Stop on an explicit exception, `success:false`, or a missing task ID.
5. Call `startTask(id)`, then `getTask(id)` and `diagnoseTask(id)`.
6. Success requires `running`, a current `runId`, a matching startup marker, and no fresh `ERROR/FATAL/PANIC`.

## Repair Loop

Use this loop for at most 5 rounds:

1. Collect `getTask(id)` and `diagnoseTask(id)`; use `getTaskLog` only when raw surrounding lines are needed.
2. Classify the failure:
   - Config repair: unknown field, missing field, invalid input, invalid VRL, unsupported type/codec.
   - External blocker: connection/auth/path/permission/target health.
3. For config repair, edit the complete config, call `validateConfig(config, format, testsRequired)`, then call `updateTask(id, name, description, config, format)` and `restartTask(id)`.
4. Recheck `getTask(id)` identity and `diagnoseTask(id)` for the new `runId`.
5. Stop when the same failure fingerprint appears twice or after 5 rounds.

Stop as successful only on `running` with no relevant new `system` error. Stop as blocked when the error needs external action.

## Status Summary

For "overall task status":

1. Call `listTasks` with the required filters and page through results.
2. Count statuses: `running`, `running[error]`, `error`, `stopped`, `created`, and unknown.
3. For each non-running task, include ID, name, status, source, and a short action.
4. For `running[error]` or `error`, fetch `system` logs and quote only a short summary of the newest error line.

## Vectum Implementation Notes

- Task config is stored as `push.yaml`, `push.toml`, or `push.json` under the task workspace.
- Vectum starts Vector with `{vector.home}/bin/vector -c {task.workspace}/{taskId}/push.{ext}`.
- `console` maps to `info.log`; `system` maps to `error.log`.
- `createTask` and `updateTask` do not accept Lua files through MCP in the current project implementation.
- `createTask` and `updateTask` require explicit format. Vectum writes `push.yaml`, `push.toml`, or `push.json` and removes files with the previous extension.
- `updateTask` preserves source and mark; callers cannot change task ownership through update.
