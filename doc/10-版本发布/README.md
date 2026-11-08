# 版本发布说明

本目录保存 ZenVis 各版本的发布说明。

发布说明由 `zenvis-issue-workflow` 的 `release` 模式根据提交记录生成初稿，经人工审核后发布。发布说明同时写入 annotated tag、Gitee Release 和 GitHub Release。

## 发布规则

- 默认只生成 `.zenvis/releases/<tag>.md` 草稿，不修改仓库文件、不提交、不打 tag、不推送。
- 明确授权后执行 `release --execute --notes <path>`，更新固定版本引用、写入本目录、提交并发布。
- `.alpha`、`.beta`、`.rc` 版本在两个平台标记 prerelease。
- Docker 镜像构建/推送、Helm 版本更新和 `VECTUM_VERSION` 不属于该流程。

完整命令和故障恢复见 [Issue 归纳与修复工作流](/07-开发指南/issue-workflow.md)。

<!-- releases:start -->
- [1.1.5.alpha](1.1.5.alpha.md) — 2026-11-08
<!-- releases:end -->
