# ZenVis Issue Workflow

`zenvis-issue-workflow` 是 ZenVis 仓库内的研发侧 Codex Skill，用于把 GitHub / Gitee issue 整理、修复、提交和关闭串成一条可审核的追踪链路。

它不是 ZenVis 平台运行时插件，不会出现在平台的 Skill 管理页，也不会被后端自动加载。它服务于在本仓库中工作的研发人员和 Codex。

核心原则：

- 报告生成只读 issue 和代码，不修改代码、标签或 issue 状态。
- 修复必须由维护者明确授权，并以最终编辑版报告为唯一执行清单。
- Codex 默认只输出提交命令，不执行 `git add`、`git commit` 或 `git push`。
- 一个 issue 对应一个 commit；Gitee 是主提交仓库，GitHub 通过现有镜像同步代码。

## 两种使用场景

| 场景 | 触发方式 | 结果 |
| --- | --- | --- |
| 已有 issue，需要整理和修复 | 说“整理 issue”，审核报告后说“按报告修复” | 生成报告，逐个修复，生成关联 issue 的提交命令 |
| 本地已发现问题或完成修复，但没有 issue | 指定平台后说“为这个问题创建 Gitee/GitHub issue” | 立即创建 issue，返回 `github#N` 或 `gitee#ID`，之后可关联提交 |

## 前置条件

1. 在 ZenVis 仓库根目录执行命令。脚本需要以根仓库作为报告、状态和 Git 操作基准。
2. 安装 Python 3，仅使用标准库，不需要安装第三方 Python 包。
3. 配置平台 token。只通过环境变量提供，不要把 token 写入命令示例、报告或仓库文件。

```bash
export ZENVIS_GITHUB_TOKEN='<GITHUB_PAT>'
export ZENVIS_GITEE_TOKEN='<GITEE_TOKEN>'
```

可选配置：

```bash
export ZENVIS_GITHUB_REPO='coolxer-studio/zenvis'
export ZENVIS_GITEE_REPO='coolxer-studio/zenvis'
export ZENVIS_ISSUE_DRY_RUN='1'
```

- GitHub token 需要目标仓库的 Issues 读写权限；fine-grained PAT 需要显式授权该仓库。
- Gitee token 需要目标仓库的 issues 权限。
- `ZENVIS_ISSUE_DRY_RUN=1` 用于预览平台写操作。`report` 仍会生成本地报告，`create-issue`、关闭和重试等平台写入不会真正执行。

先做只读自检：

```bash
python3 agent-skills/zenvis-issue-workflow/scripts/zenvis_issue.py doctor
```

离线检查环境，不访问平台 API：

```bash
python3 agent-skills/zenvis-issue-workflow/scripts/zenvis_issue.py doctor --offline
```

如果输出提示旧配置 `core.hooksPath=.githooks`，说明本机还残留已经移除的 Git hook 路径。确认不需要其他 hook 后，由维护者手动执行：

```bash
git config --unset core.hooksPath
```

## 启用 Skill

研发侧 Skill 不会自动安装到本机 Codex，也不会被 ZenVis 后端加载。可以选择以下两种方式：

- 只在当前仓库使用：让 Codex 阅读 `agent-skills/zenvis-issue-workflow/SKILL.md`，或直接在对话中说明上述触发词。脚本仍按本 README 的项目相对路径运行。
- 在本机 Codex 中全局启用：从仓库根目录创建软链接，推荐使用软链接而不是复制，便于跟随仓库更新：

```bash
mkdir -p "$HOME/.codex/skills"
ln -s "$(pwd)/agent-skills/zenvis-issue-workflow" \
  "$HOME/.codex/skills/zenvis-issue-workflow"
```

完成后重新打开 Codex 会话，使客户端重新扫描 Skill。之后可在对话中点名 `$zenvis-issue-workflow`。如果目标链接已经存在，先确认它指向本仓库，再决定是否替换。

## 在 Codex 对话中触发

Skill 已安装或当前会话已加载后，可以在提示词中点名 `$zenvis-issue-workflow`；不点名时，明确的业务指令也可以触发对应模式。建议直接说明“要做什么”和“是否授权写操作”。

常用对话：

```text
$zenvis-issue-workflow 整理当前 GitHub 和 Gitee 的 open issue，生成审核报告。

按这份报告修复，只处理 gitee#IKSGM9。

为这个本地问题在 Gitee 创建 issue：临时图表查询时提示方案未批准。

为这次修改生成关联 gitee#IKSGM9 的提交命令。

由你提交，但不要推送。

推送当前分支。
```

各模式的触发和副作用：

| 模式 | 典型说法 | Codex 可以做什么 | 不会做什么 |
| --- | --- | --- | --- |
| `report` | “整理 issue”“生成 issue 报告” | 读取 issue、检查代码、写报告 | 不改代码，不改 issue，不提交 |
| `create` | “在 Gitee/GitHub 创建 issue” | 调用平台 API 创建 issue | 不自行选择平台，不提交代码 |
| `repair` | “按报告修复”“修复 gitee#ID” | 修改代码、运行测试、输出提交命令 | 不自动 commit 或 push |
| `commit` | “生成提交命令”“由你提交” | 默认输出命令；授权后只执行 add + commit | 永不顺带 push |
| `push` | “推送”“推送到 origin” | 明确授权后推送当前分支 | 不会把提交授权当成推送授权 |
| `close` | “直接关闭 github#4”“关闭这些测试 issue” | 关闭指定 issue 并回读确认 | 不操作 Git，不重复关闭已关闭 issue |

## 场景 1：整理并修复已有 issue

### 1. 生成审核报告

```bash
python3 agent-skills/zenvis-issue-workflow/scripts/zenvis_issue.py report
```

默认读取两个平台的 open issue，并过滤 Pull Request。需要指定范围时：

```bash
python3 agent-skills/zenvis-issue-workflow/scripts/zenvis_issue.py report --state all
python3 agent-skills/zenvis-issue-workflow/scripts/zenvis_issue.py report --state closed
```

报告写入：

```text
doc/09-issue-reports/YYYY-MM-DD-HHmmss/
├── README.md    # 汇总、审核说明和执行清单
├── github.md    # GitHub issue 明细
└── gitee.md     # Gitee issue 明细
```

同时更新公开索引：`doc/09-issue-reports/README.md`。

每个 issue 使用固定块格式：

```markdown
## gitee#IKSGM9 — 临时图表查询失败
- 状态：open
- 标签：bug, backend
- 原始链接：https://gitee.com/...
- 作者：someone
- 更新时间：2026-11-03T10:00:00+08:00
### 需求摘要
临时图表在查询阶段提示方案未批准。
### 修复范围
检查工作流状态判断，并补充回归测试。
### 验收标准
复现路径恢复正常，相关测试通过，无相邻功能回归。
### 风险与待确认
- 类型：缺陷
- 领域：后端
- 优先级：P1
```

### 2. 人工审核报告

- 删除整个 `## platform#number` 块，表示不执行该 issue。
- 修改“修复范围”和“验收标准”，表示按修改后的内容执行。
- 实际执行清单以 `github.md` 和 `gitee.md` 中保留的块为准；总览 `README.md` 只用于查看。
- 报告是公开审计记录，只写脱敏摘要，不放原始对话、diff、日志、token 或凭据。

### 3. 授权修复

审核完成后，在对话中明确说“按这份报告修复”。也可以只处理一个 issue：

```bash
python3 agent-skills/zenvis-issue-workflow/scripts/zenvis_issue.py repair --issue gitee#IKSGM9 --json
```

`repair` 只读取最终报告并返回保留的 issue 块，不会自己修改代码。Codex 随后按报告执行：

1. 重新读取平台上的当前 issue，确认范围没有变化。
2. 只修改“修复范围”允许的代码。
3. 先运行最小相关测试，再运行受影响的更广泛检查。
4. 一个 issue 完成后停止，输出该 issue 的提交命令。
5. 用户完成提交后，才处理下一个 issue。

报告默认按 `github.md`、`gitee.md` 的文件顺序处理。代码失败或测试失败时停止整批，不跳过失败项继续执行。

### 4. 生成关联提交命令

```bash
python3 agent-skills/zenvis-issue-workflow/scripts/zenvis_issue.py commit \
  --issue gitee#IKSGM9 \
  --message "fix: 修复临时图表查询状态判断" \
  --file zenvis-backend/path/to/fixed-file.java \
  --file zenvis-frontend/path/to/fixed-file.ts
```

默认只输出命令，不执行 Git。输出形态如下：

```bash
git add -- 'zenvis-backend/path/to/fixed-file.java' 'zenvis-frontend/path/to/fixed-file.ts'
git commit -F - <<'ZENVIS_COMMIT_MSG'
fix: 修复临时图表查询状态判断

Zenvis-Issue: gitee#IKSGM9
Gitee-Closes: #IKSGM9
ZENVIS_COMMIT_MSG
```

GitHub issue 使用 `Zenvis-Issue: github#N` 和 `Closes #N`。一次只能关联一个 issue；提交信息里已有其他 issue 引用会被拒绝。

确认命令内容后，由维护者复制执行。只有在明确说“由你提交”后，Codex 才会在相同参数后添加 `--execute`：

```bash
python3 agent-skills/zenvis-issue-workflow/scripts/zenvis_issue.py commit \
  --issue gitee#IKSGM9 \
  --message "fix: 修复临时图表查询状态判断" \
  --file zenvis-backend/path/to/fixed-file.java \
  --execute
```

`--execute` 只执行 `git add` 和 `git commit`，输出类似：

```text
[OK] 已创建提交 a1b2c3d（未推送）。
```

### 5. 推送并关闭 issue

正常由维护者在提交完成后执行：

```bash
git push
```

Gitee `origin` 是主仓库。提交镜像到 GitHub `main` 后，镜像 Action 会读取 `Zenvis-Issue` 与关闭 trailer，确认该 commit 已经同时存在于 Gitee 和 GitHub，再评论并关闭对应 issue。

只有提交到达 GitHub `main` 时才会自动关闭。未合入 `main` 的提交不会触发关闭。

如果维护者明确授权 Codex 推送，可以单独执行：

```bash
python3 agent-skills/zenvis-issue-workflow/scripts/zenvis_issue.py push
```

`push` 推送当前分支到 Gitee `origin`，成功后尝试关闭关联的 Gitee issue；GitHub issue 继续由镜像 Action 处理。提交授权不包含推送授权。

## 场景 2：本地修复后补建 issue

适用于先发现问题、先用 Codex 分析和修改，但 Git 或平台上还没有 issue 的情况。

### 1. 明确指定平台并创建 issue

必须明确选择 GitHub 或 Gitee。Codex 不会替用户选择平台。

对话示例：

```text
我在本地发现临时图表查询失败，问题已经定位并修复。
请为这个问题创建一个 Gitee issue，标题是“临时图表查询失败”。
```

对应命令：

```bash
python3 agent-skills/zenvis-issue-workflow/scripts/zenvis_issue.py create-issue \
  --platform gitee \
  --title "临时图表查询失败" \
  --summary "临时图表在查询阶段提示方案未批准，需要定位工作流状态。" \
  --verification "已在本地复现并完成修复，相关测试通过。" \
  --label bug \
  --json
```

`--json` 返回示例：

```json
{
  "dry_run": false,
  "platform": "gitee",
  "number": "IKSGM9",
  "ref": "gitee#IKSGM9",
  "url": "https://gitee.com/coolxer-studio/zenvis/issues/IKSGM9",
  "title": "临时图表查询失败",
  "labels": ["bug"]
}
```

保存返回的 `ref`，例如 `gitee#IKSGM9`。Gitee 的 issue 标识是字母数字组合，不一定是纯数字。

issue 正文只包含问题描述和必要的脱敏验证结果，不包含 diff、原始日志、完整对话或凭据。重复请求会创建新 issue，不会自动去重。

### 2. 生成关联提交命令

```bash
python3 agent-skills/zenvis-issue-workflow/scripts/zenvis_issue.py commit \
  --issue gitee#IKSGM9 \
  --message "fix: 修复临时图表查询状态判断" \
  --file zenvis-backend/path/to/fixed-file.java
```

用户确认后执行输出的 `git add` 和 `git commit`，再执行 `git push`。提交到达 GitHub `main` 后，由镜像 Action 评论并关闭 issue。

## 普通提交

省略 `--issue` 时生成普通提交，不创建 issue，也不添加 trailer：

```bash
python3 agent-skills/zenvis-issue-workflow/scripts/zenvis_issue.py commit \
  --message "docs: 更新 issue 工作流说明" \
  --file agent-skills/zenvis-issue-workflow/README.md
```

是否让提交关联 issue 由维护者决定。本 Skill 不安装 Git hook，也不会在普通提交时自动创建 issue。

## 命令速查

所有命令均从仓库根目录运行：

```bash
S=agent-skills/zenvis-issue-workflow/scripts/zenvis_issue.py
python3 "$S" <command>
```

| 命令 | 用途 |
| --- | --- |
| `doctor [--offline]` | 检查 Python、凭据、`origin`、平台仓库可访问性和旧 hook 配置 |
| `report [--state open\|closed\|all]` | 生成两个平台的 issue 报告 |
| `create-issue --platform github\|gitee --title ... --summary ...` | 在指定平台立即创建 issue |
| `repair [--report DIR] [--issue REF] [--json]` | 读取审核后的报告并返回待修复 issue |
| `show github#N\|gitee#ID` | 重新读取单个 issue 的当前平台内容 |
| `commit --message ... --file ... [--issue REF] [--execute]` | 生成命令；授权后只执行 add + commit |
| `commit-command ...` | `commit` 的兼容别名，只输出命令 |
| `push` | 明确授权后推送当前分支，并尝试关闭关联 Gitee issue |
| `reconcile` | 提交已到平台但 Action 未关闭时，补齐评论和关闭 |
| `retry-close` | 重试 `.zenvis/issues/retry-close.json` 中失败的关闭操作 |

常用可选参数：

```bash
python3 "$S" report --github-repo owner/repo --gitee-repo owner/repo
python3 "$S" create-issue --platform github --title "..." --summary "..." --verification "..." --json
python3 "$S" repair --report doc/09-issue-reports/2026-11-03-120000 --issue github#12
python3 "$S" commit --message "fix: ..." --file path/to/file --issue github#12
```

## 关闭与恢复

标准关闭链路：

```text
本地 commit
    ↓
git push 到 Gitee origin
    ↓
现有镜像同步到 GitHub main
    ↓
.github/workflows/issue-traceability.yml
    ↓
确认 commit 同时存在于两个平台
    ↓
评论 commit 链接、修改摘要和测试结果
    ↓
关闭 GitHub / Gitee issue
```

失败处理：

- 推送失败：不关闭 issue，修复网络或权限后重试。
- Action 未启用、失败，或提交已推送但 issue 未关闭：

```bash
python3 agent-skills/zenvis-issue-workflow/scripts/zenvis_issue.py reconcile
```

`reconcile` 只会关闭平台 commit API 已确认存在的提交，不会因为本地存在 commit 就提前关闭。

- 评论或关闭 API 暂时失败：失败项写入 `.zenvis/issues/retry-close.json`，修复权限或网络后执行：

```bash
python3 agent-skills/zenvis-issue-workflow/scripts/zenvis_issue.py retry-close
```

- 一个 issue 出现在多个提交中，或一个提交关联多个 issue：脚本拒绝批量关闭。先修正提交或拆分 issue，再重试。

## 安全边界

- token、密码、私钥和带凭据 URL 不得写入代码、报告、issue 正文或提交信息。
- 报告位于公开的 `doc/09-issue-reports/`，只能写脱敏摘要。
- `create-issue` 的正文只保存问题描述和验证结果，不上传 diff、原始日志或完整对话。
- GitHub 和 Gitee 的 issue 数据、评论和状态独立处理，不做跨平台 issue 镜像。
- 代码镜像只代表代码同步，不代表两个平台的 issue 互通。
- 不创建定时 automation；需要“定期整理”时，由维护者在需要的时间主动触发。

## 常见问题

### 为什么 `commit` 只打印命令

这是默认安全边界，避免 Codex 在未授权时改变 Git 历史。直接复制执行输出即可；明确说“由你提交”后才会使用 `--execute`，而且仍然不会推送。

### 为什么 issue 没有自动关闭

优先确认：

1. 提交是否带有正确的 `Zenvis-Issue` 和关闭 trailer。
2. 提交是否已经推送并到达 GitHub `main`。
3. 镜像 Action 是否成功，且配置了 Gitee token。
4. 平台 commit API 是否能查到该 commit。

满足提交存在条件但仍未关闭时，运行 `reconcile`。

### GitHub 返回 404

常见原因是 token 没有目标仓库访问权限、fine-grained PAT 未授权 Issues 权限，或 `ZENVIS_GITHUB_REPO` 不是准确的 `owner/repo`。先运行：

```bash
python3 agent-skills/zenvis-issue-workflow/scripts/zenvis_issue.py doctor
```

不要通过打印 token 排查。

### `DRY RUN` 下没有生成真实 issue

这是预期行为。取消环境变量后重试：

```bash
unset ZENVIS_ISSUE_DRY_RUN
```

### 报告已生成，为什么没有开始改代码

报告生成不等于修复授权。需要先人工删减报告，再明确说“按这份报告修复”。

### Gitee issue ID 不是数字

Gitee 使用字母数字标识，例如 `IKSGM9`。命令和 trailer 中应原样保留，例如 `gitee#IKSGM9`，不要改成数字。

## 开发与验证

本 Skill 的实现和测试都在自己的目录内维护：

```text
agent-skills/zenvis-issue-workflow/
├── SKILL.md
├── README.md
├── agents/openai.yaml
├── scripts/zenvis_issue.py
└── tests/test_zenvis_issue.py
```

运行单元测试：

```bash
python3 -m unittest discover \
  -s agent-skills/zenvis-issue-workflow/tests \
  -p 'test_*.py' -v
```

修改命令、报告格式或关闭逻辑时，同时更新 `SKILL.md`、本 README、`doc/07-开发指南/issue-workflow.md` 和对应测试。

## 相关文档

- [Skill 执行规范](SKILL.md)
- [Issue 归纳与修复工作流](../../doc/07-开发指南/issue-workflow.md)
- [Issue 报告索引](../../doc/09-issue-reports/README.md)
- [项目级 Agent 规范](../../AGENTS.md)
- [镜像关闭 Action](../../.github/workflows/issue-traceability.yml)
