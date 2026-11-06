# 贡献指南

首先非常感谢你愿意为本项目贡献代码、文档、Bug 反馈或功能建议！

## 提交 Issue
- 提交 Bug 请尽量描述：复现步骤、预期结果、实际结果、运行环境版本
- 提交功能建议请说明使用场景和价值
- 请避免提交重复 Issue，发帖前先搜索已有 Issue

## 提交 PR（Pull Request）
1. 先 Fork 本项目
2. 新建分支进行开发，不要直接在 main 分支修改
3. 保持代码风格和项目现有风格一致
4. 提交前自测通过，保证能正常编译/运行
5. 提交 PR 请清晰说明：本次改动目的、实现内容、修复了哪些问题

## 代码规范
- 遵循项目现有的编码风格、目录结构
- 新增代码尽量补充必要注释
- 如有文档变更，请同步更新 README 或相关文档

## Issue 归纳与提交追踪

根仓库提供按需生成的 GitHub / Gitee issue 归纳报告，以及提交与 issue 的追踪工具：

```bash
python3 agent-skills/zenvis-issue-workflow/scripts/zenvis_issue.py doctor
python3 agent-skills/zenvis-issue-workflow/scripts/zenvis_issue.py report
```

报告保存到 `doc/09-issue-reports/`，生成阶段只读，不会自动修改代码或关闭 issue。人工删减报告并明确要求修复后，才按最终报告逐个 issue 修改和测试。Codex 默认只输出提交命令；只有明确要求“由你提交”时才执行 `git add` 和 `git commit`，推送始终需要单独授权。完整流程、凭据配置和故障恢复见 [Issue 归纳与修复工作流](doc/07-开发指南/issue-workflow.md)。

如果本地已经完成分析和修复，可以指定平台补建 issue；提交时生成显式关联命令：

```bash
python3 agent-skills/zenvis-issue-workflow/scripts/zenvis_issue.py create-issue --platform gitee \
  --title "问题标题" --summary "脱敏后的问题摘要"
python3 agent-skills/zenvis-issue-workflow/scripts/zenvis_issue.py commit --issue gitee#IKSGM9 \
  --message "fix: 修复问题" --file path/to/changed-file
```

`commit` 默认只输出 `git add` 和 `git commit` 命令，不执行 Git，也不包含推送。用户明确授权后才使用 `--execute`，它只创建本地提交；推送仍需用户单独确认。Issue 修复使用当前分支，不创建 issue 专用分支；每个 issue 一个 commit，也可以生成不关联任何 issue 的普通提交。Gitee 是主提交仓库，正常 `git push` 到 `origin` 后由自动镜像同步到 GitHub，提交到达 `main` 后由镜像 Action 关闭对应 issue。

## 开源协议
本项目基于 [Apache License 2.0](LICENSE) 开源。
提交任何代码、文档、文案贡献，即表示你同意按照 Apache 2.0 协议授权给本项目。

## 提交流程通用参考
1. **Fork 项目**
   点仓库右上角 Fork，把项目复制到自己账号下

2. **克隆自己 Fork 后的仓库**
```bash
git clone 你的fork仓库地址
cd 项目文件夹
```

3. **绑定原项目上游（同步更新）**
```bash
git remote add upstream 原项目官方地址
```

4. **拉取官方最新代码（避免冲突）**
```bash
git fetch upstream
git merge upstream/main
```

5. **新建分支写代码（严禁直接改main）**
```bash
git checkout -b fix/xxx  # 修复bug
# 或
git checkout -b feature/xxx  # 新增功能
```

6. **写完代码提交本地**
```bash
git add .
git commit -m "提交说明：修复xx问题/新增xx功能"
```

7. **推送到自己 Fork 仓库**
```bash
git push origin 你的分支名
```

8. **发起 PR（合并请求）**
回到网页版自己仓库，点 **Compare & pull request**
选择：自己分支 → 官方项目主分支
填写修改说明，提交等待作者审核合并

