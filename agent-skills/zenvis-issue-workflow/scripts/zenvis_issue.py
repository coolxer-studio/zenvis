#!/usr/bin/env python3

from __future__ import annotations

import argparse
import json
import os
import re
import shlex
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import asdict, dataclass
from datetime import datetime
from difflib import SequenceMatcher
from pathlib import Path
from typing import Any, Callable, Iterable, Optional, Sequence


DEFAULT_GITHUB_REPO = "coolxer-studio/zenvis"
DEFAULT_GITEE_REPO = "coolxer-studio/zenvis"
REPORT_ROOT = Path("doc") / "09-issue-reports"
STATE_ROOT = Path(".zenvis") / "issues"
RELEASE_DOC_ROOT = Path("doc") / "10-版本发布"
RELEASE_STATE_ROOT = Path(".zenvis") / "releases"
LOCAL_ENV_FILE = Path(".zenvis") / ".env"
RELEASE_VERSION_RE = re.compile(
    r"^(\d+)\.(\d+)\.(\d+)(?:\.(alpha|beta|rc)(\d*))?$"
)
ISSUE_ID_PATTERN = r"[A-Za-z0-9][A-Za-z0-9_-]*"
ISSUE_REF_RE = re.compile(
    rf"(?<![\w-])(github|gitee)#({ISSUE_ID_PATTERN})(?![\w-])",
    re.IGNORECASE,
)
TRAILER_RE = re.compile(
    rf"(?im)^Zenvis-Issue:\s*(github|gitee)#({ISSUE_ID_PATTERN})\s*$"
)
GITHUB_CLOSES_RE = re.compile(r"(?im)^(?:Closes|Fixes|Resolves)\s+#(\d+)\s*$")
GITEE_CLOSES_RE = re.compile(
    rf"(?im)^Gitee-Closes:\s*#({ISSUE_ID_PATTERN})\s*$"
)
REPORT_BLOCK_RE = re.compile(
    rf"(?m)^##\s+(github|gitee)#({ISSUE_ID_PATTERN})\s+[—-]\s+(.+?)\s*$"
)
RELEASE_INDEX_MARKER = "<!-- releases:start -->"
RELEASE_REVIEW_MARKER = "请审核提交清单，补充面向用户的版本重点后再执行发布。"


@dataclass(frozen=True)
class ReleaseVersion:
    major: int
    minor: int
    patch: int
    qualifier: str = ""
    qualifier_number: int = 0

    @property
    def text(self) -> str:
        suffix = (
            f".{self.qualifier}{self.qualifier_number or ''}"
            if self.qualifier
            else ""
        )
        return f"{self.major}.{self.minor}.{self.patch}{suffix}"

    @property
    def key(self) -> tuple[int, int, int, int, int]:
        rank = {"": 3, "rc": 2, "beta": 1, "alpha": 0}.get(self.qualifier, 0)
        return (self.major, self.minor, self.patch, rank, self.qualifier_number)

    @property
    def prerelease(self) -> bool:
        return bool(self.qualifier)


@dataclass(frozen=True)
class ReleaseTarget:
    path: str
    patterns: tuple[tuple[str, str], ...]


RELEASE_TARGETS: tuple[ReleaseTarget, ...] = (
    ReleaseTarget(
        "deploy/.env.example",
        ((r"(?m)^ZENVIS_VERSION=.*$", "ZENVIS_VERSION={version}"),),
    ),
    ReleaseTarget(
        "zenvis-backend/build.sh",
        ((r'(?m)^IMAGE_TAG="[^"]*"$', 'IMAGE_TAG="{version}"'),),
    ),
    ReleaseTarget(
        "zenvis-frontend/build.sh",
        ((r'(?m)^IMAGE_TAG="[^"]*"$', 'IMAGE_TAG="{version}"'),),
    ),
    ReleaseTarget(
        "zenvis-backend/src/main/java/com/coolxer/component/DataInitiator.java",
        ((r'setProductVersion\("[^"]*"\)', 'setProductVersion("{version}")'),),
    ),
    ReleaseTarget(
        "doc/_coverpage.md",
        ((r"<small>[^<]*</small>", "<small>{version}</small>"),),
    ),
    ReleaseTarget(
        "README.md",
        ((r"(/raw/)([^\s]+?)(/quick-deploy\.sh)", r"\g<1>{branch}\g<3>"),),
    ),
    ReleaseTarget(
        "quick-deploy.sh",
        (
            (
                r'(?m)^(readonly DEFAULT_REPO_BRANCH=")[^"]*(")$',
                r"\g<1>{branch}\g<2>",
            ),
        ),
    ),
    ReleaseTarget(
        "doc/02-安装部署与升级/快速开始.md",
        (
            (r"(/raw/)([^\s]+?)(/quick-deploy\.sh)", r"\g<1>{branch}\g<3>"),
            (r"(?m)^(git checkout )\S+$", r"\g<1>{branch}"),
        ),
    ),
    ReleaseTarget(
        "doc/02-安装部署与升级/升级与回滚.md",
        (
            (
                r"refs/heads/\S+?(:refs/remotes/origin/)\S+",
                r"refs/heads/{branch}\g<1>{branch}",
            ),
            (
                r"git switch -c \S+ refs/remotes/origin/\S+",
                "git switch -c {branch} refs/remotes/origin/{branch}",
            ),
            (r"(?m)(--version )\S+", r"\g<1>{version}"),
        ),
    ),
)


class IssueWorkflowError(RuntimeError):
    pass


class IssueApiError(IssueWorkflowError):
    def __init__(self, status: int, message: str, url: str = "") -> None:
        super().__init__(f"HTTP {status}: {message}")
        self.status = status
        self.url = url


@dataclass(frozen=True)
class Issue:
    platform: str
    number: str
    title: str
    body: str
    state: str
    labels: tuple[str, ...]
    url: str
    author: str
    created_at: str
    updated_at: str

    @property
    def ref(self) -> str:
        return f"{self.platform}#{self.number}"


@dataclass(frozen=True)
class ReportIssue:
    platform: str
    number: str
    title: str
    state: str
    labels: tuple[str, ...]
    url: str
    author: str
    updated_at: str
    requirement: str
    scope: str
    acceptance: str
    risks: str

    @property
    def ref(self) -> str:
        return f"{self.platform}#{self.number}"


def normalize_issue_number(platform: str, value: Any) -> str:
    text = str(value or "").strip()
    if platform == "github":
        if not re.fullmatch(r"\d+", text):
            raise IssueWorkflowError(f"GitHub issue 编号应为数字：{text or '<空>'}")
        return text
    text = text.upper()
    if not re.fullmatch(ISSUE_ID_PATTERN, text):
        raise IssueWorkflowError(f"Gitee issue 编号格式无效：{text or '<空>'}")
    return text


def env_flag(name: str) -> bool:
    return os.environ.get(name, "").strip().lower() in {"1", "true", "yes", "on"}


def dry_run() -> bool:
    return env_flag("ZENVIS_ISSUE_DRY_RUN")


def parse_local_env_value(raw: str, path: Path, line_number: int) -> str:
    value = raw.strip()
    if not value:
        return ""
    if value[0] not in {"'", '"'}:
        return value
    quote = value[0]
    if len(value) < 2 or value[-1] != quote:
        raise IssueWorkflowError(f"{path}:{line_number} 引号未闭合")
    inner = value[1:-1]
    if quote == "'":
        return inner
    escapes = {"\\": "\\", '"': '"', "n": "\n", "r": "\r", "t": "\t"}
    return re.sub(
        r"\\([\\\"nrt])",
        lambda match: escapes[match.group(1)],
        inner,
    )


def load_local_env(root: Path) -> tuple[str, ...]:
    path = root / LOCAL_ENV_FILE
    if not path.is_file():
        return ()
    try:
        lines = path.read_text(encoding="utf-8").splitlines()
    except OSError as exc:
        raise IssueWorkflowError(f"无法读取本地环境文件 {path}: {exc}") from exc
    values: dict[str, str] = {}
    for line_number, raw in enumerate(lines, 1):
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        if line.startswith("export "):
            line = line[7:].lstrip()
        if "=" not in line:
            raise IssueWorkflowError(
                f"{path}:{line_number} 应为 KEY=VALUE 格式"
            )
        key, raw_value = line.split("=", 1)
        key = key.strip()
        if not re.fullmatch(r"[A-Za-z_][A-Za-z0-9_]*", key):
            raise IssueWorkflowError(
                f"{path}:{line_number} 变量名格式无效"
            )
        if key != "GITHUB_TOKEN" and not key.startswith("ZENVIS_"):
            continue
        values[key] = parse_local_env_value(raw_value, path, line_number)
    for key, value in values.items():
        os.environ.setdefault(key, value)
    return tuple(values)


def discover_root(value: Optional[str] = None) -> Path:
    candidate = value or os.environ.get("ZENVIS_ISSUE_ROOT")
    if candidate:
        return Path(candidate).expanduser().resolve()
    result = subprocess.run(
        ["git", "rev-parse", "--show-toplevel"],
        text=True,
        capture_output=True,
        check=False,
    )
    if result.returncode != 0:
        raise IssueWorkflowError("当前目录不在 Git 仓库内")
    return Path(result.stdout.strip()).resolve()


def resolve_root(value: Optional[str] = None) -> Path:
    root = discover_root(value)
    load_local_env(root)
    return root


def run_git(
    root: Path,
    args: Sequence[str],
    *,
    check: bool = True,
    capture: bool = True,
    text_input: Optional[str] = None,
) -> subprocess.CompletedProcess[str]:
    result = subprocess.run(
        ["git", *args],
        cwd=root,
        text=True,
        capture_output=capture,
        input=text_input,
        check=False,
    )
    if check and result.returncode != 0:
        detail = (result.stderr or result.stdout or "").strip()
        raise IssueWorkflowError(f"git {' '.join(args)} 失败：{detail}")
    return result


def run_git_sensitive(root: Path, args: Sequence[str]) -> subprocess.CompletedProcess[str]:
    try:
        return run_git(root, args)
    except IssueWorkflowError as exc:
        raise IssueWorkflowError(redact_sensitive(str(exc))) from exc


def now_local() -> datetime:
    return datetime.now().astimezone()


def iso_now() -> str:
    return now_local().isoformat(timespec="seconds")


def atomic_write(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    mode = (path.stat().st_mode & 0o777) if path.exists() else 0o644
    with tempfile.NamedTemporaryFile(
        "w", encoding="utf-8", dir=path.parent, delete=False
    ) as handle:
        handle.write(content)
        temporary = Path(handle.name)
    os.chmod(temporary, mode)
    temporary.replace(path)


def read_json(path: Path, default: Any) -> Any:
    if not path.is_file():
        return default
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise IssueWorkflowError(f"无法读取 {path}: {exc}") from exc


def write_json(path: Path, value: Any) -> None:
    atomic_write(path, json.dumps(value, ensure_ascii=False, indent=2) + "\n")


def compact_text(value: str, limit: int) -> str:
    text = re.sub(r"\r\n?", "\n", value or "")
    text = re.sub(r"\n{3,}", "\n\n", text).strip()
    if len(text) <= limit:
        return text
    return text[: max(0, limit - 1)].rstrip() + "…"


SECRET_PATTERNS: tuple[tuple[re.Pattern[str], str], ...] = (
    (
        re.compile(
            r"-----BEGIN [A-Z0-9 ]*PRIVATE KEY-----.*?"
            r"-----END [A-Z0-9 ]*PRIVATE KEY-----",
            re.DOTALL,
        ),
        "<PRIVATE_KEY_REDACTED>",
    ),
    (
        re.compile(
            r"\b(?:gh[pousr]_[A-Za-z0-9_]{20,}|github_pat_[A-Za-z0-9_]{20,})\b"
        ),
        "<TOKEN_REDACTED>",
    ),
    (
        re.compile(r"(?i)(Authorization\s*:\s*Bearer\s+)[^\s,;]+"),
        r"\1<TOKEN_REDACTED>",
    ),
    (
        re.compile(r"(?i)(Bearer\s+)[A-Za-z0-9._~+/=-]{12,}"),
        r"\1<TOKEN_REDACTED>",
    ),
    (
        re.compile(r"(https?://[^/\s:@]+:)[^@\s/]+(@)"),
        r"\1<REDACTED>\2",
    ),
    (
        re.compile(
            r"(?i)\b(access[_-]?token|api[_-]?key|token|password|passwd|secret)"
            r"(\s*[:=]\s*)[\"']?[^\s\"',;]{6,}"
        ),
        r"\1\2<REDACTED>",
    ),
)


def redact_sensitive(value: str) -> str:
    result = value or ""
    for pattern, replacement in SECRET_PATTERNS:
        result = pattern.sub(replacement, result)
    return result


def clean_title(value: str, limit: int = 120) -> str:
    title = re.sub(r"\s+", " ", redact_sensitive(value or "")).strip()
    if not title:
        title = "未命名需求"
    if len(title) > limit:
        title = title[: limit - 1].rstrip() + "…"
    return title


def summarize_body(value: str, limit: int = 600) -> str:
    text = redact_sensitive(value)
    text = re.sub(r"<!--.*?-->", "", text, flags=re.DOTALL)
    lines: list[str] = []
    for raw_line in text.splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or line.startswith("```"):
            continue
        if line.startswith("![") or line.startswith("[!["):
            continue
        lines.append(line)
        if sum(len(item) for item in lines) >= limit:
            break
    summary = "\n".join(lines)
    if not summary:
        summary = "原 issue 未提供可提取的文字描述。"
    return compact_text(summary, limit)


def default_transport(
    method: str,
    url: str,
    headers: dict[str, str],
    data: Optional[bytes],
) -> tuple[int, dict[str, str], bytes]:
    request = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            return response.status, dict(response.headers), response.read()
    except urllib.error.HTTPError as exc:
        return exc.code, dict(exc.headers), exc.read()
    except urllib.error.URLError as exc:
        raise IssueWorkflowError(
            f"访问 {redact_sensitive(url)} 失败：{exc.reason}"
        ) from exc


Transport = Callable[
    [str, str, dict[str, str], Optional[bytes]],
    tuple[int, dict[str, str], bytes],
]


class PlatformClient:
    def __init__(
        self,
        platform: str,
        repo: str,
        token: str = "",
        transport: Optional[Transport] = None,
        base_url: Optional[str] = None,
    ) -> None:
        if platform not in {"github", "gitee"}:
            raise ValueError(f"不支持的平台：{platform}")
        if not re.fullmatch(r"[^/\s]+/[^/\s]+", repo):
            raise IssueWorkflowError(f"仓库格式应为 owner/repo：{repo}")
        self.platform = platform
        self.repo = repo
        self.token = token.strip()
        self.transport = transport or default_transport
        self.base_url = (
            base_url
            or ("https://api.github.com" if platform == "github" else "https://gitee.com/api/v5")
        ).rstrip("/")

    @classmethod
    def from_environment(
        cls,
        platform: str,
        transport: Optional[Transport] = None,
        repo: Optional[str] = None,
        token: Optional[str] = None,
    ) -> "PlatformClient":
        if platform == "github":
            resolved_repo = repo or os.environ.get("ZENVIS_GITHUB_REPO", DEFAULT_GITHUB_REPO)
            resolved_token = (
                token
                if token is not None
                else os.environ.get("ZENVIS_GITHUB_TOKEN")
                or os.environ.get("GITHUB_TOKEN")
                or ""
            )
        else:
            resolved_repo = repo or os.environ.get("ZENVIS_GITEE_REPO", DEFAULT_GITEE_REPO)
            resolved_token = (
                token
                if token is not None
                else os.environ.get("ZENVIS_GITEE_TOKEN") or ""
            )
        return cls(platform, resolved_repo, resolved_token, transport)

    def _headers(self, payload: Optional[bytes]) -> dict[str, str]:
        headers = {
            "Accept": (
                "application/vnd.github+json"
                if self.platform == "github"
                else "application/json"
            ),
            "User-Agent": "zenvis-issue-workflow",
        }
        if payload is not None:
            headers["Content-Type"] = "application/json; charset=utf-8"
        if self.token:
            if self.platform == "github":
                headers["Authorization"] = f"Bearer {self.token}"
                headers["X-GitHub-Api-Version"] = "2022-11-28"
            else:
                headers["Authorization"] = f"token {self.token}"
        return headers

    def request(
        self,
        method: str,
        path: str,
        *,
        query: Optional[dict[str, Any]] = None,
        payload: Optional[dict[str, Any]] = None,
        allow_404: bool = False,
    ) -> Any:
        if not path.startswith("/"):
            path = "/" + path
        url = f"{self.base_url}{path}"
        params = dict(query or {})
        if self.platform == "gitee" and self.token:
            params["access_token"] = self.token
        if params:
            encoded = urllib.parse.urlencode(
                [(key, value) for key, value in params.items() if value is not None]
            )
            if encoded:
                url = f"{url}?{encoded}"
        body = (
            json.dumps(payload, ensure_ascii=False).encode("utf-8")
            if payload is not None
            else None
        )
        status, _, raw = self.transport(method, url, self._headers(body), body)
        text = raw.decode("utf-8", errors="replace")
        if status == 404 and allow_404:
            return None
        if status < 200 or status >= 300:
            message = text
            try:
                parsed = json.loads(text)
                if isinstance(parsed, dict):
                    message = str(
                        parsed.get("message")
                        or parsed.get("error")
                        or parsed.get("error_description")
                        or text
                    )
            except json.JSONDecodeError:
                pass
            raise IssueApiError(status, message[:1000], redact_sensitive(url))
        if not text:
            return {}
        try:
            return json.loads(text)
        except json.JSONDecodeError as exc:
            raise IssueWorkflowError(
                f"平台返回了非 JSON 内容：{redact_sensitive(url)}"
            ) from exc

    def check_repo(self) -> dict[str, Any]:
        return self.request("GET", f"/repos/{self.repo}")

    def list_issues(self, state: str = "open", per_page: int = 100) -> list[Issue]:
        collected: list[Issue] = []
        for page in range(1, 51):
            items = self.request(
                "GET",
                f"/repos/{self.repo}/issues",
                query={
                    "state": state,
                    "per_page": per_page,
                    "page": page,
                    "sort": "updated",
                    "direction": "desc",
                },
            )
            if not isinstance(items, list):
                raise IssueWorkflowError(f"{self.platform} issue 列表格式异常")
            for item in items:
                if not isinstance(item, dict) or self._is_pull_request(item):
                    continue
                collected.append(self._to_issue(item))
            if len(items) < per_page:
                break
        return collected

    def get_issue(self, number: str) -> Issue:
        item = self.request("GET", f"/repos/{self.repo}/issues/{number}")
        if not isinstance(item, dict):
            raise IssueWorkflowError(f"{self.platform} issue 返回格式异常")
        if self._is_pull_request(item):
            raise IssueWorkflowError(f"{self.platform}#{number} 是 Pull Request，不是 issue")
        return self._to_issue(item)

    @staticmethod
    def _is_pull_request(item: dict[str, Any]) -> bool:
        if item.get("pull_request"):
            return True
        url = str(item.get("html_url") or "")
        return "/pulls/" in url or "/pull/" in url

    @staticmethod
    def _labels(item: dict[str, Any]) -> tuple[str, ...]:
        labels = item.get("labels") or []
        values: list[str] = []
        if isinstance(labels, list):
            for label in labels:
                if isinstance(label, str):
                    values.append(label)
                elif isinstance(label, dict):
                    name = label.get("name")
                    if name:
                        values.append(str(name))
                elif isinstance(label, (int, float)):
                    values.append(str(label))
        return tuple(dict.fromkeys(values))

    def _to_issue(self, item: dict[str, Any]) -> Issue:
        user = item.get("user") or {}
        number_value = item.get("number") or item.get("id")
        number = normalize_issue_number(self.platform, number_value)
        return Issue(
            platform=self.platform,
            number=number,
            title=str(item.get("title") or ""),
            body=str(item.get("body") or ""),
            state=str(item.get("state") or "open"),
            labels=self._labels(item),
            url=str(item.get("html_url") or item.get("url") or ""),
            author=str(user.get("login") or ""),
            created_at=str(item.get("created_at") or ""),
            updated_at=str(item.get("updated_at") or ""),
        )

    def create_issue(
        self, title: str, body: str, labels: Optional[Iterable[str]] = None
    ) -> str:
        payload: dict[str, Any] = {"title": clean_title(title), "body": body}
        label_values = [item for item in (labels or []) if item]
        if label_values:
            payload["labels"] = (
                label_values if self.platform == "github" else ",".join(label_values)
            )
        if self.platform == "gitee":
            owner, repo_name = self.repo.split("/", 1)
            payload["repo"] = repo_name
            path = f"/repos/{owner}/issues"
        else:
            path = f"/repos/{self.repo}/issues"
        result = self.request("POST", path, payload=payload)
        if not isinstance(result, dict) or not result.get("number"):
            raise IssueWorkflowError(f"{self.platform} 创建 issue 后未返回编号")
        return normalize_issue_number(self.platform, result["number"])

    def list_comments(self, number: str) -> list[dict[str, Any]]:
        result = self.request(
            "GET",
            f"/repos/{self.repo}/issues/{number}/comments",
            query={"per_page": 100},
        )
        if not isinstance(result, list):
            return []
        return [item for item in result if isinstance(item, dict)]

    def comment_issue(self, number: str, body: str) -> None:
        self.request(
            "POST",
            f"/repos/{self.repo}/issues/{number}/comments",
            payload={"body": body},
        )

    def close_issue(self, number: str) -> dict[str, Any]:
        payload: dict[str, Any] = {"state": "closed"}
        if self.platform == "gitee":
            owner, repo_name = self.repo.split("/", 1)
            payload["repo"] = repo_name
            path = f"/repos/{owner}/issues/{number}"
        else:
            path = f"/repos/{self.repo}/issues/{number}"
        result = self.request("PATCH", path, payload=payload)
        return result if isinstance(result, dict) else {}

    def commit_exists(self, sha: str) -> bool:
        if not re.fullmatch(r"[0-9a-fA-F]{7,64}", sha):
            raise IssueWorkflowError(f"提交 SHA 格式无效：{sha}")
        result = self.request(
            "GET", f"/repos/{self.repo}/commits/{sha}", allow_404=True
        )
        return result is not None

    def get_release(self, tag: str) -> Optional[dict[str, Any]]:
        result = self.request(
            "GET",
            f"/repos/{self.repo}/releases/tags/{urllib.parse.quote(tag, safe='')}",
            allow_404=True,
        )
        if result is None:
            return None
        if not isinstance(result, dict):
            raise IssueWorkflowError(f"{self.platform} Release 返回格式异常")
        return result

    def create_release(
        self,
        tag: str,
        name: str,
        body: str,
        *,
        target_commitish: str = "",
        prerelease: bool = False,
    ) -> dict[str, Any]:
        payload: dict[str, Any] = {
            "tag_name": tag,
            "name": name,
            "body": body,
            "prerelease": prerelease,
        }
        if target_commitish:
            payload["target_commitish"] = target_commitish
        result = self.request(
            "POST", f"/repos/{self.repo}/releases", payload=payload
        )
        if not isinstance(result, dict):
            raise IssueWorkflowError(f"{self.platform} 创建 Release 后返回格式异常")
        return result

    def tag_exists(self, tag: str) -> bool:
        quoted = urllib.parse.quote(tag, safe="")
        if self.platform == "github":
            path = f"/repos/{self.repo}/git/ref/tags/{quoted}"
        else:
            path = f"/repos/{self.repo}/tags/{quoted}"
        return self.request("GET", path, allow_404=True) is not None

    def commit_url(self, sha: str) -> str:
        if self.platform == "github":
            return f"https://github.com/{self.repo}/commit/{sha}"
        return f"https://gitee.com/{self.repo}/commit/{sha}"

    def issue_url(self, number: str) -> str:
        if self.platform == "github":
            return f"https://github.com/{self.repo}/issues/{number}"
        return f"https://gitee.com/{self.repo}/issues/{number}"


def issue_type(issue: Issue) -> str:
    labels = " ".join(issue.labels).lower()
    text = f"{issue.title}\n{issue.body}".lower()
    if any(word in labels for word in ("bug", "defect", "fix")) or re.search(
        r"(报错|异常|失败|崩溃|错误|无法|不能|不生效|修复|bug|error|exception)", text
    ):
        return "缺陷"
    if any(word in labels for word in ("feature", "enhancement")) or re.search(
        r"(希望|建议|新增|支持|增加|需求|feature|enhancement)", text
    ):
        return "功能"
    if any(word in labels for word in ("documentation", "docs")) or re.search(
        r"(文档|说明|readme|documentation)", text
    ):
        return "文档"
    if any(word in labels for word in ("question", "help")) or "?" in issue.title:
        return "咨询"
    return "其他"


def issue_priority(issue: Issue) -> str:
    labels = " ".join(issue.labels).lower()
    text = f"{issue.title}\n{issue.body}".lower()
    if any(word in labels for word in ("urgent", "critical", "p0", "p1")) or re.search(
        r"(数据丢失|安全事故|无法登录|生产故障|崩溃|阻塞|阻断|安全漏洞)", text
    ):
        return "高"
    if issue_type(issue) in {"咨询", "文档"}:
        return "低"
    return "中"


def issue_domain(issue: Issue) -> str:
    haystack = " ".join(issue.labels + (issue.title, issue.body)).lower()
    domains = (
        ("前端", ("frontend", "vue", "页面", "看板", "大屏", "组件", "样式")),
        ("后端", ("backend", "java", "接口", "api", "controller", "service", "数据库")),
        ("AI/DIH", ("dih", "智能体", "agent", "rag", "mcp", "模型", "skill")),
        ("插件", ("plugin", "插件")),
        ("部署", ("deploy", "部署", "docker", "helm", "kubernetes", "compose")),
        ("文档", ("doc", "文档", "readme")),
    )
    for name, words in domains:
        if any(word in haystack for word in words):
            return name
    return "未分类"


def _title_signature(title: str) -> str:
    lowered = re.sub(r"\b(?:github|gitee)#\d+\b", "", title.lower())
    return re.sub(r"[^0-9a-z\u4e00-\u9fff]+", "", lowered)


def _bigrams(value: str) -> set[str]:
    if len(value) < 2:
        return {value} if value else set()
    return {value[index : index + 2] for index in range(len(value) - 1)}


def duplicate_candidates(issue: Issue, issues: Sequence[Issue]) -> list[str]:
    signature = _title_signature(issue.title)
    grams = _bigrams(signature)
    candidates: list[tuple[float, str]] = []
    for other in issues:
        if other.ref == issue.ref:
            continue
        other_signature = _title_signature(other.title)
        if not signature or not other_signature:
            continue
        other_grams = _bigrams(other_signature)
        union = grams | other_grams
        similarity = len(grams & other_grams) / len(union) if union else 0.0
        sequence = SequenceMatcher(None, signature, other_signature).ratio()
        score = max(similarity, sequence)
        if score >= 0.58:
            candidates.append((score, other.ref))
    candidates.sort(reverse=True)
    return [ref for _, ref in candidates[:3]]


def missing_information(issue: Issue) -> list[str]:
    text = f"{issue.title}\n{issue.body}"
    missing: list[str] = []
    if len(issue.body.strip()) < 40:
        missing.append("描述较短，可能缺少背景")
    if issue_type(issue) == "缺陷":
        if not re.search(r"(步骤|复现|操作|场景|版本|环境)", text):
            missing.append("缺少复现步骤或运行环境")
        if not re.search(r"(预期|应该|期望)", text):
            missing.append("缺少预期结果")
        if not re.search(r"(实际|结果|现象|报错|异常)", text):
            missing.append("缺少实际结果或错误信息")
    if not re.search(r"(验收|完成标准|成功标准)", text):
        missing.append("缺少明确验收标准")
    return missing


def suggested_scope(issue: Issue) -> str:
    scopes = {
        "前端": "前端页面或组件修复，补充类型检查、Node 单元测试，必要时增加 Playwright 回归。",
        "后端": "后端接口或服务修复，补充 Maven 单元/集成测试，并验证接口契约。",
        "AI/DIH": "DIH 工作流、Skill 或 MCP 工具修复，补充对应工作流和权限边界测试。",
        "插件": "目标插件实现与路由修复，校验 Meta、配置、UI 和文档契约一致。",
        "部署": "部署脚本或配置修复，执行 shell 语法检查及 Compose/Kubernetes 配置校验。",
        "文档": "同步更新相关文档、目录和链接，检查文档站渲染与引用。",
        "未分类": "先定位影响模块并确认修改边界，再补充针对性的测试。",
    }
    return scopes[issue_domain(issue)]


def acceptance_criteria(issue: Issue) -> str:
    if issue_type(issue) == "缺陷":
        return "按 issue 描述复现问题后不再出现；相关测试通过；未引入相邻功能回归。"
    if issue_type(issue) == "功能":
        return "需求描述中的主路径可用；必要的异常和空状态已覆盖；相关测试通过。"
    if issue_type(issue) == "文档":
        return "文档内容与当前实现一致，目录和链接可正常访问。"
    return "原 issue 中的疑问得到明确答复，或需求边界被记录并经过确认。"


def render_issue_block(issue: Issue, all_issues: Sequence[Issue]) -> str:
    duplicates = duplicate_candidates(issue, all_issues)
    missing = missing_information(issue)
    labels = ", ".join(issue.labels) if issue.labels else "无"
    risk_lines = [
        f"- 类型：{issue_type(issue)}",
        f"- 领域：{issue_domain(issue)}",
        f"- 优先级：{issue_priority(issue)}",
    ]
    if duplicates:
        risk_lines.append("- 疑似重复：" + "、".join(duplicates))
    if missing:
        risk_lines.append("- 待补充信息：" + "；".join(missing))
    if len(risk_lines) == 3:
        risk_lines.append("- 未发现明显的需求歧义；实施前仍需确认影响范围。")
    return "\n".join(
        [
            f"## {issue.ref} — {clean_title(issue.title)}",
            f"- 状态：{issue.state}",
            f"- 标签：{labels}",
            f"- 原始链接：{issue.url}",
            f"- 作者：{issue.author or '未知'}",
            f"- 更新时间：{issue.updated_at or '未知'}",
            "### 需求摘要",
            summarize_body(issue.body),
            "### 修复范围",
            suggested_scope(issue),
            "### 验收标准",
            acceptance_criteria(issue),
            "### 风险与待确认",
            "\n".join(risk_lines),
            "",
        ]
    )


def safe_directory_name(generated_at: datetime) -> str:
    return generated_at.strftime("%Y-%m-%d-%H%M%S")


def unique_report_dir(root: Path, generated_at: datetime) -> Path:
    base = root / REPORT_ROOT
    candidate = base / safe_directory_name(generated_at)
    suffix = 2
    while candidate.exists():
        candidate = base / f"{safe_directory_name(generated_at)}-{suffix}"
        suffix += 1
    return candidate


def count_classification(
    issues: Sequence[Issue], field: Callable[[Issue], str]
) -> dict[str, int]:
    counts: dict[str, int] = {}
    for issue in issues:
        value = field(issue)
        counts[value] = counts.get(value, 0) + 1
    return dict(sorted(counts.items()))


def render_report_overview(
    generated_at: datetime, issues: Sequence[Issue]
) -> str:
    by_platform = {
        platform: [item for item in issues if item.platform == platform]
        for platform in ("github", "gitee")
    }
    rows: list[str] = []
    for platform, items in by_platform.items():
        types = count_classification(items, issue_type)
        type_text = "，".join(f"{key} {value}" for key, value in types.items()) or "无"
        rows.append(f"| {platform} | {len(items)} | {type_text} |")
    execution_lines = [
        f"- [ ] {item.ref} — {clean_title(item.title)}" for item in issues
    ]
    if not execution_lines:
        execution_lines = ["- 当前没有待处理 issue。"]
    return "\n".join(
        [
            f"# Issue 归纳报告 — {generated_at.strftime('%Y-%m-%d %H:%M:%S %z')}",
            "",
            "本报告由 `$zenvis-issue-workflow` 的 `report` 模式生成，仅包含脱敏后的需求摘要。",
            "报告生成阶段不会修改代码、标签或 issue 状态。",
            "",
            "## 审核方式",
            "",
            "1. 删除整个 issue 标题块表示不执行该 issue。",
            "2. 修改“修复范围”和“验收标准”表示按修改后的内容执行。",
            "3. 编辑完成后，在对话中明确说“按这份报告修复”，才会进入执行阶段。",
            "",
            "## 汇总",
            "",
            "| 平台 | 数量 | 分类 |",
            "| --- | ---: | --- |",
            *rows,
            "",
            "## 分平台报告",
            "",
            "- [GitHub](github.md)",
            "- [Gitee](gitee.md)",
            "",
            "## 执行清单",
            "",
            "下列清单仅用于概览；实际删减必须在 `github.md` 或 `gitee.md` 中完成，脚本只解析这两个平台文件。",
            "",
            *execution_lines,
            "",
        ]
    )


def render_platform_report(
    platform: str,
    issues: Sequence[Issue],
    generated_at: datetime,
    state: str = "open",
) -> str:
    title = "GitHub" if platform == "github" else "Gitee"
    platform_issues = [item for item in issues if item.platform == platform]
    scope = {
        "open": "全部 open issue",
        "closed": "全部 closed issue",
        "all": "全部 issue（含 open 和 closed）",
    }.get(state, f"全部 {state} issue")
    lines = [
        f"# {title} Issue 报告",
        "",
        f"- 生成时间：{generated_at.isoformat(timespec='seconds')}",
        f"- 范围：`{platform}` {scope}，已过滤 Pull Request。",
        f"- 数量：{len(platform_issues)}",
        "",
    ]
    if not platform_issues:
        lines.extend(["当前没有待处理的 open issue。", ""])
        return "\n".join(lines)
    for issue in platform_issues:
        lines.append(render_issue_block(issue, platform_issues))
    return "\n".join(lines)


def update_report_index(
    root: Path,
    directory: Path,
    generated_at: datetime,
    issues: Sequence[Issue],
) -> None:
    index_path = root / REPORT_ROOT / "README.md"
    index_path.parent.mkdir(parents=True, exist_ok=True)
    if not index_path.is_file():
        atomic_write(
            index_path,
            "\n".join(
                [
                    "# Issue 归纳报告",
                    "",
                    "本目录保存按需生成的 GitHub / Gitee issue 归纳报告。",
                    "报告只包含脱敏摘要；审核和修复授权由维护者在对话中完成。",
                    "",
                    "<!-- reports:start -->",
                    "<!-- reports:end -->",
                    "",
                ]
            ),
        )
    content = index_path.read_text(encoding="utf-8")
    relative = directory.relative_to(root / REPORT_ROOT).as_posix()
    github_count = sum(1 for item in issues if item.platform == "github")
    gitee_count = sum(1 for item in issues if item.platform == "gitee")
    entry = (
        f"- [{generated_at.strftime('%Y-%m-%d %H:%M:%S')}]({relative}/README.md) "
        f"— GitHub {github_count} / Gitee {gitee_count}"
    )
    marker = "<!-- reports:start -->"
    if marker in content:
        content = content.replace(marker, f"{marker}\n{entry}", 1)
    else:
        content = content.rstrip() + "\n\n## 报告列表\n\n" + entry + "\n"
    atomic_write(index_path, content)


def create_report(
    issues: Sequence[Issue],
    *,
    root: Path,
    generated_at: Optional[datetime] = None,
    state: str = "open",
) -> Path:
    generated_at = generated_at or now_local()
    directory = unique_report_dir(root, generated_at)
    directory.mkdir(parents=True, exist_ok=False)
    atomic_write(directory / "README.md", render_report_overview(generated_at, issues))
    for platform in ("github", "gitee"):
        atomic_write(
            directory / f"{platform}.md",
            render_platform_report(platform, issues, generated_at, state),
        )
    update_report_index(root, directory, generated_at, issues)
    state_path = root / STATE_ROOT / "reports" / f"{directory.name}.json"
    write_json(
        state_path,
        {
            "generated_at": generated_at.isoformat(timespec="seconds"),
            "report_dir": str(directory),
            "issues": [asdict(item) for item in issues],
        },
    )
    return directory


def _field(block: str, name: str) -> str:
    match = re.search(rf"(?m)^-\s*{re.escape(name)}\s*[:：]\s*(.*?)\s*$", block)
    return match.group(1).strip() if match else ""


def _section(block: str, name: str) -> str:
    match = re.search(
        rf"(?ms)^###\s*{re.escape(name)}\s*\n(.*?)(?=^###\s|\Z)", block
    )
    return match.group(1).strip() if match else ""


def parse_report(report_dir: Path) -> list[ReportIssue]:
    issues: list[ReportIssue] = []
    for platform in ("github", "gitee"):
        path = report_dir / f"{platform}.md"
        if not path.is_file():
            raise IssueWorkflowError(f"报告缺少 {path}")
        text = path.read_text(encoding="utf-8")
        matches = list(REPORT_BLOCK_RE.finditer(text))
        for index, match in enumerate(matches):
            block_end = matches[index + 1].start() if index + 1 < len(matches) else len(text)
            block = text[match.start() : block_end]
            labels_text = _field(block, "标签")
            labels = tuple(
                item.strip()
                for item in re.split(r"[,，]", labels_text)
                if item.strip() and item.strip() != "无"
            )
            issues.append(
                ReportIssue(
                    platform=match.group(1).lower(),
                    number=normalize_issue_number(
                        match.group(1).lower(), match.group(2)
                    ),
                    title=match.group(3).strip(),
                    state=_field(block, "状态"),
                    labels=labels,
                    url=_field(block, "原始链接"),
                    author=_field(block, "作者"),
                    updated_at=_field(block, "更新时间"),
                    requirement=_section(block, "需求摘要"),
                    scope=_section(block, "修复范围"),
                    acceptance=_section(block, "验收标准"),
                    risks=_section(block, "风险与待确认"),
                )
            )
    return issues


def resolve_report(root: Path, value: Optional[str]) -> Path:
    if value:
        path = Path(value).expanduser()
        if not path.is_absolute():
            path = root / path
        if path.is_file():
            path = path.parent
        if not path.is_dir():
            raise IssueWorkflowError(f"报告目录不存在：{path}")
        return path.resolve()
    base = root / REPORT_ROOT
    candidates = (
        [
            item
            for item in base.iterdir()
            if item.is_dir()
            and (item / "github.md").is_file()
            and (item / "gitee.md").is_file()
        ]
        if base.is_dir()
        else []
    )
    if not candidates:
        raise IssueWorkflowError("没有找到 issue 报告，请先运行 report")
    return sorted(candidates, key=lambda item: item.name)[-1].resolve()


def parse_issue_selection(value: str) -> tuple[str, str]:
    match = re.fullmatch(
        rf"\s*(github|gitee)#({ISSUE_ID_PATTERN})\s*", value, re.IGNORECASE
    )
    if not match:
        raise IssueWorkflowError(
            f"issue 引用格式应为 github#12 或 gitee#IKSGM9：{value}"
        )
    platform = match.group(1).lower()
    return platform, normalize_issue_number(platform, match.group(2))


def parse_trailers(message: str) -> list[tuple[str, str]]:
    refs: list[tuple[str, str]] = []
    for match in TRAILER_RE.finditer(message):
        platform = match.group(1).lower()
        ref = (platform, normalize_issue_number(platform, match.group(2)))
        if ref not in refs:
            refs.append(ref)
    github_closes = GITHUB_CLOSES_RE.findall(message)
    gitee_closes = GITEE_CLOSES_RE.findall(message)
    for number in gitee_closes:
        ref = ("gitee", normalize_issue_number("gitee", number))
        if ref not in refs:
            refs.append(ref)
    for number in github_closes:
        ref = ("github", normalize_issue_number("github", number))
        if ("gitee", ref[1]) in refs:
            continue
        if ref not in refs:
            refs.append(ref)
    return refs


def parse_referenced_issues(message: str) -> list[tuple[str, str]]:
    refs = parse_trailers(message)
    for match in ISSUE_REF_RE.finditer(message):
        platform = match.group(1).lower()
        ref = (platform, normalize_issue_number(platform, match.group(2)))
        if ref not in refs:
            refs.append(ref)
    for number in GITEE_CLOSES_RE.findall(message):
        ref = ("gitee", normalize_issue_number("gitee", number))
        if ref not in refs:
            refs.append(ref)
    return refs


def append_issue_trailers(message: str, platform: str, number: str) -> str:
    trailer = (
        f"Zenvis-Issue: github#{number}"
        if platform == "github"
        else f"Zenvis-Issue: gitee#{number}"
    )
    close_trailer = (
        f"Closes #{number}" if platform == "github" else f"Gitee-Closes: #{number}"
    )
    close_pattern = GITHUB_CLOSES_RE if platform == "github" else GITEE_CLOSES_RE
    additions: list[str] = []
    if not TRAILER_RE.search(message):
        additions.append(trailer)
    if not close_pattern.search(message):
        additions.append(close_trailer)
    if not additions:
        return message
    return message.rstrip() + "\n\n" + "\n".join(additions) + "\n"


def prepare_commit_message(
    issue_values: Sequence[str], message: str
) -> tuple[tuple[str, str], str]:
    if len(issue_values) != 1:
        raise IssueWorkflowError("commit-command 一次只能关联一个 issue")
    if not message.strip():
        raise IssueWorkflowError("提交信息不能为空")
    selected = parse_issue_selection(issue_values[0])
    existing = parse_referenced_issues(message)
    mismatched = [ref for ref in existing if ref != selected]
    if mismatched:
        rendered = "、".join(f"{platform}#{number}" for platform, number in mismatched)
        raise IssueWorkflowError(
            f"提交信息包含其他 issue 引用：{rendered}；一个提交只能关联 "
            f"{selected[0]}#{selected[1]}"
        )
    updated = append_issue_trailers(message, selected[0], selected[1])
    parsed = parse_trailers(updated)
    if parsed != [selected]:
        raise IssueWorkflowError("生成的提交信息未能形成唯一 issue 引用")
    return selected, updated


def _commit_heredoc_delimiter(message: str) -> str:
    used_lines = set(message.splitlines())
    candidate = "ZENVIS_COMMIT_MSG"
    suffix = 2
    while candidate in used_lines:
        candidate = f"ZENVIS_COMMIT_MSG_{suffix}"
        suffix += 1
    return candidate


def build_commit_commands(
    files: Sequence[str], message: str
) -> str:
    file_values = [item for item in files if item]
    if not file_values:
        raise IssueWorkflowError("至少需要一个 --file")
    if any("\x00" in item for item in file_values):
        raise IssueWorkflowError("文件路径不能包含 NUL 字符")
    if not message.strip():
        raise IssueWorkflowError("提交信息不能为空")
    delimiter = _commit_heredoc_delimiter(message)
    lines = [
        "git add -- " + " ".join(shlex.quote(item) for item in file_values),
        f"git commit -F - <<'{delimiter}'",
        *message.rstrip("\n").splitlines(),
        delimiter,
    ]
    return "\n".join(lines) + "\n"


def resolve_commit_message(issue_values: Sequence[str], message: str) -> str:
    if not message.strip():
        raise IssueWorkflowError("提交信息不能为空")
    if not issue_values:
        return message.rstrip() + "\n"
    _, updated = prepare_commit_message(issue_values, message)
    return updated


def execute_commit(root: Path, files: Sequence[str], message: str) -> str:
    if not files:
        raise IssueWorkflowError("至少需要一个 --file")
    if any("\x00" in item for item in files):
        raise IssueWorkflowError("文件路径不能包含 NUL 字符")
    if not message.strip():
        raise IssueWorkflowError("提交信息不能为空")
    run_git(root, ["add", "--", *files], capture=False)
    run_git(
        root,
        ["commit", "-F", "-"],
        capture=False,
        text_input=message.rstrip() + "\n",
    )
    return run_git(root, ["rev-parse", "--short", "HEAD"]).stdout.strip()


def repository_remote_url(root: Path, remote: str) -> Optional[str]:
    result = run_git(root, ["remote", "get-url", remote], check=False)
    if result.returncode != 0:
        return None
    return result.stdout.strip() or None


def current_branch(root: Path) -> str:
    result = run_git(root, ["symbolic-ref", "--short", "HEAD"], check=False)
    if result.returncode != 0:
        raise IssueWorkflowError("当前处于 detached HEAD，无法执行推送")
    return result.stdout.strip()


def parse_release_version(value: str) -> ReleaseVersion:
    text = (value or "").strip()
    match = RELEASE_VERSION_RE.fullmatch(text)
    if not match:
        raise IssueWorkflowError(
            f"版本号格式应为 X.Y.Z 或 X.Y.Z.alpha/beta/rc：{text or '<空>'}"
        )
    return ReleaseVersion(
        major=int(match.group(1)),
        minor=int(match.group(2)),
        patch=int(match.group(3)),
        qualifier=match.group(4) or "",
        qualifier_number=int(match.group(5) or 0),
    )


def next_patch_version(version: ReleaseVersion) -> ReleaseVersion:
    return ReleaseVersion(
        major=version.major,
        minor=version.minor,
        patch=version.patch + 1,
        qualifier=version.qualifier,
        qualifier_number=version.qualifier_number,
    )


def read_declared_version(root: Path) -> ReleaseVersion:
    path = root / "deploy" / ".env.example"
    if not path.is_file():
        raise IssueWorkflowError(f"缺少版本文件：{path}")
    text = path.read_text(encoding="utf-8")
    match = re.search(r"(?m)^ZENVIS_VERSION=(.*?)\s*$", text)
    if not match:
        raise IssueWorkflowError("deploy/.env.example 缺少 ZENVIS_VERSION")
    return parse_release_version(match.group(1))


def list_release_tags(root: Path) -> list[ReleaseVersion]:
    output = run_git(root, ["tag", "--list"]).stdout
    versions: list[ReleaseVersion] = []
    for line in output.splitlines():
        value = line.strip()
        if not RELEASE_VERSION_RE.fullmatch(value):
            continue
        versions.append(parse_release_version(value))
    return sorted(versions, key=lambda item: item.key)


def local_tag_exists(root: Path, tag: str) -> bool:
    return (
        run_git(
            root,
            ["rev-parse", "-q", "--verify", f"refs/tags/{tag}"],
            check=False,
        ).returncode
        == 0
    )


def remote_tag_exists(
    root: Path, tag: str, *, fetch: bool = True
) -> bool:
    if not repository_remote_url(root, "origin"):
        raise IssueWorkflowError("缺少 origin remote，无法检查或发布版本")
    if fetch:
        run_git(root, ["fetch", "origin", "--tags", "--prune"], capture=False)
    result = run_git(
        root,
        ["ls-remote", "--tags", "origin", f"refs/tags/{tag}"],
        check=False,
    )
    if result.returncode != 0:
        detail = (result.stderr or result.stdout or "").strip()
        raise IssueWorkflowError(f"检查远端 tag 失败：{redact_sensitive(detail)}")
    return bool(result.stdout.strip())


def release_branch(root: Path) -> str:
    branch = current_branch(root)
    if branch != "main" and not branch.startswith("feature/"):
        raise IssueWorkflowError(
            f"发布分支应为 main 或 feature/*，当前为 {branch}"
        )
    return branch


def resolve_release_version(
    root: Path, explicit: Optional[str]
) -> tuple[ReleaseVersion, ReleaseVersion, Optional[ReleaseVersion]]:
    declared = read_declared_version(root)
    tags = list_release_tags(root)
    latest = tags[-1] if tags else None
    if explicit:
        target = parse_release_version(explicit)
        if target.key < declared.key:
            raise IssueWorkflowError(
                f"目标版本 {target.text} 低于当前声明版本 {declared.text}"
            )
        if latest and target.key <= latest.key:
            raise IssueWorkflowError(
                f"目标版本 {target.text} 未高于最新 tag {latest.text}"
            )
    elif latest is None:
        target = declared
    elif declared.key > latest.key:
        target = declared
    elif declared.key == latest.key:
        target = next_patch_version(latest)
    else:
        raise IssueWorkflowError(
            f"当前声明版本 {declared.text} 低于最新 tag {latest.text}"
        )
    if local_tag_exists(root, target.text):
        raise IssueWorkflowError(f"本地已存在 tag {target.text}")
    return target, declared, latest


def apply_version_targets(
    root: Path, version: str, branch: str
) -> list[str]:
    updated_files: list[str] = []
    pending: list[tuple[Path, str, str]] = []
    for target in RELEASE_TARGETS:
        path = root / target.path
        if not path.is_file():
            raise IssueWorkflowError(f"版本文件不存在：{target.path}")
        original = path.read_text(encoding="utf-8")
        updated = original
        for pattern, template in target.patterns:
            replacement = template.format(version=version, branch=branch)
            updated, count = re.subn(pattern, replacement, updated)
            if count == 0:
                raise IssueWorkflowError(
                    f"版本引用模式未命中：{target.path} / {pattern}"
                )
        if (
            target.path == "doc/02-安装部署与升级/升级与回滚.md"
            and branch == "main"
        ):
            updated = updated.replace(
                "git switch -c main refs/remotes/origin/main",
                "git switch main",
            )
        pending.append((path, original, updated))
        updated_files.append(target.path)
    for path, original, updated in pending:
        if updated != original:
            atomic_write(path, updated)
    return updated_files


def release_commits(
    root: Path, previous_tag: Optional[ReleaseVersion]
) -> list[tuple[str, str]]:
    args = ["log", "--no-merges", "--format=%H%x1f%s%x1e"]
    if previous_tag:
        args.append(f"{previous_tag.text}..HEAD")
    else:
        args.extend(["--max-count=200", "HEAD"])
    output = run_git(root, args).stdout
    commits: list[tuple[str, str]] = []
    seen: set[str] = set()
    for record in output.split("\x1e"):
        fields = record.strip("\n").split("\x1f", 1)
        if len(fields) != 2:
            continue
        sha, subject = fields[0].strip(), redact_sensitive(fields[1].strip())
        if not sha or not subject or subject in seen:
            continue
        if re.match(
            r"^(?:chore\(release\)|发布\s|更新版本|更新.*版本号|Merge\b)",
            subject,
            re.IGNORECASE,
        ):
            continue
        seen.add(subject)
        commits.append((sha, clean_title(subject, 200)))
    return commits


def classify_release_commit(subject: str) -> str:
    lowered = subject.lower()
    if re.match(r"^(?:feat|feature)(?:\(|:|!|\s)", lowered) or re.search(
        r"(新增|支持|增加|添加|实现|接入|扩展)", subject
    ):
        return "新增能力"
    if re.match(r"^fix(?:\(|:|!|\s)", lowered) or re.search(
        r"(修复|解决|修正|纠正|避免)", subject
    ):
        return "问题修复"
    if re.match(r"^(?:perf|refactor)(?:\(|:|!|\s)", lowered) or re.search(
        r"(优化|改进|调整|重构|增强|完善|提升)", subject
    ):
        return "优化与体验"
    if re.match(r"^(?:docs|build|ci|test|chore)(?:\(|:|!|\s)", lowered) or re.search(
        r"(文档|构建|测试|依赖|配置|脚本|工作流)", subject
    ):
        return "文档与工程"
    return "其他"


def render_release_notes(
    root: Path,
    version: ReleaseVersion,
    previous_tag: Optional[ReleaseVersion],
    generated_at: Optional[datetime] = None,
) -> str:
    generated_at = generated_at or now_local()
    commits = release_commits(root, previous_tag)
    sections = {
        "新增能力": [],
        "问题修复": [],
        "优化与体验": [],
        "文档与工程": [],
        "其他": [],
    }
    for sha, subject in commits:
        sections[classify_release_commit(subject)].append(f"- {subject}（`{sha[:8]}`）")
    lines = [
        f"# ZenVis {version.text} 版本发布说明",
        "",
        f"- 发布日期：{generated_at.strftime('%Y-%m-%d')}",
        f"- Git 标签：`{version.text}`",
        f"- 对比范围："
        + (
            f"`{previous_tag.text}..HEAD`"
            if previous_tag
            else "`初始提交..HEAD`"
        ),
        "- 发布平台：Gitee、GitHub",
        "",
        "## 版本重点",
        "",
        RELEASE_REVIEW_MARKER,
    ]
    for name, items in sections.items():
        if not items:
            continue
        lines.extend(["", f"## {name}", *items])
    lines.append("")
    return "\n".join(lines)


def release_draft_path(root: Path, tag: str) -> Path:
    return root / RELEASE_STATE_ROOT / f"{tag}.md"


def release_doc_path(root: Path, tag: str) -> Path:
    return root / RELEASE_DOC_ROOT / f"{tag}.md"


def update_release_index(
    root: Path, tag: str, generated_at: datetime
) -> None:
    index_path = root / RELEASE_DOC_ROOT / "README.md"
    if not index_path.is_file():
        atomic_write(
            index_path,
            "\n".join(
                [
                    "# 版本发布说明",
                    "",
                    "本目录保存 ZenVis 各版本的发布说明。",
                    "发布说明由 `zenvis-issue-workflow` 生成初稿，经人工审核后发布。",
                    "",
                    RELEASE_INDEX_MARKER,
                    "<!-- releases:end -->",
                    "",
                ]
            ),
        )
    content = index_path.read_text(encoding="utf-8")
    entry = f"- [{tag}]({tag}.md) — {generated_at.strftime('%Y-%m-%d')}"
    if entry not in content:
        if RELEASE_INDEX_MARKER in content:
            content = content.replace(
                RELEASE_INDEX_MARKER, f"{RELEASE_INDEX_MARKER}\n{entry}", 1
            )
        else:
            content = content.rstrip() + "\n\n## 版本列表\n\n" + entry + "\n"
        atomic_write(index_path, content)


def update_release_sidebar(root: Path) -> None:
    sidebar = root / "doc" / "_sidebar.md"
    if not sidebar.is_file():
        return
    content = sidebar.read_text(encoding="utf-8")
    entry = "- [版本发布](/10-版本发布/README.md)"
    if entry in content:
        return
    anchor = "- [Issue 报告](/09-issue-reports/README.md)"
    if anchor in content:
        content = content.replace(anchor, f"{anchor}\n{entry}", 1)
    else:
        content = content.rstrip() + "\n\n" + entry + "\n"
    atomic_write(sidebar, content)


def ensure_tracked_worktree_clean(root: Path) -> None:
    output = run_git(root, ["status", "--porcelain"]).stdout
    dirty = [
        line
        for line in output.splitlines()
        if line and not line.startswith("??")
    ]
    if dirty:
        raise IssueWorkflowError(
            "发布前工作区存在未提交的 tracked 修改；请先提交或还原：\n"
            + "\n".join(dirty)
        )


def release_commit_message(tag: str) -> str:
    return f"chore(release): 发布 {tag}\n"


def release_state_path(root: Path, tag: str) -> Path:
    return root / RELEASE_STATE_ROOT / f"{tag}.state.json"


def read_release_state(root: Path, tag: str) -> dict[str, Any]:
    value = read_json(release_state_path(root, tag), {})
    return value if isinstance(value, dict) else {}


def write_release_state(root: Path, tag: str, value: dict[str, Any]) -> None:
    write_json(release_state_path(root, tag), value)


def resolve_release_notes(root: Path, value: str) -> Path:
    path = Path(value).expanduser()
    if not path.is_absolute():
        path = root / path
    if not path.is_file():
        raise IssueWorkflowError(f"发布说明文件不存在：{path}")
    return path.resolve()


def release_plan(
    root: Path,
    *,
    version_value: Optional[str],
    notes_value: Optional[str],
    fetch: bool,
    generated_at: Optional[datetime] = None,
) -> dict[str, Any]:
    if fetch:
        if not repository_remote_url(root, "origin"):
            raise IssueWorkflowError("缺少 origin remote，无法发布版本")
        run_git_sensitive(root, ["fetch", "origin", "--tags", "--prune"])
    branch = release_branch(root)
    version, declared, latest = resolve_release_version(root, version_value)
    if remote_tag_exists(root, version.text, fetch=False):
        raise IssueWorkflowError(f"origin 已存在 tag {version.text}")
    generated_at = generated_at or now_local()
    notes_path = (
        Path(notes_value).expanduser()
        if notes_value
        else release_draft_path(root, version.text)
    )
    if not notes_path.is_absolute():
        notes_path = root / notes_path
    notes = render_release_notes(root, version, latest, generated_at)
    doc_path = release_doc_path(root, version.text)
    commit_message = release_commit_message(version.text)
    files = [
        target.path for target in RELEASE_TARGETS
    ] + [
        doc_path.relative_to(root).as_posix(),
        (root / RELEASE_DOC_ROOT / "README.md").relative_to(root).as_posix(),
    ]
    if (root / "doc" / "_sidebar.md").is_file():
        files.append("doc/_sidebar.md")
    return {
        "dry_run": dry_run(),
        "version": version.text,
        "declared_version": declared.text,
        "previous_tag": latest.text if latest else "",
        "branch": branch,
        "notes_path": str(notes_path),
        "notes": notes,
        "doc_path": doc_path.relative_to(root).as_posix(),
        "files": files,
        "commit_message": commit_message,
        "tag_command": (
            f"git tag -a {shlex.quote(version.text)} -F "
            f"{shlex.quote(doc_path.relative_to(root).as_posix())}"
        ),
        "push_command": (
            f"git push origin HEAD:refs/heads/{branch} "
            f"refs/tags/{shlex.quote(version.text)}"
        ),
        "generated_at": generated_at.isoformat(timespec="seconds"),
    }


def execute_release(
    root: Path,
    *,
    version_value: Optional[str],
    notes_value: Optional[str],
    fetch: bool,
    preview: bool,
    generated_at: Optional[datetime] = None,
    github_timeout: int = 120,
    github_interval: float = 5.0,
) -> dict[str, Any]:
    if not notes_value:
        raise IssueWorkflowError(
            "release --execute 需要 --notes 指定已审核的发布说明；"
            "请先运行 release 生成草稿"
        )
    plan = release_plan(
        root,
        version_value=version_value,
        notes_value=notes_value,
        fetch=fetch,
        generated_at=generated_at,
    )
    if preview:
        plan["dry_run"] = True
        return {**plan, "preview": True}
    version = parse_release_version(plan["version"])
    notes_path = Path(plan["notes_path"])
    if not notes_path.is_file():
        raise IssueWorkflowError(f"发布说明文件不存在：{notes_path}")
    notes = notes_path.read_text(encoding="utf-8").strip()
    if not notes:
        raise IssueWorkflowError("发布说明不能为空")
    if RELEASE_REVIEW_MARKER in notes:
        raise IssueWorkflowError(
            "发布说明仍是未审核的草稿；请补充版本重点并移除审核提示后再执行"
        )
    ensure_tracked_worktree_clean(root)
    clients = {
        platform: PlatformClient.from_environment(platform)
        for platform in ("gitee", "github")
    }
    for platform, client in clients.items():
        if not client.token:
            raise IssueWorkflowError(f"缺少 {platform} token，无法发布版本")
        client.check_repo()
        existing = client.get_release(version.text)
        if existing is not None:
            raise IssueWorkflowError(
                f"{platform} 已存在 Release {version.text}"
            )
    plan["notes"] = notes
    updated = apply_version_targets(root, version.text, plan["branch"])
    doc_path = release_doc_path(root, version.text)
    atomic_write(doc_path, notes.rstrip() + "\n")
    update_release_index(root, version.text, generated_at or now_local())
    update_release_sidebar(root)
    files = updated + [
        doc_path.relative_to(root).as_posix(),
        (root / RELEASE_DOC_ROOT / "README.md").relative_to(root).as_posix(),
    ]
    if (root / "doc" / "_sidebar.md").is_file():
        files.append("doc/_sidebar.md")
    run_git(root, ["add", "--", *files], capture=False)
    run_git(
        root,
        ["commit", "-F", "-"],
        capture=False,
        text_input=release_commit_message(version.text),
    )
    sha = run_git(root, ["rev-parse", "HEAD"]).stdout.strip()
    run_git(
        root,
        [
            "tag",
            "-a",
            version.text,
            "-F",
            doc_path.relative_to(root).as_posix(),
            sha,
        ],
        capture=False,
    )
    run_git_sensitive(
        root,
        [
            "push",
            "origin",
            f"HEAD:refs/heads/{plan['branch']}",
            f"refs/tags/{version.text}",
        ],
    )
    state = {
        "tag": version.text,
        "commit": sha,
        "branch": plan["branch"],
        "notes": doc_path.relative_to(root).as_posix(),
        "git": {
            "committed": True,
            "tagged": True,
            "pushed": True,
            "at": iso_now(),
        },
        "platforms": {},
    }
    write_release_state(root, version.text, state)
    result = publish_release(
        root,
        tag=version.text,
        commit=sha,
        notes=str(doc_path),
        preview=False,
        github_timeout=github_timeout,
        github_interval=github_interval,
    )
    return {
        **plan,
        "commit": sha,
        "platforms": result["platforms"],
        "ok": result["ok"],
    }


def normalize_release_body(value: str) -> str:
    return re.sub(r"\r\n?", "\n", value or "").strip()


def publish_release(
    root: Path,
    *,
    tag: str,
    commit: str,
    notes: str,
    preview: bool = False,
    github_timeout: int = 120,
    github_interval: float = 5.0,
    sleep: Callable[[float], None] = time.sleep,
    clock: Callable[[], float] = time.monotonic,
) -> dict[str, Any]:
    version = parse_release_version(tag)
    if not re.fullmatch(r"[0-9a-fA-F]{7,64}", commit):
        raise IssueWorkflowError(f"提交 SHA 格式无效：{commit}")
    notes_path = resolve_release_notes(root, notes)
    body = notes_path.read_text(encoding="utf-8").strip()
    if not body:
        raise IssueWorkflowError("发布说明不能为空")
    if RELEASE_REVIEW_MARKER in body:
        raise IssueWorkflowError(
            "发布说明仍是未审核的草稿；请补充版本重点并移除审核提示后再发布"
        )
    if preview:
        return {
            "ok": True,
            "platforms": {
                platform: {"status": "would_create"}
                for platform in ("gitee", "github")
            },
        }
    clients = {
        platform: PlatformClient.from_environment(platform)
        for platform in ("gitee", "github")
    }
    existing_releases: dict[str, dict[str, Any]] = {}
    for platform, client in clients.items():
        if not client.token:
            raise IssueWorkflowError(f"缺少 {platform} token，无法发布版本")
        client.check_repo()
        existing = client.get_release(version.text)
        if existing is not None:
            existing_body = normalize_release_body(
                str(existing.get("body") or "")
            )
            if existing_body != normalize_release_body(body):
                raise IssueWorkflowError(
                    f"{platform} 已存在同名 Release，但说明内容不同"
                )
            existing_releases[platform] = existing
    results: dict[str, Any] = {}
    failures = 0
    for platform in ("gitee", "github"):
        client = clients[platform]
        try:
            existing = existing_releases.get(platform)
            if existing is not None:
                results[platform] = {
                    "status": "exists",
                    "url": str(
                        existing.get("html_url")
                        or existing.get("url")
                        or ""
                    ),
                }
                continue
            if preview:
                results[platform] = {"status": "would_create"}
                continue
            target = commit
            if platform == "github":
                target = publish_github_release(
                    client,
                    version,
                    commit,
                    github_timeout=github_timeout,
                    github_interval=github_interval,
                    sleep=sleep,
                    clock=clock,
                )
            created = client.create_release(
                version.text,
                version.text,
                body,
                target_commitish=target,
                prerelease=version.prerelease,
            )
            results[platform] = {
                "status": "created",
                "url": str(
                    created.get("html_url") or created.get("url") or ""
                ),
            }
        except Exception as exc:
            failures += 1
            results[platform] = {"status": "failed", "error": str(exc)}
    state = read_release_state(root, version.text)
    state.update(
        {
            "tag": version.text,
            "commit": commit,
            "notes": notes_path.relative_to(root).as_posix()
            if notes_path.is_relative_to(root)
            else str(notes_path),
            "platforms": results,
            "updated_at": iso_now(),
        }
    )
    write_release_state(root, version.text, state)
    return {"ok": failures == 0, "platforms": results}


def publish_github_release(
    client: PlatformClient,
    version: ReleaseVersion,
    commit: str,
    *,
    github_timeout: int,
    github_interval: float,
    sleep: Callable[[float], None],
    clock: Callable[[], float],
) -> str:
    deadline = clock() + max(0, github_timeout)
    while True:
        if client.tag_exists(version.text):
            return ""
        if clock() >= deadline:
            print(
                f"[WARN] GitHub 尚未同步 tag {version.text}；"
                f"将使用 target_commitish={commit[:8]} 创建 Release。",
                file=sys.stderr,
            )
            return commit
        sleep(github_interval)


def build_manual_issue_body(summary: str, verification: str = "") -> str:
    description = compact_text(redact_sensitive(summary), 4000)
    if not description:
        raise IssueWorkflowError("issue 问题摘要不能为空")
    lines = ["### 问题描述", description]
    verified = compact_text(redact_sensitive(verification), 1200)
    if verified:
        lines.extend(["", "### 验证结果", verified])
    return "\n".join(lines).rstrip() + "\n"


def create_issue_for_request(
    platform: str,
    title: str,
    summary: str,
    verification: str = "",
    labels: Sequence[str] = (),
    *,
    preview: bool = False,
) -> dict[str, Any]:
    if platform not in {"github", "gitee"}:
        raise IssueWorkflowError(f"不支持的平台：{platform}")
    if not title.strip():
        raise IssueWorkflowError("issue 标题不能为空")
    cleaned_title = clean_title(title)
    body = build_manual_issue_body(summary, verification)
    label_values = tuple(
        dict.fromkeys(
            item.strip() for item in labels if item and item.strip()
        )
    )
    if preview:
        return {
            "dry_run": True,
            "platform": platform,
            "title": cleaned_title,
            "body": body,
            "labels": list(label_values),
        }
    client = PlatformClient.from_environment(platform)
    if not client.token:
        raise IssueWorkflowError(f"缺少 {platform} token，无法创建 issue")
    number = client.create_issue(cleaned_title, body, label_values)
    reference = f"{platform}#{number}"
    return {
        "dry_run": False,
        "platform": platform,
        "number": number,
        "ref": reference,
        "url": client.issue_url(number),
        "title": cleaned_title,
        "labels": list(label_values),
    }


def close_issues(
    references: Sequence[str],
    *,
    comment: str = "",
    preview: bool = False,
) -> list[dict[str, Any]]:
    if not references:
        raise IssueWorkflowError("至少需要一个 github#N 或 gitee#ID 引用")
    parsed: list[tuple[str, str]] = []
    for value in references:
        ref = parse_issue_selection(value)
        if ref not in parsed:
            parsed.append(ref)
    results: list[dict[str, Any]] = []
    for platform, number in parsed:
        reference = f"{platform}#{number}"
        client = PlatformClient.from_environment(platform)
        result: dict[str, Any] = {
            "platform": platform,
            "number": number,
            "ref": reference,
            "url": client.issue_url(number),
        }
        try:
            issue = client.get_issue(number)
            result["previous_state"] = issue.state
            if preview:
                result.update(
                    ok=True,
                    dry_run=True,
                    state=issue.state,
                    changed=False,
                    commented=False,
                )
                results.append(result)
                continue
            if not client.token:
                raise IssueWorkflowError(f"缺少 {platform} token，无法关闭 issue")
            changed = issue.state.lower() != "closed"
            if changed and comment:
                client.comment_issue(number, comment)
            if changed:
                client.close_issue(number)
            verified = client.get_issue(number)
            if verified.state.lower() != "closed":
                raise IssueWorkflowError(
                    f"{reference} 关闭后状态仍为 {verified.state or '<未知>'}"
                )
            result.update(
                dry_run=False,
                ok=True,
                state=verified.state,
                changed=changed,
                commented=bool(changed and comment),
            )
        except Exception as exc:
            result.update(ok=False, error=str(exc))
        results.append(result)
    return results


def close_queue_path(root: Path) -> Path:
    return root / STATE_ROOT / "retry-close.json"


def read_close_queue(root: Path) -> list[dict[str, Any]]:
    value = read_json(close_queue_path(root), [])
    if not isinstance(value, list):
        return []
    return [item for item in value if isinstance(item, dict)]


def write_close_queue(root: Path, queue: list[dict[str, Any]]) -> None:
    write_json(close_queue_path(root), queue)


def queue_close(root: Path, entry: dict[str, Any]) -> None:
    queue = read_close_queue(root)
    key = (entry.get("platform"), entry.get("number"), entry.get("sha"))
    queue = [
        item
        for item in queue
        if (item.get("platform"), item.get("number"), item.get("sha")) != key
    ]
    queue.append(entry)
    write_close_queue(root, queue)


def remove_close_queue_entry(root: Path, entry: dict[str, Any]) -> None:
    queue = read_close_queue(root)
    key = (entry.get("platform"), entry.get("number"), entry.get("sha"))
    queue = [
        item
        for item in queue
        if (item.get("platform"), item.get("number"), item.get("sha")) != key
    ]
    write_close_queue(root, queue)


def close_trace_comment(entry: dict[str, Any], *, verification: str = "") -> str:
    branch = entry.get("branch", "")
    commit_url = entry.get("commit_url", "")
    sha = entry.get("sha", "")
    rows = [
        "已完成代码修复并通过本地验证。",
        "",
        f"- 提交：`{sha}`",
    ]
    if branch:
        rows.append(f"- 分支：`{branch}`")
    if commit_url:
        rows.append(f"- 链接：{commit_url}")
    if verification:
        rows.extend(["", "### 验证结果", redact_sensitive(verification)])
    rows.extend(["", f"<!-- zenvis-issue-close:{sha} -->"])
    return "\n".join(rows)


def close_issue_with_trace(
    root: Path,
    client: PlatformClient,
    platform: str,
    number: str,
    sha: str,
    branch: str,
    verification: str = "",
) -> None:
    entry = {
        "platform": platform,
        "number": number,
        "repository": client.repo,
        "sha": sha,
        "branch": branch,
        "commit_url": client.commit_url(sha),
        "comment": close_trace_comment(
            {
                "sha": sha,
                "branch": branch,
                "commit_url": client.commit_url(sha),
            },
            verification=verification,
        ),
        "comment_done": False,
        "created_at": iso_now(),
    }
    try:
        marker = f"<!-- zenvis-issue-close:{sha} -->"
        existing = client.list_comments(number)
        if not any(marker in str(item.get("body") or "") for item in existing):
            client.comment_issue(number, str(entry["comment"]))
        entry["comment_done"] = True
        client.close_issue(number)
    except Exception:
        queue_close(root, entry)
        raise
    remove_close_queue_entry(root, entry)


def retry_close_queue(root: Path, *, preview: bool = False) -> int:
    queue = read_close_queue(root)
    if not queue:
        print("关闭重试队列为空。")
        return 0
    if preview:
        for entry in queue:
            print(
                f"[DRY RUN] 将重试评论并关闭 "
                f"{entry.get('platform')}#{entry.get('number')}"
            )
        return 0
    remaining: list[dict[str, Any]] = []
    failures = 0
    for entry in queue:
        try:
            client = PlatformClient.from_environment(
                str(entry.get("platform")),
                repo=str(entry.get("repository") or ""),
            )
            if not client.token:
                raise IssueWorkflowError(
                    f"缺少 {entry.get('platform')} token，无法重试关闭"
                )
            if not entry.get("comment_done"):
                marker = f"<!-- zenvis-issue-close:{entry.get('sha')} -->"
                number = normalize_issue_number(
                    str(entry.get("platform")), entry.get("number")
                )
                existing = client.list_comments(number)
                if not any(marker in str(item.get("body") or "") for item in existing):
                    client.comment_issue(
                        number, str(entry.get("comment") or "")
                    )
                entry["comment_done"] = True
            client.close_issue(
                normalize_issue_number(
                    str(entry.get("platform")), entry.get("number")
                )
            )
            print(f"[OK] 已关闭 {entry.get('platform')}#{entry.get('number')}")
        except Exception as exc:
            failures += 1
            remaining.append(entry)
            print(
                f"[WARN] 重试关闭 {entry.get('platform')}#{entry.get('number')} 失败：{exc}",
                file=sys.stderr,
            )
    write_close_queue(root, remaining)
    return 1 if failures else 0


def ensure_origin(root: Path) -> str:
    origin_url = repository_remote_url(root, "origin")
    if not origin_url:
        raise IssueWorkflowError("缺少 origin remote，无法推送到 Gitee")
    return origin_url


def collect_unpushed_commits(
    root: Path, limit: int = 50
) -> list[tuple[str, str]]:
    output = run_git(
        root,
        [
            "rev-list",
            "--max-count",
            str(max(1, limit)),
            "HEAD",
            "--not",
            "--remotes=origin",
        ],
    ).stdout
    commits: list[tuple[str, str]] = []
    for sha in [line.strip() for line in output.splitlines() if line.strip()]:
        message = run_git(root, ["show", "-s", "--format=%B", sha]).stdout
        commits.append((sha, message))
    return commits


def push_current_branch(
    root: Path,
    *,
    branch: Optional[str],
    verification: str,
    no_close: bool,
    preview: bool,
    fetch: bool = True,
) -> int:
    branch = branch or current_branch(root)
    origin_url = ensure_origin(root)
    if fetch and not preview:
        run_git(root, ["fetch", "origin", "--prune"], capture=False)
    commits = collect_unpushed_commits(root)
    refs: list[tuple[str, str]] = []
    ref_shas: dict[tuple[str, str], str] = {}
    for sha, message in commits:
        commit_refs = parse_trailers(message)
        if len(commit_refs) > 1:
            raise IssueWorkflowError(
                f"提交 {sha[:8]} 关联多个 issue；请拆分提交后再推送"
            )
        for ref in commit_refs:
            if ref not in refs:
                refs.append(ref)
                ref_shas[ref] = sha
            elif ref_shas[ref] != sha:
                previous = ref_shas[ref]
                raise IssueWorkflowError(
                    f"{ref[0]}#{ref[1]} 同时出现在提交 {previous[:8]} 和 "
                    f"{sha[:8]}；一个 issue 只能对应一个提交，请拆分或合并提交"
                )
    print(f"Gitee origin: {redact_sensitive(origin_url)}")
    print("GitHub：由 Gitee 自动镜像，无需本地 github remote")
    print(f"分支：{branch}")
    print(f"待处理提交：{len(commits)}，待关闭 issue：{len(refs)}")
    if preview:
        print("[DRY RUN] 将推送到 Gitee origin；镜像 Action 负责关闭 GitHub/Gitee issue。")
    else:
        run_git(root, ["push", "origin", f"HEAD:refs/heads/{branch}"], capture=False)
    if no_close or not refs:
        return 0
    failures = 0
    for platform, number in refs:
        issue_sha = ref_shas[(platform, number)]
        if platform != "gitee":
            if preview:
                print(f"[DRY RUN] 镜像 Action 将在镜像到达后关闭 github#{number}")
            else:
                print(f"[WAIT] github#{number} 由镜像 Action 在确认提交后关闭")
            continue
        try:
            client = PlatformClient.from_environment(platform)
            if preview:
                commit_url = client.commit_url(issue_sha)
                print(
                    f"[DRY RUN] 将评论并关闭 gitee#{number}：{commit_url}"
                )
                continue
            if not client.token:
                raise IssueWorkflowError(f"缺少 {platform} token")
            close_issue_with_trace(
                root,
                client,
                platform,
                number,
                issue_sha,
                branch,
                verification,
            )
            print(f"[OK] 已评论并关闭 gitee#{number}")
        except Exception as exc:
            failures += 1
            print(f"[WARN] 关闭 gitee#{number} 失败：{exc}", file=sys.stderr)
    if failures:
        print(
            "推送已完成，但部分 Gitee issue 关闭失败；请执行 "
            "`python3 agent-skills/zenvis-issue-workflow/scripts/zenvis_issue.py "
            "retry-close`。",
            file=sys.stderr,
        )
        return 1
    return 0


def list_recent_commits(root: Path, limit: int) -> list[tuple[str, str]]:
    output = run_git(
        root, ["log", f"-{max(1, limit)}", "--format=%H%x1f%B%x1e"]
    ).stdout
    commits: list[tuple[str, str]] = []
    for record in output.split("\x1e"):
        fields = record.strip("\n").split("\x1f", 1)
        if len(fields) != 2:
            continue
        sha = fields[0].strip()
        message = fields[1].strip()
        if sha:
            commits.append((sha, message))
    return commits


def reconcile_issues(
    root: Path,
    *,
    limit: int,
    fetch: bool,
    verification: str,
    preview: bool,
) -> int:
    if fetch and not preview:
        if repository_remote_url(root, "origin"):
            run_git(root, ["fetch", "origin", "--prune"], capture=False)
    entries: list[tuple[str, list[tuple[str, str]]]] = []
    seen: dict[tuple[str, str], str] = {}
    duplicate = False
    for sha, message in list_recent_commits(root, limit):
        refs = parse_trailers(message)
        if len(refs) > 1:
            print(f"[WARN] {sha[:8]} 关联多个 issue，已跳过", file=sys.stderr)
            duplicate = True
            continue
        for ref in refs:
            previous = seen.get(ref)
            if previous and previous != sha:
                print(
                    f"[WARN] {ref[0]}#{ref[1]} 同时出现在提交 {previous[:8]} 和 "
                    f"{sha[:8]}；一个 issue 只能对应一个提交",
                    file=sys.stderr,
                )
                duplicate = True
            else:
                seen[ref] = sha
        entries.append((sha, refs))
    if duplicate:
        return 1
    failures = 0
    candidates = 0
    for sha, refs in entries:
        for platform, number in refs:
            try:
                client = PlatformClient.from_environment(platform)
                if preview:
                    print(f"[DRY RUN] 将关闭 {platform}#{number}（{sha[:8]}）")
                    continue
                if not client.token:
                    raise IssueWorkflowError(f"缺少 {platform} token")
                if not client.commit_exists(sha):
                    print(
                        f"[WAIT] {sha[:8]} 尚未出现在 {platform}，保留 "
                        f"{platform}#{number} 打开"
                    )
                    continue
                candidates += 1
                close_issue_with_trace(
                    root,
                    client,
                    platform,
                    number,
                    sha,
                    current_branch(root),
                    verification,
                )
                print(f"[OK] 已关闭 {platform}#{number}")
            except Exception as exc:
                failures += 1
                print(f"[WARN] 关闭 {platform}#{number} 失败：{exc}", file=sys.stderr)
    print(f"已检查 {candidates} 个可关闭关联。")
    return 1 if failures else 0


def action_before_after(root: Path, before: str, after: str) -> list[tuple[str, str]]:
    if before and set(before) != {"0"}:
        output = run_git(root, ["rev-list", "--reverse", f"{before}..{after}"]).stdout
    else:
        output = run_git(root, ["rev-list", "-n", "1", after]).stdout
    commits: list[tuple[str, str]] = []
    for sha in [line.strip() for line in output.splitlines() if line.strip()]:
        message = run_git(root, ["show", "-s", "--format=%B", sha]).stdout
        commits.append((sha, message))
    return commits


def action_close(
    root: Path,
    *,
    before: str,
    after: str,
    verification: str,
    preview: bool,
) -> int:
    entries: list[tuple[str, list[tuple[str, str]]]] = []
    seen: dict[tuple[str, str], str] = {}
    duplicate = False
    for sha, message in action_before_after(root, before, after):
        refs = parse_trailers(message)
        if len(refs) > 1:
            print(f"[WARN] {sha[:8]} 关联多个 issue，Action 跳过", file=sys.stderr)
            duplicate = True
            continue
        for ref in refs:
            previous = seen.get(ref)
            if previous and previous != sha:
                print(
                    f"[WARN] {ref[0]}#{ref[1]} 同时出现在提交 {previous[:8]} 和 "
                    f"{sha[:8]}；Action 跳过整批关闭",
                    file=sys.stderr,
                )
                duplicate = True
            else:
                seen[ref] = sha
        entries.append((sha, refs))
    if duplicate:
        return 1
    failures = 0
    for sha, refs in entries:
        for platform, number in refs:
            try:
                client = PlatformClient.from_environment(platform)
                if preview:
                    print(
                        f"[DRY RUN] Action 将关闭 {platform}#{number}（{sha[:8]}）"
                    )
                    continue
                if not client.token:
                    raise IssueWorkflowError(f"Action 缺少 {platform} token")
                if not client.commit_exists(sha):
                    print(
                        f"[WAIT] {sha[:8]} 尚未出现在 {platform}，保留 "
                        f"{platform}#{number} 打开"
                    )
                    continue
                close_issue_with_trace(
                    root,
                    client,
                    platform,
                    number,
                    sha,
                    os.environ.get("GITHUB_REF_NAME", "main"),
                    verification,
                )
                print(f"[OK] Action 已关闭 {platform}#{number}")
            except Exception as exc:
                failures += 1
                print(
                    f"[WARN] Action 关闭 {platform}#{number} 失败：{exc}",
                    file=sys.stderr,
                )
    return 1 if failures else 0


def load_report_issues(
    root: Path, report_value: Optional[str], selections: Sequence[str]
) -> tuple[Path, list[ReportIssue]]:
    report_dir = resolve_report(root, report_value)
    issues = parse_report(report_dir)
    if selections:
        requested = [parse_issue_selection(value) for value in selections]
        by_ref = {item.ref: item for item in issues}
        missing = [
            f"{platform}#{number}"
            for platform, number in requested
            if f"{platform}#{number}" not in by_ref
        ]
        if missing:
            raise IssueWorkflowError(
                "报告中不存在或已被删减的 issue：" + "、".join(missing)
            )
        issues = [by_ref[f"{platform}#{number}"] for platform, number in requested]
    return report_dir, issues


def repair_session_path(root: Path) -> Path:
    return root / STATE_ROOT / "repair-session.json"


def prepare_repair(
    root: Path,
    *,
    report_value: Optional[str],
    selections: Sequence[str],
    preview: bool,
    quiet: bool = False,
) -> list[ReportIssue]:
    report_dir, issues = load_report_issues(root, report_value, selections)
    output = sys.stderr if quiet else sys.stdout
    if not issues:
        print(f"报告中没有保留的 issue：{report_dir}", file=output)
        return []
    base = run_git(root, ["rev-parse", "HEAD"]).stdout.strip()
    session = {
        "report_dir": str(report_dir),
        "base_sha": base,
        "started_at": iso_now(),
        "pending": [item.ref for item in issues],
        "completed": [],
        "failed": [],
    }
    if not preview:
        write_json(repair_session_path(root), session)
    for item in issues:
        print(f"\n## {item.ref} — {item.title}", file=output)
        print(f"原始链接：{item.url}", file=output)
        print(f"需求摘要：\n{item.requirement}", file=output)
        print(f"修复范围：\n{item.scope}", file=output)
        print(f"验收标准：\n{item.acceptance}", file=output)
        print(f"风险与待确认：\n{item.risks}", file=output)
    print(
        "\n修复会话已准备。当前基准提交为 "
        f"{base[:8]}。直接在当前分支处理，每个 issue 形成一个 commit；"
        "完成后正常 git push，由镜像 Action 确认提交后关闭 issue。",
        file=output,
    )
    return issues


def run_report(
    root: Path,
    *,
    github_repo: Optional[str],
    gitee_repo: Optional[str],
    state: str,
    preview: bool,
) -> int:
    clients = [
        PlatformClient.from_environment("github", repo=github_repo),
        PlatformClient.from_environment("gitee", repo=gitee_repo),
    ]
    issues: list[Issue] = []
    for client in clients:
        try:
            issues.extend(client.list_issues(state=state))
        except Exception as exc:
            print(f"[WARN] 读取 {client.platform} issue 失败：{exc}", file=sys.stderr)
            return 1
    directory = create_report(issues, root=root, state=state)
    github_count = sum(1 for item in issues if item.platform == "github")
    gitee_count = sum(1 for item in issues if item.platform == "gitee")
    print(f"报告已生成：{directory}")
    print(f"GitHub {github_count} 条，Gitee {gitee_count} 条。")
    if preview:
        print("[DRY RUN] 仅生成本地报告，未修改平台 issue。")
    return 0


def doctor(root: Path, *, offline: bool) -> int:
    checks: list[tuple[str, str]] = []
    checks.append(("OK", f"Python {sys.version.split()[0]}"))
    checks.append(("OK", f"仓库根目录：{root}"))
    local_env = root / LOCAL_ENV_FILE
    checks.append(
        (
            "OK" if local_env.is_file() else "INFO",
            "本地环境文件 .zenvis/.env"
            if local_env.is_file()
            else "本地环境文件 .zenvis/.env 未配置",
        )
    )
    hooks_result = run_git(root, ["config", "--get", "core.hooksPath"], check=False)
    hooks_path = hooks_result.stdout.strip() if hooks_result.returncode == 0 else ""
    if not hooks_path:
        checks.append(("OK", "Git hooks：未启用"))
    elif hooks_path == ".githooks":
        checks.append(
            (
                "WARN",
                "检测到旧 core.hooksPath=.githooks；请执行 "
                "`git config --unset core.hooksPath`",
            )
        )
    else:
        checks.append(("WARN", f"core.hooksPath={hooks_path}（非本工作流管理）"))
    origin = repository_remote_url(root, "origin")
    checks.append(("OK" if origin else "ERROR", "Gitee remote origin"))
    for platform in ("github", "gitee"):
        token = PlatformClient.from_environment(platform).token
        checks.append(("OK" if token else "WARN", f"{platform} token"))
    if not offline:
        for platform in ("github", "gitee"):
            client = PlatformClient.from_environment(platform)
            try:
                client.check_repo()
                checks.append(("OK", f"{platform} 仓库可访问：{client.repo}"))
            except Exception as exc:
                checks.append(("ERROR", f"{platform} 仓库检查失败：{exc}"))
    for level, text in checks:
        print(f"[{level}] {text}")
    return 1 if any(level == "ERROR" for level, _ in checks) else 0


def command_create_issue(args: argparse.Namespace) -> int:
    result = create_issue_for_request(
        args.platform,
        args.title,
        args.summary,
        verification=args.verification,
        labels=args.label,
        preview=dry_run(),
    )
    if args.json:
        print(json.dumps(result, ensure_ascii=False, indent=2))
        return 0
    if result["dry_run"]:
        print(
            f"[DRY RUN] 未创建 {result['platform']} issue：{result['title']}"
        )
        print(result["body"], end="")
        print("移除 ZENVIS_ISSUE_DRY_RUN=1 后可正式创建。")
        return 0
    print(f"ID：{result['number']}")
    print(f"引用：{result['ref']}")
    print(f"链接：{result['url']}")
    return 0


def command_commit(args: argparse.Namespace) -> int:
    message = resolve_commit_message(args.issue, args.message)
    if args.execute:
        root = resolve_root(args.root)
        sha = execute_commit(root, args.file, message)
        print(f"[OK] 已创建提交 {sha}（未推送）。")
        return 0
    print(build_commit_commands(args.file, message), end="")
    return 0


def command_commit_command(args: argparse.Namespace) -> int:
    message = resolve_commit_message(args.issue, args.message)
    print(build_commit_commands(args.file, message), end="")
    return 0


def command_report(args: argparse.Namespace) -> int:
    root = resolve_root(args.root)
    return run_report(
        root,
        github_repo=args.github_repo,
        gitee_repo=args.gitee_repo,
        state=args.state,
        preview=dry_run(),
    )


def command_repair(args: argparse.Namespace) -> int:
    root = resolve_root(args.root)
    issues = prepare_repair(
        root,
        report_value=args.report,
        selections=args.issue,
        preview=dry_run(),
        quiet=args.json,
    )
    if args.json:
        payload = [
            {**asdict(item), "ref": item.ref}
            for item in issues
        ]
        print(json.dumps(payload, ensure_ascii=False, indent=2))
    return 0


def command_show(args: argparse.Namespace) -> int:
    platform, number = parse_issue_selection(args.issue)
    client = PlatformClient.from_environment(platform)
    issue = client.get_issue(number)
    print(json.dumps(asdict(issue), ensure_ascii=False, indent=2))
    return 0


def command_close(args: argparse.Namespace) -> int:
    results = close_issues(
        args.issue,
        comment=args.comment,
        preview=dry_run(),
    )
    if args.json:
        print(json.dumps(results, ensure_ascii=False, indent=2))
    else:
        for item in results:
            if not item.get("ok"):
                print(
                    f"[WARN] {item['ref']} 关闭失败：{item.get('error')}",
                    file=sys.stderr,
                )
                continue
            if item.get("dry_run"):
                if item.get("previous_state", "").lower() == "closed":
                    print(f"[DRY RUN] {item['ref']} 已是 closed，无需关闭")
                else:
                    print(f"[DRY RUN] 将关闭 {item['ref']}（当前 {item['state']}）")
            elif item.get("changed"):
                print(f"[OK] 已关闭 {item['ref']}：{item['url']}")
            else:
                print(f"[OK] {item['ref']} 已是 closed：{item['url']}")
    return 0 if all(item.get("ok") for item in results) else 1


def command_push(args: argparse.Namespace) -> int:
    root = resolve_root(args.root)
    return push_current_branch(
        root,
        branch=args.branch,
        verification=args.verification,
        no_close=args.no_close,
        preview=dry_run(),
        fetch=not args.no_fetch,
    )


def command_reconcile(args: argparse.Namespace) -> int:
    root = resolve_root(args.root)
    return reconcile_issues(
        root,
        limit=args.limit,
        fetch=not args.no_fetch,
        verification=args.verification,
        preview=dry_run(),
    )


def command_release(args: argparse.Namespace) -> int:
    root = resolve_root(args.root)
    preview = dry_run()
    if args.execute:
        result = execute_release(
            root,
            version_value=args.version,
            notes_value=args.notes,
            fetch=not args.no_fetch,
            preview=preview,
            github_timeout=args.github_timeout,
        )
    else:
        result = release_plan(
            root,
            version_value=args.version,
            notes_value=args.notes,
            fetch=not args.no_fetch,
        )
        notes_path = Path(result["notes_path"])
        should_write = not preview and not (args.notes and notes_path.is_file())
        if should_write and notes_path.resolve().is_relative_to(root):
            relative = notes_path.resolve().relative_to(root)
            if relative.parts[:2] != (".zenvis", "releases"):
                raise IssueWorkflowError(
                    "准备阶段的草稿只能写入 .zenvis/releases/ 或仓库外路径；"
                    "已审核的说明请用 release --execute 发布"
                )
        if should_write:
            atomic_write(
                notes_path,
                str(result["notes"]).rstrip() + "\n",
            )
    if args.json:
        print(json.dumps(result, ensure_ascii=False, indent=2))
        return 0 if result.get("ok", True) else 1
    if not args.execute or result.get("preview"):
        print(f"目标版本：{result['version']}")
        print(f"当前声明版本：{result['declared_version']}")
        if result["previous_tag"]:
            print(f"上一版本：{result['previous_tag']}")
        print(f"发布分支：{result['branch']}")
        print(f"说明草稿：{result['notes_path']}")
        print("将更新：")
        for path in result["files"]:
            print(f"- {path}")
        print(f"提交信息：{str(result['commit_message']).strip()}")
        print(f"创建 tag：{result['tag_command']}")
        print(f"推送：{result['push_command']}")
        print("平台：创建 Gitee Release；等待 GitHub 镜像后创建 GitHub Release。")
        if preview:
            print("[DRY RUN] 未写入草稿、版本文件或平台数据。")
        else:
            print("审核说明后执行：")
            print(
                "python3 agent-skills/zenvis-issue-workflow/scripts/zenvis_issue.py "
                f"release --execute --notes {shlex.quote(str(result['notes_path']))}"
            )
        return 0
    platforms = result.get("platforms") or {}
    print(f"发布完成：{result['version']}")
    print(f"提交：{result.get('commit', '')}")
    for platform in ("gitee", "github"):
        item = platforms.get(platform) or {}
        status = item.get("status", "unknown")
        url = item.get("url", "")
        print(f"- {platform}: {status} {url}".rstrip())
    return 0 if result.get("ok") else 1


def command_release_publish(args: argparse.Namespace) -> int:
    root = resolve_root(args.root)
    result = publish_release(
        root,
        tag=args.tag,
        commit=args.commit,
        notes=args.notes,
        preview=dry_run(),
        github_timeout=args.github_timeout,
    )
    if args.json:
        print(json.dumps(result, ensure_ascii=False, indent=2))
        return 0 if result.get("ok") else 1
    for platform in ("gitee", "github"):
        item = result["platforms"].get(platform) or {}
        status = item.get("status", "unknown")
        url = item.get("url", "")
        error = item.get("error", "")
        suffix = f"：{error}" if error else f" {url}".rstrip()
        print(f"- {platform}: {status}{suffix}")
    return 0 if result.get("ok") else 1


def command_retry_close(args: argparse.Namespace) -> int:
    root = resolve_root(args.root)
    return retry_close_queue(root, preview=dry_run())


def command_doctor(args: argparse.Namespace) -> int:
    root = resolve_root(args.root)
    return doctor(root, offline=args.offline)


def command_action_close(args: argparse.Namespace) -> int:
    root = resolve_root(args.root)
    return action_close(
        root,
        before=args.before,
        after=args.after,
        verification=args.verification,
        preview=dry_run(),
    )


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="ZenVis GitHub / Gitee issue 归纳、追踪、关闭与版本发布工具"
    )
    parser.add_argument("--root", help="Git 仓库根目录，默认自动查询")
    subparsers = parser.add_subparsers(dest="command", required=True)

    report = subparsers.add_parser("report", help="生成 GitHub 和 Gitee issue 报告")
    report.add_argument("--github-repo", help="GitHub owner/repo")
    report.add_argument("--gitee-repo", help="Gitee owner/repo")
    report.add_argument("--state", default="open", choices=("open", "closed", "all"))
    report.set_defaults(func=command_report)

    create = subparsers.add_parser(
        "create-issue", help="按问题摘要立即创建 GitHub 或 Gitee issue"
    )
    create.add_argument("--platform", required=True, choices=("github", "gitee"))
    create.add_argument("--title", required=True, help="issue 标题")
    create.add_argument("--summary", required=True, help="已脱敏的问题摘要")
    create.add_argument("--verification", default="", help="可选的验证结果")
    create.add_argument(
        "--label", action="append", default=[], help="可选标签，可重复传入"
    )
    create.add_argument("--json", action="store_true", help="以 JSON 输出")
    create.set_defaults(func=command_create_issue)

    commit = subparsers.add_parser(
        "commit", help="生成提交命令；使用 --execute 时只执行 add 和 commit"
    )
    commit.add_argument("--message", required=True, help="完整提交信息")
    commit.add_argument(
        "--file", action="append", required=True, help="要暂存的文件，可重复传入"
    )
    commit.add_argument(
        "--issue",
        action="append",
        default=[],
        help="可选的 github#12 或 gitee#IKSGM9；一次只能指定一个",
    )
    commit.add_argument(
        "--execute", action="store_true", help="实际执行 git add 和 git commit，不推送"
    )
    commit.set_defaults(func=command_commit)

    commit_command = subparsers.add_parser(
        "commit-command", help="兼容别名：生成提交命令，不执行 Git"
    )
    commit_command.add_argument("--message", required=True, help="完整提交信息")
    commit_command.add_argument(
        "--file", action="append", required=True, help="要暂存的文件，可重复传入"
    )
    commit_command.add_argument(
        "--issue",
        action="append",
        default=[],
        help="可选的 github#12 或 gitee#IKSGM9；一次只能指定一个",
    )
    commit_command.set_defaults(func=command_commit_command)

    repair = subparsers.add_parser("repair", help="读取审核后的报告并准备修复会话")
    repair.add_argument("--report", help="报告目录，默认最新报告")
    repair.add_argument(
        "--issue", action="append", default=[], help="github#12 或 gitee#IKSGM9"
    )
    repair.add_argument("--json", action="store_true", help="输出 JSON")
    repair.set_defaults(func=command_repair)

    show = subparsers.add_parser("show", help="读取单个 issue 的当前平台内容")
    show.add_argument("issue", help="github#12 或 gitee#IKSGM9")
    show.set_defaults(func=command_show)

    close = subparsers.add_parser(
        "close", help="直接关闭一个或多个 issue，可重复执行"
    )
    close.add_argument(
        "issue",
        nargs="+",
        help="github#12 或 gitee#IKSGM9，可传多个",
    )
    close.add_argument(
        "--comment",
        default="",
        help="关闭前添加的说明；issue 已关闭时不会重复添加",
    )
    close.add_argument("--json", action="store_true", help="以 JSON 输出")
    close.set_defaults(func=command_close)

    release = subparsers.add_parser(
        "release", help="准备或执行版本发布，更新版本文件并创建 Gitee/GitHub Release"
    )
    release.add_argument("--version", help="目标版本；默认自动推算")
    release.add_argument(
        "--notes", help="准备时的草稿路径；执行时必须提供已审核的说明文件"
    )
    release.add_argument(
        "--execute",
        action="store_true",
        help="实际提交、打 tag、推送并创建两个平台 Release",
    )
    release.add_argument("--no-fetch", action="store_true", help="不拉取远端 tag")
    release.add_argument(
        "--github-timeout",
        type=int,
        default=120,
        help="等待 GitHub 镜像 tag 的秒数，默认 120",
    )
    release.add_argument("--json", action="store_true", help="以 JSON 输出")
    release.set_defaults(func=command_release)

    publish = subparsers.add_parser(
        "release-publish",
        help="仅重试平台 Release 创建，不重复 Git 操作",
    )
    publish.add_argument("--tag", required=True, help="版本 tag")
    publish.add_argument("--commit", required=True, help="Release 对应的提交 SHA")
    publish.add_argument("--notes", required=True, help="已审核的发布说明文件")
    publish.add_argument(
        "--github-timeout",
        type=int,
        default=120,
        help="等待 GitHub 镜像 tag 的秒数，默认 120",
    )
    publish.add_argument("--json", action="store_true", help="以 JSON 输出")
    publish.set_defaults(func=command_release_publish)

    push = subparsers.add_parser(
        "push", help="推送当前分支到 Gitee 并关闭关联的 Gitee issue"
    )
    push.add_argument("--branch", help="目标分支，默认当前分支")
    push.add_argument("--verification", default="见提交说明与本地测试结果")
    push.add_argument("--no-close", action="store_true")
    push.add_argument("--no-fetch", action="store_true")
    push.set_defaults(func=command_push)

    reconcile = subparsers.add_parser(
        "reconcile", help="确认提交已到达平台后补齐 issue 关闭"
    )
    reconcile.add_argument("--limit", type=int, default=50)
    reconcile.add_argument("--no-fetch", action="store_true")
    reconcile.add_argument("--verification", default="补充关闭：提交已到达远端")
    reconcile.set_defaults(func=command_reconcile)

    retry = subparsers.add_parser("retry-close", help="重试失败的 issue 关闭")
    retry.set_defaults(func=command_retry_close)

    doctor_parser = subparsers.add_parser("doctor", help="检查凭据、remote 和旧 hook 配置")
    doctor_parser.add_argument("--offline", action="store_true")
    doctor_parser.set_defaults(func=command_doctor)

    action = subparsers.add_parser("action-close", help=argparse.SUPPRESS)
    action.add_argument("--before", required=True)
    action.add_argument("--after", required=True)
    action.add_argument("--verification", default="GitHub Actions 检测到镜像提交")
    action.set_defaults(func=command_action_close)
    return parser


def main(argv: Optional[Sequence[str]] = None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)
    try:
        try:
            root = discover_root(args.root)
        except IssueWorkflowError:
            root = None
        if root is not None:
            load_local_env(root)
        return int(args.func(args))
    except IssueWorkflowError as exc:
        print(f"[ERROR] {exc}", file=sys.stderr)
        return 1
    except KeyboardInterrupt:
        print("[ERROR] 已中断", file=sys.stderr)
        return 130


if __name__ == "__main__":
    raise SystemExit(main())
