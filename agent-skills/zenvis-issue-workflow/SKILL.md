---
name: zenvis-issue-workflow
description: Generate on-demand, human-reviewed GitHub and Gitee issue reports; create an issue on an explicitly chosen platform; repair only approved issue blocks; prepare or execute Git commits only when explicitly authorized; and prepare or publish version releases on Gitee and GitHub. Use when the user asks to organize issues, create a local-problem issue, repair a specific issue, commit a change with issue traceability, push completed fixes, or publish a version release.
---

# ZenVis Issue Workflow

The implementation, tests and operating rules for this skill live entirely under
`agent-skills/zenvis-issue-workflow/`. Use the full script path in every command:

```bash
python3 agent-skills/zenvis-issue-workflow/scripts/zenvis_issue.py <command>
```

## Local configuration

The CLI reads platform settings from the process environment and, when present, from the
gitignored repository file `.zenvis/.env`. The file accepts simple `KEY=VALUE` and
`export KEY=VALUE` lines, blank lines, full-line comments, and single- or double-quoted
values. Only keys starting with `ZENVIS_` plus `GITHUB_TOKEN` are loaded.

Process environment values always take precedence over the file. Never print, commit or
include token values in reports, release notes or command output.

## Modes and authorization

Choose a mode only from the user's explicit request:

- `report`: requests such as “整理 issue”, “生成 issue 报告” or “归纳 GitHub/Gitee issue”.
- `create`: the user identifies a local problem and explicitly names GitHub or Gitee for a new issue.
- `repair`: “按报告修复”, “执行这份报告”, “修复 github#N” or “修复 gitee#<issue-id>”.
- `commit`: “准备提交命令”, “生成提交命令”, or an explicit request to include a known issue in a commit.
- `push`: the user explicitly asks to push. A request to prepare a commit or review a diff is not push authorization.
- `release`: “发布 1.1.6.alpha 版本”, “准备版本发布” or “自动创建 release”.
- `triage question`: answer questions about an existing report without changing code or platform state.

Default boundary:

- Codex may edit code and run tests in `repair` mode.
- Codex does not run `git add`, `git commit` or `git push` by default. It prints complete, copy-paste commands.
- If the user explicitly says “由你提交”, call `commit --execute`; this performs only `git add` and `git commit`.
- If the user explicitly says “推送”, call `push` after the commit exists. Commit authorization never implies push authorization.
- If the user explicitly says “发布 <version> 版本”, call `release --execute`; that authorizes
  the release commit, annotated tag, tag/branch push to Gitee `origin`, and Gitee/GitHub
  Release creation for that version. It never authorizes unrelated commits, branch changes,
  issue repair or issue closure.
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

## Release mode

Version releases are separate from issue repair. A release request does not authorize code
changes outside the version files and release notes.

### Prepare

1. Run:

   ```bash
   python3 agent-skills/zenvis-issue-workflow/scripts/zenvis_issue.py release
   ```

   The command reads `deploy/.env.example` and the latest `X.Y.Z[.alpha|beta|rc]` tag,
   computes the target version, writes a review draft to `.zenvis/releases/<tag>.md`, and
   prints the files that `--execute` will change. It does not modify tracked files, commit,
   tag, push or call platform write APIs. Use `--version` for an explicit version and
   `--no-fetch` to skip `git fetch --tags`.
2. Review and edit the draft. Add the user-facing “版本重点”; keep the generated commit
   classification but remove internal-only or misleading entries. Remove the review prompt
   `请审核提交清单，补充面向用户的版本重点后再执行发布。`; `--execute` rejects a draft
   that still contains it.
3. The command updates these files during execution: `deploy/.env.example`,
   `zenvis-backend/build.sh`, `zenvis-frontend/build.sh`, `DataInitiator.java`,
   `doc/_coverpage.md`, `README.md`, `quick-deploy.sh`, and the two deployment guides under
   `doc/02-安装部署与升级/`. Branch references follow the current `main` or `feature/*`
   branch. Do not add `zenvis-frontend/package.json`, Helm values or `VECTUM_VERSION` to the
   release change.

### Publish

After the user has reviewed the draft and explicitly authorized the release:

```bash
python3 agent-skills/zenvis-issue-workflow/scripts/zenvis_issue.py release \
  --execute \
  --notes .zenvis/releases/1.1.6.alpha.md
```

The command requires a clean tracked worktree, both platform tokens, an unused tag, and no
existing Release with the same tag. It then:

1. Writes `doc/10-版本发布/<tag>.md`, updates its index and `doc/_sidebar.md`, and updates
   all version references.
2. Commits only the release files with `chore(release): 发布 <tag>`, creates an annotated
   tag whose message is the reviewed notes, and pushes the current branch plus the tag to
   Gitee `origin` in one push.
3. Creates the Gitee Release, then waits up to 120 seconds for the GitHub mirror tag. If the
   tag does not appear, it creates the GitHub Release against the release commit SHA.

`.alpha`/`.beta`/`.rc` versions are marked prerelease on both platforms.

If one platform fails, the command exits non-zero and writes
`.zenvis/releases/<tag>.state.json`. Retry without repeating Git operations:

```bash
python3 agent-skills/zenvis-issue-workflow/scripts/zenvis_issue.py release-publish \
  --tag 1.1.6.alpha \
  --commit <release-sha> \
  --notes doc/10-版本发布/1.1.6.alpha.md
```

`release-publish` is idempotent: a Release with identical notes is skipped, while different
notes are rejected. Set `ZENVIS_ISSUE_DRY_RUN=1` to preview every release action without
writing files or calling platform APIs.

Do not build or push Docker images, update Helm values, or change `VECTUM_VERSION` as part
of this mode. Those remain separate deployment tasks.

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
- `doc/10-版本发布/README.md` for the public release index and publishing rules.
- `CONTRIBUTING.md` for the short contributor workflow.
- `agent-skills/zenvis-issue-workflow/tests/test_zenvis_issue.py` for executable expectations.
- `agent-skills/zenvis-issue-workflow/tests/test_release.py` for release expectations.
