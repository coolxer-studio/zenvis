from __future__ import annotations

import json
import subprocess
import sys
import tempfile
import unittest
from contextlib import redirect_stdout
from datetime import datetime, timezone
from io import StringIO
from pathlib import Path
from unittest import mock

SCRIPTS_DIR = Path(__file__).resolve().parents[1] / "scripts"
sys.path.insert(0, str(SCRIPTS_DIR))

import zenvis_issue as zi


class QueueTransport:
    def __init__(self, responses):
        self.responses = list(responses)
        self.calls = []

    def __call__(self, method, url, headers, data):
        self.calls.append((method, url, headers, data))
        if not self.responses:
            raise AssertionError(f"unexpected request: {method} {url}")
        return self.responses.pop(0)


def response(value, status=200):
    return status, {"content-type": "application/json"}, json.dumps(value).encode()


def make_issue(platform, number, title, body="", labels=()):
    return zi.Issue(
        platform=platform,
        number=zi.normalize_issue_number(platform, number),
        title=title,
        body=body,
        state="open",
        labels=tuple(labels),
        url=f"https://example.test/{platform}/{number}",
        author="tester",
        created_at="2026-11-01T00:00:00+08:00",
        updated_at="2026-11-02T00:00:00+08:00",
    )


class FakeClient:
    def __init__(self, platform="github", repo="owner/repo"):
        self.platform = platform
        self.repo = repo
        self.token = "token"
        self.comments = []
        self.closed = []
        self.close_error = None
        self.created = []
        self.existing_commits = None

    def create_issue(self, title, body, labels=()):
        self.created.append((title, body, tuple(labels)))
        return "42"

    def commit_url(self, sha):
        return f"https://example.test/{self.platform}/commit/{sha}"

    def issue_url(self, number):
        return f"https://example.test/{self.platform}/issues/{number}"

    def list_comments(self, number):
        return [
            {"body": body}
            for issue_number, body in self.comments
            if issue_number == number
        ]

    def comment_issue(self, number, body):
        self.comments.append((number, body))

    def close_issue(self, number):
        if self.close_error:
            raise self.close_error
        self.closed.append(number)

    def commit_exists(self, sha):
        return self.existing_commits is None or sha in self.existing_commits


class RedactionTests(unittest.TestCase):
    def test_redacts_tokens_passwords_and_embedded_credentials(self):
        value = (
            "token=abcdef123456\n"
            "Authorization: Bearer abcdefghijklmnop\n"
            "https://user:supersecret@example.test/repo\n"
            "ghp_abcdefghijklmnopqrstuvwx"
        )
        redacted = zi.redact_sensitive(value)
        self.assertNotIn("abcdef123456", redacted)
        self.assertNotIn("supersecret", redacted)
        self.assertNotIn("ghp_abcdefghijklmnopqrstuvwx", redacted)
        self.assertIn("<REDACTED>", redacted)
        self.assertIn("<TOKEN_REDACTED>", redacted)

    def test_report_body_is_bounded_and_redacted(self):
        body = "需求说明 " * 500 + "\npassword=verysecret123"
        summary = zi.summarize_body(body, limit=120)
        self.assertLessEqual(len(summary), 120)
        self.assertNotIn("verysecret123", summary)


class ApiClientTests(unittest.TestCase):
    def test_github_pagination_filters_pull_requests(self):
        transport = QueueTransport(
            [
                response(
                    [
                        {
                            "number": 2,
                            "title": "真实 issue",
                            "body": "正文",
                            "state": "open",
                            "labels": [{"name": "bug"}],
                            "user": {"login": "alice"},
                            "html_url": "https://github.test/issues/2",
                        },
                        {
                            "number": 3,
                            "title": "PR",
                            "pull_request": {"url": "https://github.test/pulls/3"},
                            "html_url": "https://github.test/pull/3",
                        },
                    ]
                ),
                response(
                    [
                        {
                            "number": 1,
                            "title": "第二页 issue",
                            "body": "",
                            "state": "open",
                            "labels": [],
                            "user": {"login": "bob"},
                            "html_url": "https://github.test/issues/1",
                        }
                    ]
                ),
            ]
        )
        client = zi.PlatformClient("github", "owner/repo", "token", transport)
        issues = client.list_issues(per_page=2)
        self.assertEqual([item.number for item in issues], ["2", "1"])
        self.assertEqual(len(transport.calls), 2)
        self.assertIn("page=1", transport.calls[0][1])
        self.assertIn("page=2", transport.calls[1][1])
        self.assertEqual(transport.calls[0][2]["Authorization"], "Bearer token")

    def test_gitee_pagination_handles_labels_and_pr_urls(self):
        transport = QueueTransport(
            [
                response(
                    [
                        {
                            "number": "IKSGM9",
                            "title": "Gitee issue",
                            "body": "问题",
                            "state": "open",
                            "labels": [{"name": "bug"}, "backend"],
                            "user": {"login": "alice"},
                            "html_url": "https://gitee.test/issues/7",
                        },
                        {
                            "number": 8,
                            "title": "Merge Request",
                            "html_url": "https://gitee.test/pulls/8",
                        },
                    ]
                )
            ]
        )
        client = zi.PlatformClient("gitee", "owner/repo", "token", transport)
        issues = client.list_issues(per_page=100)
        self.assertEqual(len(issues), 1)
        self.assertEqual(issues[0].ref, "gitee#IKSGM9")
        self.assertEqual(issues[0].labels, ("bug", "backend"))
        self.assertEqual(transport.calls[0][2]["Authorization"], "token token")
        self.assertIn("access_token=token", transport.calls[0][1])

    def test_gitee_error_url_redacts_access_token(self):
        transport = QueueTransport([(200, {}, b"not-json")])
        client = zi.PlatformClient("gitee", "owner/repo", "secret-token", transport)
        with self.assertRaises(zi.IssueWorkflowError) as caught:
            client.check_repo()
        self.assertNotIn("secret-token", str(caught.exception))
        self.assertIn("access_token=<REDACTED>", str(caught.exception))

    def test_empty_issue_list(self):
        transport = QueueTransport([response([])])
        client = zi.PlatformClient("github", "owner/repo", "token", transport)
        self.assertEqual(client.list_issues(), [])

    def test_gitee_create_issue_uses_owner_endpoint_and_repo_body_field(self):
        transport = QueueTransport([response({"number": "IKSGM9"})])
        client = zi.PlatformClient("gitee", "owner/repo", "token", transport)
        self.assertEqual(client.create_issue("标题", "正文"), "IKSGM9")
        method, url, _, body = transport.calls[0]
        self.assertEqual(method, "POST")
        self.assertIn("/repos/owner/issues", url)
        payload = json.loads(body.decode("utf-8"))
        self.assertEqual(payload["repo"], "repo")
        self.assertEqual(payload["title"], "标题")

    def test_commit_exists_handles_found_and_missing_sha(self):
        transport = QueueTransport(
            [
                response({"sha": "a" * 40}),
                (404, {}, b'{"message":"Not Found"}'),
            ]
        )
        client = zi.PlatformClient("github", "owner/repo", "token", transport)
        self.assertTrue(client.commit_exists("a" * 40))
        self.assertFalse(client.commit_exists("b" * 40))

    def test_github_create_issue_uses_repository_endpoint(self):
        transport = QueueTransport([response({"number": 7})])
        client = zi.PlatformClient("github", "owner/repo", "token", transport)
        self.assertEqual(client.create_issue("Title", "Body"), "7")
        _, url, _, body = transport.calls[0]
        self.assertIn("/repos/owner/repo/issues", url)
        self.assertNotIn("repo", json.loads(body.decode("utf-8")))


class CreateIssueTests(unittest.TestCase):
    def test_creates_github_issue_and_returns_reference(self):
        client = FakeClient("github", "owner/repo")
        with mock.patch.object(
            zi.PlatformClient, "from_environment", return_value=client
        ):
            result = zi.create_issue_for_request(
                "github",
                "临时图表查询失败",
                "临时图表查询没有返回数据。\npassword=verysecret123",
                verification="单元测试通过",
                labels=["bug", "bug", "ai"],
            )
        self.assertEqual(result["ref"], "github#42")
        self.assertEqual(result["url"], "https://example.test/github/issues/42")
        title, body, labels = client.created[0]
        self.assertEqual(title, "临时图表查询失败")
        self.assertIn("临时图表查询没有返回数据", body)
        self.assertIn("单元测试通过", body)
        self.assertNotIn("verysecret123", body)
        self.assertEqual(labels, ("bug", "ai"))

    def test_creates_gitee_issue_with_alphanumeric_identifier(self):
        client = FakeClient("gitee", "owner/repo")
        with mock.patch.object(
            zi.PlatformClient, "from_environment", return_value=client
        ):
            with mock.patch.object(client, "create_issue", return_value="IKSGM9"):
                result = zi.create_issue_for_request(
                    "gitee", "看板导出失败", "点击导出后没有生成文件。"
                )
        self.assertEqual(result["ref"], "gitee#IKSGM9")
        self.assertEqual(result["url"], "https://example.test/gitee/issues/IKSGM9")

    def test_dry_run_does_not_call_platform(self):
        with mock.patch.object(zi.PlatformClient, "from_environment") as factory:
            result = zi.create_issue_for_request(
                "gitee",
                "刷新失败",
                "刷新按钮无响应。",
                preview=True,
            )
            factory.assert_not_called()
        self.assertTrue(result["dry_run"])
        self.assertNotIn("ref", result)
        self.assertIn("刷新按钮无响应", result["body"])

    def test_missing_token_fails_before_create(self):
        client = FakeClient("github", "owner/repo")
        client.token = ""
        with mock.patch.object(
            zi.PlatformClient, "from_environment", return_value=client
        ):
            with self.assertRaises(zi.IssueWorkflowError):
                zi.create_issue_for_request("github", "标题", "问题摘要")
        self.assertEqual(client.created, [])

    def test_json_output_is_machine_readable(self):
        args = zi.build_parser().parse_args(
            [
                "create-issue",
                "--platform",
                "gitee",
                "--title",
                "刷新失败",
                "--summary",
                "刷新按钮无响应。",
                "--json",
            ]
        )
        result = {
            "dry_run": False,
            "platform": "gitee",
            "number": "IKSGM9",
            "ref": "gitee#IKSGM9",
            "url": "https://gitee.com/owner/repo/issues/IKSGM9",
            "title": "刷新失败",
            "labels": [],
        }
        output = StringIO()
        with mock.patch.object(zi, "create_issue_for_request", return_value=result):
            with redirect_stdout(output):
                self.assertEqual(args.func(args), 0)
        self.assertEqual(json.loads(output.getvalue()), result)


class CommitCommandTests(unittest.TestCase):
    def test_gitee_commit_command_uses_safe_files_and_close_trailer(self):
        _, message = zi.prepare_commit_message(
            ["gitee#IKSGM9"], "fix: 修复临时图表查询\n"
        )
        commands = zi.build_commit_commands(
            ["src/a file.py", "src/it's.py"], message
        )
        self.assertIn("git add -- 'src/a file.py' 'src/it'\"'\"'s.py'", commands)
        self.assertIn("Zenvis-Issue: gitee#IKSGM9", commands)
        self.assertIn("Gitee-Closes: #IKSGM9", commands)
        self.assertNotIn("\ngit push\n", commands)

    def test_github_commit_command_has_no_push(self):
        _, message = zi.prepare_commit_message(
            ["github#12"], "fix: 修复看板刷新\n"
        )
        commands = zi.build_commit_commands(["zenvis-frontend/src/view.ts"], message)
        self.assertIn("Zenvis-Issue: github#12", commands)
        self.assertIn("Closes #12", commands)
        self.assertNotIn("\ngit push\n", commands)

    def test_commit_without_issue_has_no_trailers_or_creation(self):
        message = zi.resolve_commit_message([], "chore: 更新文档\n")
        commands = zi.build_commit_commands(["doc/README.md"], message)
        self.assertIn("chore: 更新文档", commands)
        self.assertNotIn("Zenvis-Issue:", commands)
        self.assertNotIn("Closes #", commands)

    def test_multiple_issue_arguments_are_rejected(self):
        with self.assertRaises(zi.IssueWorkflowError):
            zi.prepare_commit_message(
                ["github#1", "gitee#IKSGM9"], "fix: 多个问题\n"
            )

    def test_conflicting_message_reference_is_rejected(self):
        with self.assertRaises(zi.IssueWorkflowError):
            zi.prepare_commit_message(
                ["gitee#IKSGM9"], "fix: 修复问题\n\nCloses #12\n"
            )

    def test_existing_selected_trailer_is_not_duplicated(self):
        _, message = zi.prepare_commit_message(
            ["gitee#IKSGM9"],
            "fix: 修复问题\n\nZenvis-Issue: gitee#IKSGM9\nGitee-Closes: #IKSGM9\n",
        )
        self.assertEqual(message.count("Zenvis-Issue:"), 1)
        self.assertEqual(message.count("Gitee-Closes:"), 1)

    def test_cli_only_prints_commands(self):
        args = zi.build_parser().parse_args(
            [
                "commit-command",
                "--issue",
                "github#12",
                "--message",
                "fix: 修复看板刷新\n",
                "--file",
                "zenvis-frontend/src/view.ts",
            ]
        )
        output = StringIO()
        with mock.patch("subprocess.run") as run:
            with redirect_stdout(output):
                self.assertEqual(args.func(args), 0)
        run.assert_not_called()
        self.assertIn("git add -- zenvis-frontend/src/view.ts", output.getvalue())
        self.assertIn("Closes #12", output.getvalue())

    def test_commit_alias_accepts_an_unlinked_commit(self):
        args = zi.build_parser().parse_args(
            [
                "commit-command",
                "--message",
                "docs: 更新说明\n",
                "--file",
                "README.md",
            ]
        )
        output = StringIO()
        with redirect_stdout(output):
            self.assertEqual(args.func(args), 0)
        self.assertIn("docs: 更新说明", output.getvalue())
        self.assertNotIn("Zenvis-Issue:", output.getvalue())


class ReportTests(unittest.TestCase):
    def test_report_round_trip_and_user_trimming(self):
        issues = [
            make_issue(
                "github",
                12,
                "看板刷新报错",
                "复现步骤：打开看板\n预期：正常\n实际：报错\n验收：刷新成功",
                ("bug",),
            ),
            make_issue(
                "gitee",
                "IKSGM9",
                "新增导出功能",
                "希望支持导出 CSV。验收：可以下载。",
                ("feature",),
            ),
        ]
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            generated = datetime(2026, 11, 3, 9, 0, 0, tzinfo=timezone.utc)
            report_dir = zi.create_report(issues, root=root, generated_at=generated)
            self.assertTrue((report_dir / "README.md").is_file())
            self.assertTrue((root / zi.REPORT_ROOT / "README.md").is_file())
            parsed = zi.parse_report(report_dir)
            self.assertEqual(
                [item.ref for item in parsed], ["github#12", "gitee#IKSGM9"]
            )

            gitee_path = report_dir / "gitee.md"
            text = gitee_path.read_text(encoding="utf-8")
            before_block = text.split("## gitee#IKSGM9", 1)[0]
            gitee_path.write_text(before_block, encoding="utf-8")
            parsed_after_trim = zi.parse_report(report_dir)
            self.assertEqual([item.ref for item in parsed_after_trim], ["github#12"])

            with self.assertRaises(zi.IssueWorkflowError):
                zi.load_report_issues(root, str(report_dir), ["gitee#IKSGM9"])

    def test_classification_and_duplicate_candidates(self):
        first = make_issue("github", 1, "看板刷新后报错", "复现步骤和预期结果", ("bug",))
        duplicate = make_issue("github", 2, "看板刷新报错", "同样的问题", ("bug",))
        feature = make_issue("github", 3, "新增大屏导出", "希望导出图片", ("feature",))
        self.assertEqual(zi.issue_type(first), "缺陷")
        self.assertEqual(zi.issue_type(feature), "功能")
        self.assertEqual(zi.issue_domain(feature), "前端")
        self.assertIn("github#2", zi.duplicate_candidates(first, [first, duplicate, feature]))

    def test_empty_report_has_both_platform_files(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            report_dir = zi.create_report(
                [], root=root, generated_at=datetime(2026, 11, 3, tzinfo=timezone.utc)
            )
            self.assertEqual(zi.parse_report(report_dir), [])
            self.assertIn(
                "当前没有待处理的 open issue",
                (report_dir / "github.md").read_text(encoding="utf-8"),
            )


class GitTestCase(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)
        subprocess.run(["git", "init", "-q", str(self.root)], check=True)
        subprocess.run(["git", "-C", str(self.root), "config", "user.name", "Tester"], check=True)
        subprocess.run(
            ["git", "-C", str(self.root), "config", "user.email", "tester@example.test"],
            check=True,
        )
        (self.root / "tracked.txt").write_text("before\n", encoding="utf-8")
        subprocess.run(["git", "-C", str(self.root), "add", "tracked.txt"], check=True)
        subprocess.run(
            ["git", "-C", str(self.root), "commit", "-qm", "initial"],
            check=True,
        )
        self.change_index = 0

    def tearDown(self):
        self.temporary.cleanup()

    def stage_change(self):
        self.change_index += 1
        (self.root / "tracked.txt").write_text(
            f"after-{self.change_index}\n", encoding="utf-8"
        )
        subprocess.run(["git", "-C", str(self.root), "add", "tracked.txt"], check=True)

class CommitCommandExecutionTests(GitTestCase):
    def test_generated_commands_create_expected_commit(self):
        (self.root / "a file.txt").write_text("fixed\n", encoding="utf-8")
        _, message = zi.prepare_commit_message(
            ["gitee#IKSGM9"], "fix: 修复临时图表查询\n"
        )
        commands = zi.build_commit_commands(["a file.txt"], message)
        subprocess.run(
            ["bash", "-c", commands],
            cwd=self.root,
            text=True,
            capture_output=True,
            check=True,
        )
        saved = subprocess.run(
            ["git", "-C", str(self.root), "log", "-1", "--format=%B"],
            text=True,
            capture_output=True,
            check=True,
        ).stdout
        self.assertIn("Zenvis-Issue: gitee#IKSGM9", saved)
        self.assertIn("Gitee-Closes: #IKSGM9", saved)

    def test_commit_preview_does_not_stage_or_create_commit(self):
        (self.root / "tracked.txt").write_text("after\n", encoding="utf-8")
        before_status = subprocess.run(
            ["git", "-C", str(self.root), "status", "--porcelain"],
            text=True,
            capture_output=True,
            check=True,
        ).stdout
        before_head = subprocess.run(
            ["git", "-C", str(self.root), "rev-parse", "HEAD"],
            text=True,
            capture_output=True,
            check=True,
        ).stdout
        args = zi.build_parser().parse_args(
            [
                "--root",
                str(self.root),
                "commit",
                "--issue",
                "github#12",
                "--message",
                "fix: 修复刷新\n",
                "--file",
                "tracked.txt",
            ]
        )
        output = StringIO()
        with redirect_stdout(output):
            self.assertEqual(args.func(args), 0)
        self.assertIn("git add -- tracked.txt", output.getvalue())
        self.assertEqual(
            subprocess.run(
                ["git", "-C", str(self.root), "status", "--porcelain"],
                text=True,
                capture_output=True,
                check=True,
            ).stdout,
            before_status,
        )
        self.assertEqual(
            subprocess.run(
                ["git", "-C", str(self.root), "rev-parse", "HEAD"],
                text=True,
                capture_output=True,
                check=True,
            ).stdout,
            before_head,
        )

    def test_commit_execute_commits_with_trailers_without_pushing(self):
        origin = self.root / "origin.git"
        subprocess.run(["git", "init", "--bare", "-q", str(origin)], check=True)
        subprocess.run(
            ["git", "-C", str(self.root), "remote", "add", "origin", str(origin)],
            check=True,
        )
        branch = zi.current_branch(self.root)
        subprocess.run(
            ["git", "-C", str(self.root), "push", "origin", f"HEAD:{branch}"],
            check=True,
        )
        before_remote = subprocess.run(
            ["git", "--git-dir", str(origin), "rev-parse", branch],
            text=True,
            capture_output=True,
            check=True,
        ).stdout
        (self.root / "tracked.txt").write_text("after\n", encoding="utf-8")
        args = zi.build_parser().parse_args(
            [
                "--root",
                str(self.root),
                "commit",
                "--issue",
                "gitee#IKSGM9",
                "--message",
                "fix: 修复刷新\n",
                "--file",
                "tracked.txt",
                "--execute",
            ]
        )
        with redirect_stdout(StringIO()):
            self.assertEqual(args.func(args), 0)
        message = subprocess.run(
            ["git", "-C", str(self.root), "log", "-1", "--format=%B"],
            text=True,
            capture_output=True,
            check=True,
        ).stdout
        self.assertIn("Zenvis-Issue: gitee#IKSGM9", message)
        self.assertIn("Gitee-Closes: #IKSGM9", message)
        self.assertEqual(
            subprocess.run(
                ["git", "--git-dir", str(origin), "rev-parse", branch],
                text=True,
                capture_output=True,
                check=True,
            ).stdout,
            before_remote,
        )


class DoctorTests(GitTestCase):
    def test_warns_about_legacy_hooks_path_without_requiring_hook_files(self):
        subprocess.run(
            ["git", "-C", str(self.root), "config", "core.hooksPath", ".githooks"],
            check=True,
        )
        subprocess.run(
            [
                "git",
                "-C",
                str(self.root),
                "remote",
                "add",
                "origin",
                "https://example.test/owner/repo.git",
            ],
            check=True,
        )
        output = StringIO()
        with redirect_stdout(output):
            result = zi.doctor(self.root, offline=True)
        self.assertEqual(result, 0)
        self.assertIn("[WARN]", output.getvalue())
        self.assertIn("git config --unset core.hooksPath", output.getvalue())


class CloseAndPushTests(GitTestCase):
    def add_bare_origin(self):
        origin = self.root / "origin.git"
        subprocess.run(["git", "init", "--bare", "-q", str(origin)], check=True)
        subprocess.run(
            ["git", "-C", str(self.root), "remote", "add", "origin", str(origin)],
            check=True,
        )
        return origin

    def commit_with_trailer(self, value, trailer):
        self.stage_change()
        subprocess.run(
            [
                "git",
                "-C",
                str(self.root),
                "commit",
                "-qm",
                f"{value}\n\n{trailer}",
            ],
            check=True,
        )

    def test_close_failure_enters_retry_queue_and_retry_succeeds(self):
        client = FakeClient()
        client.close_error = zi.IssueWorkflowError("temporary")
        with self.assertRaises(zi.IssueWorkflowError):
            zi.close_issue_with_trace(
                self.root, client, "github", "11", "a" * 40, "codex/issue-11", "tests passed"
            )
        queue = zi.read_close_queue(self.root)
        self.assertEqual(len(queue), 1)
        self.assertTrue(queue[0]["comment_done"])
        self.assertIn("zenvis-issue-close", queue[0]["comment"])

        client.close_error = None
        with mock.patch.object(zi.PlatformClient, "from_environment", return_value=client):
            self.assertEqual(zi.retry_close_queue(self.root), 0)
        self.assertEqual(client.closed, ["11"])
        self.assertEqual(zi.read_close_queue(self.root), [])

    def test_retry_close_dry_run_does_not_write_platform_or_queue(self):
        client = FakeClient()
        client.close_error = zi.IssueWorkflowError("temporary")
        with self.assertRaises(zi.IssueWorkflowError):
            zi.close_issue_with_trace(
                self.root, client, "github", "12", "b" * 40, "codex/issue-12", "tests"
            )
        with mock.patch.object(zi.PlatformClient, "from_environment") as factory:
            self.assertEqual(zi.retry_close_queue(self.root, preview=True), 0)
            factory.assert_not_called()
        self.assertEqual(len(zi.read_close_queue(self.root)), 1)

    def test_push_rejects_multiple_issue_refs(self):
        self.add_bare_origin()
        self.stage_change()
        subprocess.run(
            [
                "git",
                "-C",
                str(self.root),
                "commit",
                "-qm",
                "修复\n\nZenvis-Issue: github#1\nCloses #1\nGitee-Closes: #2",
            ],
            check=True,
        )
        with self.assertRaises(zi.IssueWorkflowError):
            zi.push_current_branch(
                self.root,
                branch="main",
                verification="",
                no_close=False,
                preview=True,
            )

    def test_push_rejects_same_issue_across_two_commits(self):
        self.add_bare_origin()
        self.commit_with_trailer("第一步", "Zenvis-Issue: github#1\nCloses #1")
        self.commit_with_trailer("第二步", "Zenvis-Issue: github#1\nCloses #1")
        with self.assertRaises(zi.IssueWorkflowError):
            zi.push_current_branch(
                self.root,
                branch="main",
                verification="",
                no_close=False,
                preview=True,
            )

    def test_push_success_closes_gitee_and_leaves_github_to_mirror_action(self):
        origin = self.add_bare_origin()
        self.commit_with_trailer("修复 GitHub 问题", "Zenvis-Issue: github#11\nCloses #11")
        self.commit_with_trailer(
            "修复 Gitee 问题",
            "Zenvis-Issue: gitee#IKSGM9\nGitee-Closes: #IKSGM9",
        )
        clients = {"github": FakeClient("github"), "gitee": FakeClient("gitee")}
        with mock.patch.object(
            zi.PlatformClient,
            "from_environment",
            side_effect=lambda platform: clients[platform],
        ):
            result = zi.push_current_branch(
                self.root,
                branch="main",
                verification="tests passed",
                no_close=False,
                preview=False,
                fetch=False,
            )
        self.assertEqual(result, 0)
        self.assertEqual(clients["github"].closed, [])
        self.assertEqual(clients["gitee"].closed, ["IKSGM9"])
        pushed = subprocess.run(
            ["git", "--git-dir", str(origin), "rev-parse", "refs/heads/main"],
            text=True,
            capture_output=True,
            check=True,
        ).stdout.strip()
        self.assertEqual(
            pushed,
            subprocess.run(
                ["git", "-C", str(self.root), "rev-parse", "HEAD"],
                text=True,
                capture_output=True,
                check=True,
            ).stdout.strip(),
        )

    def test_push_failure_does_not_close_issue(self):
        subprocess.run(
            [
                "git",
                "-C",
                str(self.root),
                "remote",
                "add",
                "origin",
                str(self.root / "missing.git"),
            ],
            check=True,
        )
        self.commit_with_trailer("修复", "Zenvis-Issue: github#12\nCloses #12")
        with mock.patch.object(zi.PlatformClient, "from_environment") as factory:
            with self.assertRaises(zi.IssueWorkflowError):
                zi.push_current_branch(
                    self.root,
                    branch="main",
                    verification="",
                    no_close=False,
                    preview=False,
                    fetch=False,
                )
            factory.assert_not_called()

    def test_reconcile_closes_when_commit_exists_on_platform(self):
        self.commit_with_trailer("修复", "Zenvis-Issue: github#21\nCloses #21")
        client = FakeClient("github")
        with mock.patch.object(zi.PlatformClient, "from_environment", return_value=client):
            result = zi.reconcile_issues(
                self.root,
                limit=10,
                fetch=False,
                verification="tests",
                preview=False,
            )
        self.assertEqual(result, 0)
        self.assertEqual(client.closed, ["21"])

    def test_reconcile_waits_when_mirror_commit_is_missing(self):
        self.commit_with_trailer("修复", "Zenvis-Issue: github#23\nCloses #23")
        client = FakeClient("github")
        client.existing_commits = set()
        with mock.patch.object(zi.PlatformClient, "from_environment", return_value=client):
            result = zi.reconcile_issues(
                self.root,
                limit=10,
                fetch=False,
                verification="tests",
                preview=False,
            )
        self.assertEqual(result, 0)
        self.assertEqual(client.closed, [])

    def test_action_close_closes_both_platforms_after_mirror(self):
        self.commit_with_trailer("修复 GitHub", "Zenvis-Issue: github#22\nCloses #22")
        self.commit_with_trailer(
            "修复 Gitee", "Zenvis-Issue: gitee#IKSGM9\nGitee-Closes: #IKSGM9"
        )
        clients = {"github": FakeClient("github"), "gitee": FakeClient("gitee")}
        with mock.patch.object(
            zi.PlatformClient,
            "from_environment",
            side_effect=lambda platform: clients[platform],
        ):
            result = zi.action_close(
                self.root,
                before="HEAD~2",
                after="HEAD",
                verification="GitHub Actions",
                preview=False,
            )
        self.assertEqual(result, 0)
        self.assertEqual(clients["github"].closed, ["22"])
        self.assertEqual(clients["gitee"].closed, ["IKSGM9"])
        self.assertIn("zenvis-issue-close", clients["gitee"].comments[0][1])

    def test_action_close_rejects_same_issue_across_two_commits(self):
        self.commit_with_trailer("第一步", "Zenvis-Issue: github#24\nCloses #24")
        self.commit_with_trailer("第二步", "Zenvis-Issue: github#24\nCloses #24")
        client = FakeClient("github")
        with mock.patch.object(zi.PlatformClient, "from_environment", return_value=client):
            result = zi.action_close(
                self.root,
                before="HEAD~2",
                after="HEAD",
                verification="GitHub Actions",
                preview=False,
            )
        self.assertEqual(result, 1)
        self.assertEqual(client.closed, [])

    def test_push_preview_accepts_origin_only(self):
        self.add_bare_origin()
        self.stage_change()
        subprocess.run(
            [
                "git",
                "-C",
                str(self.root),
                "commit",
                "-qm",
                "修复\n\nZenvis-Issue: github#1\nCloses #1",
            ],
            check=True,
        )
        with mock.patch.object(zi.PlatformClient, "from_environment") as factory:
            self.assertEqual(
                zi.push_current_branch(
                    self.root,
                    branch="main",
                    verification="tests",
                    no_close=False,
                    preview=True,
                ),
                0,
            )
            factory.assert_not_called()


class RepairCommandTests(GitTestCase):
    def test_repair_json_stdout_contains_only_json(self):
        report_dir = zi.create_report(
            [make_issue("github", 9, "修复刷新", "验收：刷新成功。", ("bug",))],
            root=self.root,
            generated_at=datetime(2026, 11, 3, 9, 30, 0, tzinfo=timezone.utc),
        )
        result = subprocess.run(
            [
                sys.executable,
                str(SCRIPTS_DIR / "zenvis_issue.py"),
                "--root",
                str(self.root),
                "repair",
                "--report",
                str(report_dir),
                "--json",
            ],
            text=True,
            capture_output=True,
            check=False,
        )
        self.assertEqual(result.returncode, 0, result.stderr)
        parsed = json.loads(result.stdout)
        self.assertEqual([item["ref"] for item in parsed], ["github#9"])


if __name__ == "__main__":
    unittest.main()
