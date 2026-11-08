from __future__ import annotations

import json
import os
import subprocess
import sys
import tempfile
import unittest
from contextlib import redirect_stderr, redirect_stdout
from io import StringIO
from pathlib import Path
from unittest import mock

SCRIPTS_DIR = Path(__file__).resolve().parents[1] / "scripts"
sys.path.insert(0, str(SCRIPTS_DIR))

import zenvis_issue as zi


def response(value, status=200):
    return status, {"content-type": "application/json"}, json.dumps(value).encode()


class QueueTransport:
    def __init__(self, responses):
        self.responses = list(responses)
        self.calls = []

    def __call__(self, method, url, headers, data):
        self.calls.append((method, url, headers, data))
        if not self.responses:
            raise AssertionError(f"unexpected request: {method} {url}")
        return self.responses.pop(0)


def run_git(root, *args, check=True):
    return subprocess.run(
        ["git", "-C", str(root), *args],
        text=True,
        capture_output=True,
        check=check,
    )


def write_release_fixture(root: Path, version: str = "1.1.5.alpha") -> None:
    branch = f"feature/{version}"
    files = {
        "deploy/.env.example": f"ARCH=amd64\nZENVIS_VERSION={version}\nVECTUM_VERSION=1.0.2\n",
        "zenvis-backend/build.sh": f'#!/bin/bash\nIMAGE_TAG="{version}"\n',
        "zenvis-frontend/build.sh": f'#!/bin/bash\nIMAGE_TAG="{version}"\n',
        "zenvis-backend/src/main/java/com/coolxer/component/DataInitiator.java": (
            "class DataInitiator {\n"
            f'    void init() {{ systemInfo.setProductVersion("{version}"); }}\n'
            "}\n"
        ),
        "doc/_coverpage.md": f"# ZenVis\n# 配置驱动的数据分析框架 <small>{version}</small>\n",
        "README.md": (
            "```bash\n"
            f"curl -fsSL https://gitee.com/coolxer-studio/zenvis/raw/{branch}/quick-deploy.sh | bash\n"
            "```\n"
        ),
        "quick-deploy.sh": (
            "#!/usr/bin/env bash\n"
            f'readonly DEFAULT_REPO_BRANCH="{branch}"\n'
        ),
        "doc/02-安装部署与升级/快速开始.md": (
            "```bash\n"
            f"curl -fsSL https://gitee.com/coolxer-studio/zenvis/raw/{branch}/quick-deploy.sh | bash\n"
            f"git checkout {branch}\n"
            "```\n"
        ),
        "doc/02-安装部署与升级/升级与回滚.md": (
            "```bash\n"
            f"git fetch --depth=1 origin refs/heads/{branch}:refs/remotes/origin/{branch}\n"
            f"git switch -c {branch} refs/remotes/origin/{branch}\n"
            f"./zenvisctl compose upgrade --version {version}\n"
            f"  --version {version} \\\n"
            "```\n"
        ),
        "doc/_sidebar.md": (
            "- [开发指南](/07-开发指南/README.md)\n"
            "- [Issue 报告](/09-issue-reports/README.md)\n"
        ),
        ".gitignore": "/.zenvis/\n",
    }
    for relative, content in files.items():
        path = root / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8")


class FakeReleaseClient:
    def __init__(self, platform: str, token: str = "token"):
        self.platform = platform
        self.repo = "owner/repo"
        self.token = token
        self.releases: dict[str, dict] = {}
        self.created: list[dict] = []
        self.tag_ready = True
        self.fail_create = None

    def check_repo(self):
        return {}

    def get_release(self, tag):
        return self.releases.get(tag)

    def create_release(
        self,
        tag,
        name,
        body,
        *,
        target_commitish="",
        prerelease=False,
    ):
        if self.fail_create:
            raise self.fail_create
        record = {
            "tag_name": tag,
            "name": name,
            "body": body,
            "target_commitish": target_commitish,
            "prerelease": prerelease,
            "html_url": f"https://example.test/{self.platform}/releases/{tag}",
        }
        self.created.append(record)
        self.releases[tag] = record
        return record

    def tag_exists(self, tag):
        return self.tag_ready


class ReleaseGitTestCase(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)
        run_git(self.root, "init", "-q", "-b", "main")
        run_git(self.root, "config", "user.name", "Tester")
        run_git(self.root, "config", "user.email", "tester@example.test")
        write_release_fixture(self.root)
        run_git(self.root, "add", "-A")
        run_git(self.root, "commit", "-qm", "初始化 1.1.5.alpha")
        self.origin = self.root / "origin.git"
        subprocess.run(["git", "init", "--bare", "-q", str(self.origin)], check=True)
        run_git(self.root, "remote", "add", "origin", str(self.origin))
        run_git(self.root, "push", "-q", "origin", "HEAD:refs/heads/main")

    def tearDown(self):
        self.temporary.cleanup()

    def tag_current(self, tag: str):
        run_git(self.root, "tag", "-a", tag, "-m", tag)
        run_git(self.root, "push", "-q", "origin", f"refs/tags/{tag}")

    def commit(self, message: str):
        run_git(self.root, "commit", "--allow-empty", "-qm", message)

    def clients(self):
        return {
            "gitee": FakeReleaseClient("gitee"),
            "github": FakeReleaseClient("github"),
        }

    def patch_clients(self, clients):
        return mock.patch.object(
            zi.PlatformClient,
            "from_environment",
            side_effect=lambda platform: clients[platform],
        )


class ReleaseVersionTests(unittest.TestCase):
    def test_parse_and_order(self):
        alpha = zi.parse_release_version("1.1.5.alpha")
        stable = zi.parse_release_version("1.1.5")
        self.assertEqual(alpha.text, "1.1.5.alpha")
        self.assertLess(alpha.key, stable.key)
        self.assertEqual(zi.next_patch_version(alpha).text, "1.1.6.alpha")

    def test_rejects_invalid_version(self):
        with self.assertRaises(zi.IssueWorkflowError):
            zi.parse_release_version("v1.1")


class VersionResolutionTests(ReleaseGitTestCase):
    def test_lists_release_tags_sorted_and_ignores_plugin_tags(self):
        self.tag_current("1.1.5")
        self.tag_current("1.1.4.alpha")
        self.tag_current("zenvis-plugin-demo-9.9.9")
        versions = zi.list_release_tags(self.root)
        self.assertEqual(
            [version.text for version in versions],
            ["1.1.4.alpha", "1.1.5"],
        )

    def test_declared_version_ahead_of_latest_tag_is_released_directly(self):
        self.tag_current("1.1.4.alpha")
        version, declared, latest = zi.resolve_release_version(self.root, None)
        self.assertEqual(version.text, "1.1.5.alpha")
        self.assertEqual(declared.text, "1.1.5.alpha")
        self.assertEqual(latest.text, "1.1.4.alpha")

    def test_declared_version_equal_to_latest_tag_bumps_patch(self):
        self.tag_current("1.1.5.alpha")
        version, _, _ = zi.resolve_release_version(self.root, None)
        self.assertEqual(version.text, "1.1.6.alpha")

    def test_declared_version_behind_latest_tag_fails(self):
        self.tag_current("1.1.6.alpha")
        with self.assertRaises(zi.IssueWorkflowError):
            zi.resolve_release_version(self.root, None)

    def test_explicit_version_must_be_newer(self):
        self.tag_current("1.1.5.alpha")
        with self.assertRaises(zi.IssueWorkflowError):
            zi.resolve_release_version(self.root, "1.1.5.alpha")
        with self.assertRaises(zi.IssueWorkflowError):
            zi.resolve_release_version(self.root, "1.1.4.alpha")
        version, _, _ = zi.resolve_release_version(self.root, "1.2.0.alpha")
        self.assertEqual(version.text, "1.2.0.alpha")


class VersionTargetTests(ReleaseGitTestCase):
    def test_updates_all_version_files_and_follows_feature_branch(self):
        zi.apply_version_targets(self.root, "1.1.6.alpha", "feature/1.1.6.alpha")
        combined = "\n".join(
            (self.root / target.path).read_text(encoding="utf-8")
            for target in zi.RELEASE_TARGETS
        )
        self.assertIn("1.1.6.alpha", combined)
        self.assertNotIn("1.1.5.alpha", combined)
        self.assertIn("feature/1.1.6.alpha", combined)
        self.assertNotIn("feature/1.1.5.alpha", combined)
        upgrade = (
            self.root / "doc/02-安装部署与升级/升级与回滚.md"
        ).read_text(encoding="utf-8")
        self.assertIn(
            "git switch -c feature/1.1.6.alpha "
            "refs/remotes/origin/feature/1.1.6.alpha",
            upgrade,
        )

    def test_main_branch_replaces_feature_references(self):
        zi.apply_version_targets(self.root, "1.1.6.alpha", "main")
        readme = (self.root / "README.md").read_text(encoding="utf-8")
        quick = (self.root / "quick-deploy.sh").read_text(encoding="utf-8")
        upgrade = (
            self.root / "doc/02-安装部署与升级/升级与回滚.md"
        ).read_text(encoding="utf-8")
        self.assertIn("/raw/main/quick-deploy.sh", readme)
        self.assertIn('DEFAULT_REPO_BRANCH="main"', quick)
        self.assertIn("git switch main", upgrade)
        self.assertNotIn("git switch -c main", upgrade)

    def test_missing_pattern_fails_instead_of_silently_skipping(self):
        path = self.root / "zenvis-frontend/build.sh"
        path.write_text("#!/bin/bash\n", encoding="utf-8")
        before = (self.root / "README.md").read_text(encoding="utf-8")
        with self.assertRaises(zi.IssueWorkflowError):
            zi.apply_version_targets(self.root, "1.1.6.alpha", "main")
        self.assertEqual(
            (self.root / "README.md").read_text(encoding="utf-8"), before
        )

    def test_preserves_executable_mode(self):
        path = self.root / "quick-deploy.sh"
        os.chmod(path, 0o755)
        zi.apply_version_targets(self.root, "1.1.6.alpha", "main")
        self.assertEqual(path.stat().st_mode & 0o777, 0o755)


class ReleaseNotesTests(ReleaseGitTestCase):
    def test_groups_commits_and_excludes_release_commits(self):
        self.tag_current("1.1.4.alpha")
        self.commit("feat: 新增导出能力")
        self.commit("fix: 修复刷新失败")
        self.commit("docs: 更新部署文档")
        self.commit("chore: 调整构建脚本")
        self.commit("chore(release): 发布 1.1.5.alpha")
        notes = zi.render_release_notes(
            self.root,
            zi.parse_release_version("1.1.5.alpha"),
            zi.parse_release_version("1.1.4.alpha"),
        )
        self.assertIn("## 新增能力", notes)
        self.assertIn("新增导出能力", notes)
        self.assertIn("## 问题修复", notes)
        self.assertIn("## 文档与工程", notes)
        self.assertNotIn("chore(release)", notes)

    def test_redacts_sensitive_commit_subjects(self):
        self.tag_current("1.1.4.alpha")
        self.commit("fix: password=abcdef123456 泄漏")
        notes = zi.render_release_notes(
            self.root,
            zi.parse_release_version("1.1.5.alpha"),
            zi.parse_release_version("1.1.4.alpha"),
        )
        self.assertNotIn("abcdef123456", notes)
        self.assertIn("<REDACTED>", notes)


class ReleasePrepareTests(ReleaseGitTestCase):
    def test_prepare_command_writes_only_gitignored_draft(self):
        before_head = run_git(self.root, "rev-parse", "HEAD").stdout
        before_status = run_git(self.root, "status", "--porcelain").stdout
        args = zi.build_parser().parse_args(
            ["--root", str(self.root), "release", "--no-fetch"]
        )
        output = StringIO()
        with redirect_stdout(output):
            self.assertEqual(args.func(args), 0)
        self.assertEqual(run_git(self.root, "rev-parse", "HEAD").stdout, before_head)
        self.assertEqual(run_git(self.root, "status", "--porcelain").stdout, before_status)
        draft = self.root / zi.RELEASE_STATE_ROOT / "1.1.5.alpha.md"
        self.assertTrue(draft.is_file())
        self.assertIn("1.1.5.alpha", output.getvalue())

    def test_prepare_json_is_machine_readable(self):
        result = zi.release_plan(
            self.root,
            version_value=None,
            notes_value=None,
            fetch=False,
        )
        payload = json.loads(json.dumps(result, ensure_ascii=False))
        self.assertEqual(payload["version"], "1.1.5.alpha")
        self.assertIn("deploy/.env.example", payload["files"])

    def test_prepare_refuses_tracked_output_path(self):
        args = zi.build_parser().parse_args(
            [
                "--root",
                str(self.root),
                "release",
                "--notes",
                "doc/review.md",
                "--no-fetch",
            ]
        )
        with redirect_stdout(StringIO()):
            with self.assertRaises(zi.IssueWorkflowError):
                args.func(args)
        self.assertFalse((self.root / "doc/review.md").exists())


class ReleaseExecuteTests(ReleaseGitTestCase):
    def prepare_notes(self, tag="1.1.6.alpha"):
        path = self.root / zi.RELEASE_STATE_ROOT / f"{tag}.md"
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(
            f"# ZenVis {tag} 版本发布说明\n\n## 版本重点\n\n- 自动化发布。\n",
            encoding="utf-8",
        )
        return path

    def test_execute_commits_tags_pushes_and_publishes(self):
        self.tag_current("1.1.5.alpha")
        notes = self.prepare_notes()
        clients = self.clients()
        (self.root / "scratch.txt").write_text("untracked\n", encoding="utf-8")
        args = zi.build_parser().parse_args(
            [
                "--root",
                str(self.root),
                "release",
                "--execute",
                "--notes",
                str(notes),
                "--no-fetch",
            ]
        )
        output = StringIO()
        with self.clients_patch(clients), redirect_stdout(output):
            self.assertEqual(args.func(args), 0)
        message = run_git(self.root, "log", "-1", "--format=%B").stdout
        self.assertIn("chore(release): 发布 1.1.6.alpha", message)
        self.assertEqual(
            run_git(self.root, "cat-file", "-t", "1.1.6.alpha").stdout.strip(),
            "tag",
        )
        tag_message = run_git(self.root, "tag", "-l", "-n99", "1.1.6.alpha").stdout
        self.assertIn("自动化发布", tag_message)
        self.assertTrue((self.root / zi.RELEASE_DOC_ROOT / "1.1.6.alpha.md").is_file())
        self.assertIn(
            "1.1.6.alpha",
            (self.root / zi.RELEASE_DOC_ROOT / "README.md").read_text(encoding="utf-8"),
        )
        self.assertIn(
            "版本发布",
            (self.root / "doc/_sidebar.md").read_text(encoding="utf-8"),
        )
        remote_branch = run_git(
            self.origin, "rev-parse", "refs/heads/main"
        ).stdout.strip()
        self.assertEqual(remote_branch, run_git(self.root, "rev-parse", "HEAD").stdout.strip())
        remote_tag = run_git(
            self.origin, "rev-parse", "refs/tags/1.1.6.alpha"
        ).stdout.strip()
        self.assertTrue(remote_tag)
        self.assertEqual(clients["gitee"].created[0]["prerelease"], True)
        self.assertEqual(clients["github"].created[0]["prerelease"], True)
        state = zi.read_release_state(self.root, "1.1.6.alpha")
        self.assertEqual(state["git"]["pushed"], True)
        self.assertEqual(state["platforms"]["gitee"]["status"], "created")
        committed = set(
            run_git(
                self.root,
                "-c",
                "core.quotepath=false",
                "show",
                "--pretty=",
                "--name-only",
                "HEAD",
            )
            .stdout.splitlines()
        )
        self.assertNotIn("scratch.txt", committed)
        self.assertNotIn(".zenvis/releases/1.1.6.alpha.md", committed)
        self.assertIn("doc/10-版本发布/1.1.6.alpha.md", committed)
        self.assertIn("deploy/.env.example", committed)

    def test_execute_requires_reviewed_notes(self):
        self.tag_current("1.1.5.alpha")
        before = run_git(self.root, "rev-parse", "HEAD").stdout
        with self.assertRaises(zi.IssueWorkflowError):
            zi.execute_release(
                self.root,
                version_value=None,
                notes_value=None,
                fetch=False,
                preview=False,
            )
        self.assertEqual(run_git(self.root, "rev-parse", "HEAD").stdout, before)

    def test_unreviewed_draft_marker_blocks_release(self):
        self.tag_current("1.1.5.alpha")
        notes = self.prepare_notes()
        notes.write_text(
            f"# ZenVis 1.1.6.alpha\n\n{zi.RELEASE_REVIEW_MARKER}\n",
            encoding="utf-8",
        )
        clients = self.clients()
        before = run_git(self.root, "rev-parse", "HEAD").stdout
        with self.clients_patch(clients):
            with self.assertRaises(zi.IssueWorkflowError):
                zi.execute_release(
                    self.root,
                    version_value=None,
                    notes_value=str(notes),
                    fetch=False,
                    preview=False,
                )
        self.assertEqual(run_git(self.root, "rev-parse", "HEAD").stdout, before)

    def test_existing_release_blocks_before_git_write(self):
        self.tag_current("1.1.5.alpha")
        notes = self.prepare_notes()
        clients = self.clients()
        clients["github"].releases["1.1.6.alpha"] = {"body": "existing"}
        before = run_git(self.root, "rev-parse", "HEAD").stdout
        with self.clients_patch(clients):
            with self.assertRaises(zi.IssueWorkflowError):
                zi.execute_release(
                    self.root,
                    version_value=None,
                    notes_value=str(notes),
                    fetch=False,
                    preview=False,
                )
        self.assertEqual(run_git(self.root, "rev-parse", "HEAD").stdout, before)

    def test_dirty_tracked_worktree_blocks_release(self):
        self.tag_current("1.1.5.alpha")
        notes = self.prepare_notes()
        (self.root / "README.md").write_text("dirty\n", encoding="utf-8")
        clients = self.clients()
        with self.clients_patch(clients):
            with self.assertRaises(zi.IssueWorkflowError):
                zi.execute_release(
                    self.root,
                    version_value=None,
                    notes_value=str(notes),
                    fetch=False,
                    preview=False,
                )
        self.assertFalse((self.root / zi.RELEASE_DOC_ROOT / "1.1.6.alpha.md").exists())

    def test_missing_token_blocks_before_git_write(self):
        self.tag_current("1.1.5.alpha")
        notes = self.prepare_notes()
        clients = self.clients()
        clients["github"].token = ""
        before = run_git(self.root, "rev-parse", "HEAD").stdout
        with self.clients_patch(clients):
            with self.assertRaises(zi.IssueWorkflowError):
                zi.execute_release(
                    self.root,
                    version_value=None,
                    notes_value=str(notes),
                    fetch=False,
                    preview=False,
                )
        self.assertEqual(run_git(self.root, "rev-parse", "HEAD").stdout, before)

    def test_remote_tag_conflict_blocks_prepare(self):
        self.tag_current("1.1.5.alpha")
        run_git(self.root, "tag", "1.1.6.alpha")
        run_git(self.root, "push", "-q", "origin", "refs/tags/1.1.6.alpha")
        run_git(self.root, "tag", "-d", "1.1.6.alpha")
        with self.assertRaises(zi.IssueWorkflowError):
            zi.release_plan(
                self.root,
                version_value=None,
                notes_value=None,
                fetch=False,
            )

    def test_dry_run_does_not_write_or_call_platform(self):
        self.tag_current("1.1.5.alpha")
        notes = self.prepare_notes()
        before = run_git(self.root, "rev-parse", "HEAD").stdout
        with mock.patch.dict(os.environ, {"ZENVIS_ISSUE_DRY_RUN": "1"}):
            with mock.patch.object(zi.PlatformClient, "from_environment") as factory:
                result = zi.execute_release(
                    self.root,
                    version_value=None,
                    notes_value=str(notes),
                    fetch=False,
                    preview=True,
                )
                factory.assert_not_called()
        self.assertTrue(result["preview"])
        self.assertEqual(run_git(self.root, "rev-parse", "HEAD").stdout, before)
        self.assertFalse((self.root / zi.RELEASE_DOC_ROOT / "1.1.6.alpha.md").exists())

    def clients_patch(self, clients):
        return mock.patch.object(
            zi.PlatformClient,
            "from_environment",
            side_effect=lambda platform: clients[platform],
        )


class ReleasePublishTests(ReleaseGitTestCase):
    def notes(self):
        path = self.root / zi.RELEASE_DOC_ROOT / "1.1.6.alpha.md"
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text("# ZenVis 1.1.6.alpha\n\n- 修复问题\n", encoding="utf-8")
        return path

    def test_creates_both_platforms_and_waits_for_github_tag(self):
        clients = self.clients()
        clients["github"].tag_ready = True
        with self.patch_clients(clients):
            result = zi.publish_release(
                self.root,
                tag="1.1.6.alpha",
                commit="a" * 40,
                notes=str(self.notes()),
                github_timeout=1,
            )
        self.assertTrue(result["ok"])
        self.assertEqual([item["tag_name"] for item in clients["gitee"].created], ["1.1.6.alpha"])
        self.assertEqual([item["tag_name"] for item in clients["github"].created], ["1.1.6.alpha"])
        self.assertEqual(clients["gitee"].created[0]["prerelease"], True)
        self.assertEqual(clients["gitee"].created[0]["target_commitish"], "a" * 40)

    def test_github_timeout_falls_back_to_commit_sha(self):
        clients = self.clients()
        clients["github"].tag_ready = False
        ticks = iter([0.0, 0.0, 10.0])
        with self.patch_clients(clients):
            result = zi.publish_release(
                self.root,
                tag="1.1.6.alpha",
                commit="b" * 40,
                notes=str(self.notes()),
                github_timeout=5,
                github_interval=1,
                sleep=lambda _: None,
                clock=lambda: next(ticks),
            )
        self.assertTrue(result["ok"])
        self.assertEqual(clients["github"].created[0]["target_commitish"], "b" * 40)

    def test_existing_identical_release_is_skipped(self):
        clients = self.clients()
        body = self.notes().read_text(encoding="utf-8").strip()
        for client in clients.values():
            client.releases["1.1.6.alpha"] = {
                "body": body.replace("\n", "\r\n"),
                "html_url": "https://example.test/existing",
            }
        with self.patch_clients(clients):
            result = zi.publish_release(
                self.root,
                tag="1.1.6.alpha",
                commit="c" * 40,
                notes=str(self.notes()),
            )
        self.assertTrue(result["ok"])
        self.assertEqual(clients["gitee"].created, [])
        self.assertEqual(clients["github"].created, [])
        self.assertEqual(result["platforms"]["gitee"]["status"], "exists")

    def test_different_existing_release_fails(self):
        clients = self.clients()
        clients["gitee"].releases["1.1.6.alpha"] = {"body": "旧说明"}
        with self.patch_clients(clients):
            with self.assertRaises(zi.IssueWorkflowError):
                zi.publish_release(
                    self.root,
                    tag="1.1.6.alpha",
                    commit="d" * 40,
                    notes=str(self.notes()),
                )
        self.assertEqual(clients["gitee"].created, [])
        self.assertEqual(clients["github"].created, [])

    def test_partial_failure_can_be_retried_without_duplicate_release(self):
        clients = self.clients()
        clients["github"].fail_create = zi.IssueWorkflowError("temporary")
        with self.patch_clients(clients):
            first = zi.publish_release(
                self.root,
                tag="1.1.6.alpha",
                commit="e" * 40,
                notes=str(self.notes()),
            )
        self.assertFalse(first["ok"])
        clients["github"].fail_create = None
        with self.patch_clients(clients):
            second = zi.publish_release(
                self.root,
                tag="1.1.6.alpha",
                commit="e" * 40,
                notes=str(self.notes()),
            )
        self.assertTrue(second["ok"])
        self.assertEqual(len(clients["gitee"].created), 1)
        self.assertEqual(len(clients["github"].created), 1)
        state = zi.read_release_state(self.root, "1.1.6.alpha")
        self.assertEqual(state["platforms"]["github"]["status"], "created")

    def test_publish_preview_does_not_call_platform(self):
        with mock.patch.object(zi.PlatformClient, "from_environment") as factory:
            result = zi.publish_release(
                self.root,
                tag="1.1.6.alpha",
                commit="f" * 40,
                notes=str(self.notes()),
                preview=True,
            )
            factory.assert_not_called()
        self.assertTrue(result["ok"])
        self.assertEqual(result["platforms"]["github"]["status"], "would_create")

    def test_publish_rejects_unreviewed_notes(self):
        notes = self.notes()
        notes.write_text(
            f"# ZenVis 1.1.6.alpha\n\n{zi.RELEASE_REVIEW_MARKER}\n",
            encoding="utf-8",
        )
        with mock.patch.object(zi.PlatformClient, "from_environment") as factory:
            with self.assertRaises(zi.IssueWorkflowError):
                zi.publish_release(
                    self.root,
                    tag="1.1.6.alpha",
                    commit="a" * 40,
                    notes=str(notes),
                )
            factory.assert_not_called()


class ReleaseApiTests(unittest.TestCase):
    def test_github_missing_release_returns_none(self):
        transport = QueueTransport([response({"message": "Not Found"}, 404)])
        client = zi.PlatformClient(
            "github", "owner/repo", "token", transport=transport
        )
        self.assertIsNone(client.get_release("1.1.6.alpha"))
        self.assertIn("/releases/tags/1.1.6.alpha", transport.calls[0][1])

    def test_gitee_create_release_payload(self):
        transport = QueueTransport([response({"id": 1, "html_url": "https://example.test"})])
        client = zi.PlatformClient(
            "gitee", "owner/repo", "token", transport=transport
        )
        client.create_release(
            "1.1.6.alpha",
            "1.1.6.alpha",
            "说明",
            prerelease=True,
        )
        method, url, _, data = transport.calls[0]
        payload = json.loads(data.decode("utf-8"))
        self.assertEqual(method, "POST")
        self.assertTrue(url.startswith("https://gitee.com/api/v5/repos/owner/repo/releases"))
        self.assertIn("access_token=token", url)
        self.assertEqual(payload["tag_name"], "1.1.6.alpha")
        self.assertTrue(payload["prerelease"])

    def test_github_create_release_payload_uses_target_commitish(self):
        transport = QueueTransport([response({"id": 2, "html_url": "https://example.test"})])
        client = zi.PlatformClient(
            "github", "owner/repo", "token", transport=transport
        )
        client.create_release(
            "1.1.6.alpha",
            "1.1.6.alpha",
            "说明",
            target_commitish="a" * 40,
            prerelease=True,
        )
        payload = json.loads(transport.calls[0][3].decode("utf-8"))
        self.assertEqual(payload["target_commitish"], "a" * 40)
        self.assertTrue(payload["prerelease"])


if __name__ == "__main__":
    unittest.main()
