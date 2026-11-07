---
name: zenvis-issue-workflow
description: Generate on-demand, human-reviewed GitHub and Gitee issue reports; create an issue on an explicitly chosen platform; repair only approved issue blocks; and prepare or execute Git commits only when explicitly authorized. Use when the user asks to organize issues, create a local-problem issue on GitHub or Gitee, repair a specific issue, commit a change with issue traceability, or push completed issue fixes.
---

# ZenVis Issue Workflow

The implementation, tests and operating rules for this skill live entirely under
`agent-skills/zenvis-issue-workflow/`. Use the full script path in every command:

```bash
python3 agent-skills/zenvis-issue-workflow/scripts/zenvis_issue.py <command>
```

## Modes and authorization

Choose a mode only from the user's explicit request:

- `report`: requests such as “整理 issue”, “生成 issue 报告” or “归纳 GitHub/Gitee issue”.
- `create`: the user identifies a local problem and explicitly names GitHub or Gitee for a new issue.
- `repair`: “按报告修复”, “执行这份报告”, “修复 github#N” or “修复 gitee#<issue-id>”.
- `commit`: “准备提交命令”, “生成提交命令”, or an explicit request to include a known issue in a commit.
- `push`: the user explicitly asks to push. A request to prepare a commit or review a diff is not push authorization.
- `triage question`: answer questions about an existing report without changing code or platform state.

Default boundary:

- Codex may edit code and run tests in `repair` mode.
- Codex does not run `git add`, `git commit` or `git push` by default. It prints complete, copy-paste commands.
- If the user explicitly says “由你提交”, call `commit --execute`; this performs only `git add` and `git commit`.
- If the user explicitly says “推送”, call `push` after the commit exists. Commit authorization never implies push authorization.
- Never infer issue-repair authorization from report creation or report editing. Never choose an issue platform on the user's behalf.
- Codex does not create branches, switch branches, create Git hooks or create scheduled automations.

## Report mode

1. Run:

   ```bash
   python3 agent-skills/zenvis-issue-workflow/scripts/zenvis_issue.py report
   ```

2. Read the generated `doc/09-issue-reports/YYYY-MM-DD-HHmmss/README.md`, `github.md` and
   `gitee.md`.
3. For each issue, inspect enough repository context to improve “需求摘要”, “修复范围”,
   “验收标准” and “风险与待确认”. Keep GitHub and Gitee independent; do not create
   cross-platform links or mirrors.
4. Keep the fixed heading and field names. Do not put raw conversation transcripts in the
   report. Preserve the script's redaction.
5. Do not create a branch, edit product code, mutate issue labels/state, stage files or
   commit the report.
6. Tell the user the report path and explain that they may delete whole
   `## platform#number` blocks and edit the scope/acceptance sections before explicitly
   requesting repair.

If either platform cannot be read, stop and report the failed platform. Do not silently
produce a partial report.

## Create mode

Use this mode only after a local problem is understood and the user has explicitly named
GitHub or Gitee. If the platform is missing, ask before creating anything.

1. Build a short, redacted problem summary. Do not include raw diff, logs, conversation
   transcripts, tokens or other credentials.
2. Run:

   ```bash
   python3 agent-skills/zenvis-issue-workflow/scripts/zenvis_issue.py create-issue \
     --platform gitee \
     --title "临时图表查询失败" \
     --summary "临时图表在查询阶段提示方案未批准，需要定位工作流状态。" \
     --verification "已复现，待修复" \
     --json
   ```

   Optional arguments are repeatable `--label` and `--verification`. Add `--json` for a
   machine-readable result. `ZENVIS_ISSUE_DRY_RUN=1` previews the body without calling the
   platform.
3. Return the created `github#N` or `gitee#<issue-id>` and its URL to the user. Keep that
   reference for the later commit.
4. Do not commit code or close the new issue during creation. Closing happens only after a
   commit containing the required trailer reaches GitHub `main` and the Action confirms the
   mirrored commit on both platforms.
5. Repeated requests create new issues. Duplicate detection belongs to report review; do not
   invent automatic deduplication.

## Repair mode

### Prepare

1. Resolve the target report. Use the explicit path if supplied; otherwise use the latest
   timestamp directory.
2. Run:

   ```bash
   python3 agent-skills/zenvis-issue-workflow/scripts/zenvis_issue.py repair --json
   ```

   For a single authorized issue, add `--issue github#N` or `--issue gitee#IKSGM9`. Gitee
   issue identifiers are alphanumeric; preserve the identifier shown in the report. The
   returned blocks are the only authorized work. Never repair an issue block the user deleted.
3. Check `git status --short`. Separate unrelated working-tree changes. Do not include
   unrelated files in issue commits.
4. Work directly on the current branch. Do not create or switch branches.

### Execute each retained issue

Process the report in file order: `github.md`, then `gitee.md`.

1. Re-read the live issue:

   ```bash
   python3 agent-skills/zenvis-issue-workflow/scripts/zenvis_issue.py show github#12
   ```

   Use the appropriate reference. Do not trust a stale report when the live issue contradicts
   it; stop and ask if the difference changes the scope.
2. Implement only the retained “修复范围”. Run the smallest relevant test first, then the
   broader checks required by the touched subsystem.
3. After the issue's code and tests are complete, prepare the commit command:

   ```bash
   python3 agent-skills/zenvis-issue-workflow/scripts/zenvis_issue.py commit \
     --issue github#12 \
     --message "fix: 修复看板刷新失败" \
     --file path/to/changed-file
   ```

   For Gitee, use `--issue gitee#IKSGM9`. GitHub emits `Zenvis-Issue: github#N` and
   `Closes #N`; Gitee emits `Zenvis-Issue: gitee#ID` and `Gitee-Closes: #ID`.
4. By default, stop after printing the command and wait for the user to execute it. When the
   user explicitly says “由你提交”, rerun the same command with `--execute`; that runs only
   `git add` and `git commit`, never `git push`.
5. Stop after one issue until the user confirms its commit completed. This preserves the
   one-issue/one-commit invariant. Do not begin the next issue before confirmation.
6. A commit may contain no issue trailer when the user does not request traceability. Omit
   `--issue` in that case: no issue is created and no trailer is added.
7. The mirror GitHub Action verifies that the commit exists on both platforms before
   commenting and closing the issue. If the Action is unavailable after the user pushes, the
   user may run `reconcile`.
8. Stop the batch on code failure or test failure. Keep completed commits intact; report the
   failed issue and leave remaining issue blocks untouched.

### Finish

After all retained issues are processed and the user has committed them:

1. Update the report `README.md` execution list and the relevant platform files with status,
   commit URL and test result.
2. Prepare, but do not execute, a commit command for only the report files. A report commit is
   a normal documentation commit and does not need an issue trailer.
3. Ask separately before pushing. Report completion does not imply push authorization.
4. Do not create or mirror issues across platforms; issue data remains independent even
   though code is mirrored.

## Local problem without an existing issue

When the user tests locally, fixes the problem, and then asks for a tracking issue:

1. Use `create` mode only after the platform is explicit.
2. Return the issue reference from `create-issue`.
3. When the user asks to prepare a commit, use `commit` with that reference and the exact
   files that belong to the fix.
4. Return the generated command verbatim unless the user explicitly asked Codex to execute
   the commit.
5. Do not execute Git or close the issue unless the user separately authorizes that action.

## Push and recovery

- `push` is a separate, explicit operation. It pushes the current branch to Gitee `origin`;
  GitHub receives the code through the existing mirror. It may close associated Gitee issues
  after a successful push.
- If the user asks only for a push command, print `git push`; do not execute it.
- If the user explicitly authorizes Codex to push, run:

  ```bash
  python3 agent-skills/zenvis-issue-workflow/scripts/zenvis_issue.py push
  ```

- Missing Gitee `origin` or token: stop before pushing; do not close the issue.
- Push failure: leave the issue open and stop the batch.
- GitHub Action failure after a successful push: re-run the Action, or use:

  ```bash
  python3 agent-skills/zenvis-issue-workflow/scripts/zenvis_issue.py reconcile
  ```

- Local close/comment failure: use:

  ```bash
  python3 agent-skills/zenvis-issue-workflow/scripts/zenvis_issue.py retry-close
  ```

- To close issues directly without a commit, use `close`. It reads the current state first,
  skips issues already closed, writes the optional comment once, and re-reads the issue to
  confirm `closed`:

  ```bash
  python3 agent-skills/zenvis-issue-workflow/scripts/zenvis_issue.py close \
    github#4 gitee#IKTO68 --comment "测试 issue 已完成验证，现直接关闭。"
  ```

  Add `--json` for machine-readable output; set `ZENVIS_ISSUE_DRY_RUN=1` to preview.
  The command exits non-zero if any issue fails, while still processing the rest.

- Never print or store tokens. Use the environment variables documented in
  `doc/07-开发指南/issue-workflow.md`.

## Legacy hook cleanup

This workflow no longer uses Git hooks. If `doctor` reports a stale `core.hooksPath=.githooks`,
ask the user to run:

```bash
git config --unset core.hooksPath
```

Do not modify the user's Git configuration without explicit permission.

## References

- `doc/07-开发指南/issue-workflow.md` for setup, commands and recovery.
- `doc/09-issue-reports/README.md` for the public report index and review rules.
- `CONTRIBUTING.md` for the short contributor workflow.
- `agent-skills/zenvis-issue-workflow/tests/test_zenvis_issue.py` for executable expectations.
