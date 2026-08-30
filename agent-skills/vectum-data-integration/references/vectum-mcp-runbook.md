# Vectum MCP Runbook

## MCP Tools

Vectum exposes these tools through `McpTaskTools`:

- `getTasks()` returns all task metadata and runtime status as JSON.
- `getTask(id)` returns one task by string ID.
- `createTask(name, description, config)` creates a task with source `MCP`.
- `updateTask(id, name, description, config)` updates the task.
- `deleteTask(id)` deletes a task and removes its Vector process files.
- `toggleTask(id)` starts a stopped task or stops a running task.
- `getTaskLog(id, logType)` returns plain text logs; `logType` is `console` or `system`.
- `batchDeleteTasks(ids)` deletes comma-separated task IDs.

The MCP server is normally at `/sse` and uses the same bearer token as REST when auth is enabled.

## Create And Start

1. Call `createTask(name, description, config)`.
2. Parse the returned JSON. Stop if it has `success:false` or no task ID.
3. Call `toggleTask(id)` to start the task. A new task normally starts from `created`/`stopped`.
4. Call `getTask(id)` and inspect `status`.
5. If status is `running`, fetch `system` logs once to confirm there are no fresh errors.

## Repair Loop

Use this loop for at most 5 rounds:

1. Collect `getTask(id)`, `getTaskLog(id, "system")`, and `getTaskLog(id, "console")`.
2. Classify the failure:
   - Config repair: unknown field, missing field, invalid input, invalid VRL, unsupported type/codec.
   - External blocker: connection/auth/path/permission/target health.
3. For config repair, edit the config, run local validation if possible, then call `updateTask(id, name, description, config)`.
4. Ensure the task is stopped before starting when status is `running[error]` or `error`; `toggleTask(id)` may stop a running process, then call it again to start.
5. Recheck status and logs.

Stop as successful only on `running` with no relevant new `system` error. Stop as blocked when the error needs external action.

## Status Summary

For "overall task status":

1. Call `getTasks()`.
2. Count statuses: `running`, `running[error]`, `error`, `stopped`, `created`, and unknown.
3. For each non-running task, include ID, name, status, source, and a short action.
4. For `running[error]` or `error`, fetch `system` logs and quote only a short summary of the newest error line.

## Vectum Implementation Notes

- Task config is stored as `push.yaml`, `push.toml`, or `push.json` under the task workspace.
- Vectum starts Vector with `{vector.home}/bin/vector -c {task.workspace}/{taskId}/push.{ext}`.
- `console` maps to `info.log`; `system` maps to `error.log`.
- `createTask` and `updateTask` do not accept Lua files through MCP in the current project implementation.
- `updateTask` should receive all fields because lower layers update the task object from the DTO and can overwrite omitted fields.
