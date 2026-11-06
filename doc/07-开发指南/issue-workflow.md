# Issue 归纳与修复工作流

本文说明两种 issue 工作流：

1. 维护者按需要求“整理 issue”，生成报告并人工审核；明确授权后逐个修复、测试和提交。
2. 本地测试发现问题并完成修复后，指定 GitHub 或 Gitee 创建追踪 issue；提交代码时关联并关闭该 issue。

Gitee 是主提交仓库，代码通过已有的 Gitee-to-GitHub 自动镜像同步；两边 issue 数据独立处理，不做 issue 镜像。

Codex 可以分析问题、修改代码和运行测试，但默认只输出完整的提交命令，不执行 `git add`、`git commit` 或 `git push`。只有维护者明确说“由你提交”时，Codex 才执行 `commit --execute`，且只创建本地提交；推送始终需要单独授权。本工作流不安装或依赖 Git hooks。

## 适用范围

本工作流只覆盖 ZenVis 根仓库：

- `zenvis-plugin-*`、`zenvis-service-*` 等嵌套 Git 仓库不纳入本流程。
- 报告只读取两个平台指定状态的 issue，并过滤 Pull Request，默认读取 open issue。
- 报告保存到公开的 `doc/09-issue-reports/`，因此不得写入真实 token、密码、私钥或未脱敏对话。
- 修复代码在当前分支修改，不创建 issue 专用分支；所有 Git 历史操作由维护者决定是否授权 Codex 执行。
- 提交是否关联 issue 由维护者决定。普通提交不会自动创建 issue，也不会自动添加 trailer。

## 初始配置

### 1. 配置平台凭据

GitHub 使用具有 Issues 读写权限的 fine-grained PAT，Gitee 使用具有 issues 权限的个人令牌。只通过环境变量提供，不写入仓库：

```bash
export ZENVIS_GITHUB_TOKEN='<GITHUB_PAT>'
export ZENVIS_GITEE_TOKEN='<GITEE_TOKEN>'
```

可选覆盖：

```bash
export ZENVIS_GITHUB_REPO='coolxer-studio/zenvis'
export ZENVIS_GITEE_REPO='coolxer-studio/zenvis'
export ZENVIS_ISSUE_DRY_RUN='1'
```

`ZENVIS_ISSUE_DRY_RUN=1` 只预览将执行的平台操作；`report` 仍会生成本地报告。脚本不会从 Git remote URL 中读取内嵌凭据，也不会打印其中的用户名或密码。

### 2. 配置镜像 Action 的 Gitee 凭据

代码镜像由仓库现有配置完成，本地不需要增加 GitHub remote。GitHub 镜像收到提交后，`.github/workflows/issue-traceability.yml` 负责确认提交已到达 Gitee 和 GitHub，再评论并关闭对应 issue。

在 GitHub 仓库的 `Settings → Secrets and variables → Actions` 中添加：

```text
ZENVIS_GITEE_TOKEN=<具有 Gitee issues 读写权限的 token>
```

GitHub 侧使用 Action 自带的 `GITHUB_TOKEN`，无需额外配置。未配置 `ZENVIS_GITEE_TOKEN` 时，Action 只能关闭 GitHub issue，Gitee issue 会保持打开并导致任务失败。

### 3. 检查环境

```bash
python3 agent-skills/zenvis-issue-workflow/scripts/zenvis_issue.py doctor
```

`doctor` 只做只读检查。它不会要求本地存在 GitHub remote，不会输出 token，也不修改 issue。离线检查可使用：

```bash
python3 agent-skills/zenvis-issue-workflow/scripts/zenvis_issue.py doctor --offline
```

如果 `doctor` 报告旧配置 `core.hooksPath=.githooks`，说明本机还残留已删除的 hook 路径。维护者执行：

```bash
git config --unset core.hooksPath
```

不需要安装任何 hook 文件。

## 两种使用场景

### 场景 1：按需整理已有 issue

1. 维护者在需要时要求 Codex“整理 issue”或“生成 issue 报告”。不创建定时任务，也不自动修复。
2. Codex 读取两个平台的 issue，生成审核报告。
3. 维护者删除不执行的 issue 块、修改修复范围和验收标准，然后明确要求“按这份报告修复”。
4. Codex 每次只处理一个保留的 issue：修改代码、运行测试并停止，默认输出 `commit` 准备的完整命令。
5. 维护者执行命令并提交。若明确要求 Codex 提交，Codex 使用 `--execute` 只创建本地 commit，不推送。
6. 维护者确认提交完成后，Codex 才继续处理下一个 issue，保证一个 issue 对应一个 commit。
7. 维护者执行 `git push`，或在单独授权后由 Codex 执行 `push`。提交到达 GitHub `main` 后，镜像 Action 确认两个平台都存在该提交，再评论并关闭对应 issue。

### 场景 2：本地修复后补建 issue

1. 维护者完成本地问题分析，明确指定 GitHub 或 Gitee。
2. Codex 使用 `create-issue` 立即创建 issue，返回 `github#N` 或 `gitee#IKSGM9` 及链接。
3. 维护者准备提交时，Codex 使用 `commit --issue ...` 输出关联 issue 的完整命令。
4. 维护者执行命令，或在明确授权后由 Codex 使用 `--execute` 创建本地 commit。提交已包含唯一 issue 引用和关闭 trailer。
5. 维护者执行 `git push` 后，镜像 Action 在提交到达 `main` 时评论并关闭该 issue。

issue 正文只记录问题摘要和必要的脱敏验证结果，不上传 diff、原始日志、完整对话或凭据。

## 第一步：生成报告

在对话中明确要求“整理 issue”或“生成 issue 报告”，或者直接执行：

```bash
python3 agent-skills/zenvis-issue-workflow/scripts/zenvis_issue.py report
```

命令会读取两个平台当前全部 open issue，并生成：

```text
doc/09-issue-reports/YYYY-MM-DD-HHmmss/
├── README.md
├── github.md
└── gitee.md
```

同时更新 `doc/09-issue-reports/README.md` 的索引，并在忽略提交的 `.zenvis/issues/reports/` 保存本次规范化数据。

报告生成是只读操作：不修改代码、不创建分支、不修改 issue 标签或状态，也不自动提交报告。

## 第二步：人工审核和删减

每个 issue 固定为一个标题块：

```markdown
## github#12 — 标题
- 状态：open
- 标签：bug, backend
- 原始链接：https://github.com/...
### 需求摘要
### 修复范围
### 验收标准
### 风险与待确认
```

Gitee 的 issue 编号通常是 `IKSGM9` 这类字母数字组合，不是数字；报告中会显示实际的 `gitee#IKSGM9`，后续修复和提交都必须沿用该编号。

审核规则：

- 在 `github.md` 或 `gitee.md` 中删除整个 `## platform#number` 块，表示本次不执行该 issue；总览 `README.md` 的执行清单只用于概览，不决定执行范围。
- 修改“修复范围”和“验收标准”，表示按修改后的内容执行。
- 最终保存的 Markdown 就是批量执行清单；不要只保留标题或删除字段名。

审核完成后，只有明确说“按这份报告修复”“修复 github#12”或“修复 gitee#IKSGM9”，才代表授权进入代码修改阶段。

## 第三步：批量修复

Codex 使用以下命令准备修复会话：

```bash
python3 agent-skills/zenvis-issue-workflow/scripts/zenvis_issue.py repair
python3 agent-skills/zenvis-issue-workflow/scripts/zenvis_issue.py repair --report doc/09-issue-reports/YYYY-MM-DD-HHmmss
python3 agent-skills/zenvis-issue-workflow/scripts/zenvis_issue.py repair --issue github#12 --issue gitee#IKSGM9
```

`repair` 只读取最终报告并记录执行会话，不会自动改代码。实际修复遵循以下顺序：

1. Codex 直接在当前分支修改代码。不要创建 issue 专用分支，也不要逐个 issue 切换分支。
2. 按报告顺序逐个 issue 处理：重新读取原始 issue，按最终报告的“修复范围”修改代码并运行对应测试。
3. 一个 issue 只能形成一个 commit。若必须拆成多个 commit，停止并先拆分 issue。
4. Codex 默认不执行 Git。测试通过后，Codex 使用 `commit` 输出完整的暂存和提交命令；维护者检查后手动执行。生成的提交信息包含对应 trailer：

```text
Zenvis-Issue: github#12
Closes #12
```

或：

```text
Zenvis-Issue: gitee#IKSGM9
Gitee-Closes: #IKSGM9
```

5. 维护者确认该 issue 的提交完成后，Codex 才处理下一个 issue。不要在一个 issue 尚未提交时继续修改下一个 issue，否则无法保证“一 issue 一 commit”。
6. 提交完成后由维护者执行：

```bash
git push
```

Gitee `origin` 是主仓库，提交会自动镜像到 GitHub。镜像 Action 只在提交到达 `main` 后，确认两个平台都存在该提交，再评论 commit 链接并关闭对应 issue；不需要本地配置 GitHub remote，也不要把同一次提交推送到第二个远端。

7. 全部完成后更新报告中的执行状态；报告更新作为普通文档提交处理，可以不带 issue trailer。

任何代码失败或测试失败都会停止整批处理。已完成提交保留，未处理 issue 继续留在报告中。推送失败时不要关闭 issue。GitHub Action 关闭失败时重跑 Action，或执行 `reconcile`；它只会在平台 commit API 确认提交存在后关闭 issue。

## 本地修复后补建 issue

当本地已经分析和修复问题，但还没有 issue 时，指定平台后立即创建：

```bash
python3 agent-skills/zenvis-issue-workflow/scripts/zenvis_issue.py create-issue \
  --platform gitee \
  --title "临时图表查询失败" \
  --summary "临时图表在查询阶段提示方案未批准，需要定位工作流状态。" \
  --verification "已复现，待修复"
```

可选参数：

- `--label bug`：添加标签，可重复传入。
- `--verification "测试结果"`：记录脱敏后的验证结果。
- `--json`：输出机器可读 JSON，包含 `ref`、`url` 等信息。

创建成功后会返回 `gitee#IKSGM9` 或 `github#12` 及链接。正文只包含问题摘要和验证结果，不包含 diff。预览操作可设置 `ZENVIS_ISSUE_DRY_RUN=1`，此时不会调用平台 API，也不会生成真实 issue ID。

提交时准备完整、可复制的命令：

```bash
python3 agent-skills/zenvis-issue-workflow/scripts/zenvis_issue.py commit \
  --issue gitee#IKSGM9 \
  --message "fix: 修复临时图表查询状态判断" \
  --file zenvis-backend/path/to/fixed-file.java \
  --file zenvis-frontend/path/to/fixed-file.ts
```

命令只输出到终端，不会执行 Git。默认内容为：

```bash
git add -- 'file1' 'file2'
git commit -F - <<'ZENVIS_COMMIT_MSG'
fix: ...

Zenvis-Issue: gitee#IKSGM9
Gitee-Closes: #IKSGM9
ZENVIS_COMMIT_MSG
```

GitHub issue 使用 `Closes #N`。一次只能关联一个 issue。输出不包含 `git push`，推送需要在提交完成后单独执行。

### 由 Codex 创建本地提交

只有维护者明确说“由你提交”时，才在相同参数后添加 `--execute`：

```bash
python3 agent-skills/zenvis-issue-workflow/scripts/zenvis_issue.py commit \
  --issue gitee#IKSGM9 \
  --message "fix: 修复临时图表查询状态判断" \
  --file zenvis-backend/path/to/fixed-file.java \
  --execute
```

`--execute` 只执行 `git add` 和 `git commit`。它不会推送，也不会关闭 issue。`commit-command` 保留为只输出命令的兼容别名。

### 不关联 issue 的普通提交

省略 `--issue` 即可生成普通提交，不创建 issue，也不添加 trailer：

```bash
python3 agent-skills/zenvis-issue-workflow/scripts/zenvis_issue.py commit \
  --message "docs: 更新说明" \
  --file doc/README.md
```

## 推送与关闭

正常路径由维护者直接执行：

```bash
git push
```

镜像 Action 会自动处理 issue 评论和关闭。只有在维护者明确授权 Codex 推送时，才执行：

```bash
python3 agent-skills/zenvis-issue-workflow/scripts/zenvis_issue.py push
```

`push` 推送当前分支到 Gitee `origin`，并在成功后关闭关联的 Gitee issue；GitHub issue 留给镜像 Action。若推送失败，不执行关闭。

若 Action 未启用、失败，或需要事后补关闭，再执行：

```bash
python3 agent-skills/zenvis-issue-workflow/scripts/zenvis_issue.py reconcile
```

`reconcile` 会检查最近提交和 trailer，并通过 Gitee / GitHub 的 commit API 确认提交已经到达对应平台，只对确认存在的提交补评论和关闭；不需要本地 GitHub remote。

## 关闭失败恢复

关闭评论或状态更新失败时，脚本写入忽略提交的 `.zenvis/issues/retry-close.json`。修复权限或网络后执行：

```bash
python3 agent-skills/zenvis-issue-workflow/scripts/zenvis_issue.py retry-close
```

评论使用提交 SHA 标记，重试不会重复写入同一评论。

## 命令速查

| 命令 | 作用 | 是否修改平台 | 是否操作 Git |
| --- | --- | --- | --- |
| `doctor` | 检查 token、remote、仓库访问和残留旧 hook 配置 | 否 | 否 |
| `report` | 生成两个平台的 issue 报告 | 否 | 否 |
| `create-issue` | 按问题摘要立即创建指定平台的 issue 并返回 ID | 是 | 否 |
| `commit` | 默认输出 add/commit 命令；`--execute` 只创建本地提交 | 否 | 仅 `--execute` |
| `commit-command` | `commit` 的兼容别名，只输出命令 | 否 | 否 |
| `repair` | 读取审核后的报告并准备执行会话 | 否 | 否 |
| `show` | 重新读取单个 issue 的当前平台内容 | 否 | 否 |
| `push` | 推送 Gitee `origin` 并关闭关联 Gitee issue | 是 | 是 |
| `reconcile` | Action 不可用时，对已到达平台的提交补齐关闭 | 是 | 否 |
| `retry-close` | 重试失败的评论或关闭 | 是 | 否 |

## 相关文件

- `.github/workflows/issue-traceability.yml`：GitHub 镜像收到提交后，确认两个平台都存在该提交，再补评论并关闭对应 issue。
- `agent-skills/zenvis-issue-workflow/SKILL.md`：Codex 的唯一完整执行规范。
- `agent-skills/zenvis-issue-workflow/scripts/zenvis_issue.py`：工作流实现脚本。
- `agent-skills/zenvis-issue-workflow/tests/test_zenvis_issue.py`：工作流自动化测试。
- `doc/09-issue-reports/`：公开的审核报告和修复记录。
