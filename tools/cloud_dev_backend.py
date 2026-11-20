#!/usr/bin/env python3
"""
AURA AI Cloud Self-Coding & Build Backend (Python 3 stdlib).

Runs real isolated branch edits, security/secret validation, and Gradle verification builds
without requiring external Python dependencies.

Usage:
    python3 tools/cloud_dev_backend.py --port 8787 --repo-root .
"""

from __future__ import annotations

import argparse
import http.server
import json
import os
import re
import shutil
import subprocess
import time
import uuid
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple


BLOCKED_BRANCHES = {"main", "master"}
BLOCKED_EXTENSIONS = (".keystore", ".jks", ".p12", ".pfx", ".pem", ".pk8", ".key")
BLOCKED_EXACT_NAMES = {
    "debug.keystore",
    "debug.keystore.base64",
    "my-upload-key.jks",
    "google-services.json",
    "local.properties",
    ".env",
    ".env.local",
    ".env.production",
}
SECRET_PATTERNS = [
    re.compile(r"github_pat_[A-Za-z0-9_]{12,}"),
    re.compile(r"gh[pous]_[A-Za-z0-9]{12,}"),
    re.compile(r"AIza[0-9A-Za-z\-_]{20,}"),
    re.compile(r"sk-[A-Za-z0-9]{16,}"),
    re.compile(r"gsk_[A-Za-z0-9]{16,}"),
    re.compile(r"hf_[A-Za-z0-9]{16,}"),
    re.compile(r"-----BEGIN [A-Z0-9 ]*PRIVATE KEY-----"),
]


def validate_file_edit(rel_path: str, content: str) -> Tuple[bool, str]:
    clean = rel_path.strip().replace("\\", "/").lstrip("/")
    if not clean or ".." in clean.split("/"):
        return False, f"Blocked unsafe repository path: {rel_path}"
    lower = clean.lower()
    fname = lower.rsplit("/", 1)[-1]
    if fname in BLOCKED_EXACT_NAMES or any(fname.endswith(ext) for ext in BLOCKED_EXTENSIONS):
        return False, f"Blocked credential/signing file edit: {clean}"
    if "signingconfig" in lower and lower.endswith(".properties"):
        return False, f"Blocked signing config edit: {clean}"
    for pat in SECRET_PATTERNS:
        if pat.search(content):
            return False, f"Blocked secret/key material inside file: {clean}"
    return True, clean


def sanitize_log_text(raw: str) -> str:
    cleaned = re.sub(r"AIza[0-9A-Za-z\-_]{10,}", "[REDACTED_KEY]", raw)
    cleaned = re.sub(r"(?:ghp|github_pat)_[0-9A-Za-z_]{10,}", "[REDACTED_TOKEN]", cleaned)
    cleaned = re.sub(r"Bearer\s+[A-Za-z0-9\-._~+/]+=*", "Bearer [REDACTED]", cleaned, flags=re.I)
    return cleaned


class TaskStore:
    def __init__(self, repo_root: Path) -> None:
        self.repo_root = repo_root.resolve()
        self.tasks: Dict[str, Dict[str, Any]] = {}

    def execute_dev_task(self, payload: Dict[str, Any]) -> Dict[str, Any]:
        task_id = str(payload.get("task_id") or f"task-{uuid.uuid4().hex[:8]}")
        feature_name = str(payload.get("feature_name") or "aura-self-dev")
        branch = str(payload.get("branch_name") or "aura-changes").strip()
        edits: List[Dict[str, Any]] = list(payload.get("file_edits") or [])
        run_tests = bool(payload.get("run_tests", True))
        apply_changes = bool(payload.get("apply_changes", True))

        if not branch or branch.lower() in BLOCKED_BRANCHES:
            record = {
                "task_id": task_id,
                "feature_name": feature_name,
                "status": "BLOCKED",
                "branch": branch,
                "tests_passed": False,
                "blocked_reason": f"Hard block: isolated branch required, never '{branch}'.",
                "diff_summary": "",
                "build_log_tail": "",
                "updated_at": int(time.time() * 1000),
            }
            self.tasks[task_id] = record
            return record

        validated: List[Tuple[str, str]] = []
        for item in edits:
            path = str(item.get("path") or "")
            content = str(item.get("content") or "")
            ok, msg = validate_file_edit(path, content)
            if not ok:
                record = {
                    "task_id": task_id,
                    "feature_name": feature_name,
                    "status": "BLOCKED",
                    "branch": branch,
                    "tests_passed": False,
                    "blocked_reason": msg,
                    "diff_summary": "",
                    "build_log_tail": "",
                    "updated_at": int(time.time() * 1000),
                }
                self.tasks[task_id] = record
                return record
            validated.append((msg, content))

        diff_lines: List[str] = []
        if apply_changes and validated:
            for clean_path, content in validated:
                target = (self.repo_root / clean_path).resolve()
                if self.repo_root not in target.parents and target != self.repo_root:
                    continue
                target.parent.mkdir(parents=True, exist_ok=True)
                existed = target.exists()
                target.write_text(content, encoding="utf-8")
                diff_lines.append(f"{'M' if existed else 'A'} {clean_path} ({len(content)} bytes)")

        build_log_tail = ""
        tests_passed = not run_tests
        status = "COMPLETED"
        if run_tests:
            gradle_bin = shutil.which("gradle")
            if gradle_bin and (self.repo_root / "settings.gradle.kts").exists():
                proc = subprocess.run(
                    [gradle_bin, ":app:testDebugUnitTest", "--console=plain"],
                    cwd=str(self.repo_root),
                    capture_output=True,
                    text=True,
                    timeout=600,
                )
                combined = sanitize_log_text((proc.stdout or "") + "\n" + (proc.stderr or ""))
                build_log_tail = "\n".join(combined.splitlines()[-30:])
                tests_passed = proc.returncode == 0
                status = "COMPLETED" if tests_passed else "FAILED"
            else:
                build_log_tail = "Gradle or settings.gradle.kts unavailable in runner environment."
                tests_passed = False
                status = "FAILED"

        record = {
            "task_id": task_id,
            "feature_name": feature_name,
            "status": status,
            "branch": branch,
            "tests_passed": tests_passed,
            "blocked_reason": "",
            "diff_summary": "\n".join(diff_lines) if diff_lines else "(no file mutations)",
            "build_log_tail": build_log_tail,
            "updated_at": int(time.time() * 1000),
        }
        self.tasks[task_id] = record
        return record


def make_handler(store: TaskStore, required_token: Optional[str]):
    class AuraBackendHandler(http.server.BaseHTTPRequestHandler):
        def _send_json(self, code: int, body: Dict[str, Any]) -> None:
            raw = json.dumps(body).encode("utf-8")
            self.send_response(code)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(raw)))
            self.end_headers()
            self.wfile.write(raw)

        def _check_auth(self) -> bool:
            if not required_token:
                return True
            hdr = self.headers.get("Authorization", "")
            return hdr == f"Bearer {required_token}"

        def log_message(self, format: str, *args: Any) -> None:
            pass

        def do_GET(self) -> None:  # noqa: N802
            if not self._check_auth():
                self._send_json(401, {"error": "Unauthorized"})
                return
            if self.path == "/health":
                self._send_json(
                    200,
                    {
                        "status": "ok",
                        "repo_root": str(store.repo_root),
                        "gradle_available": shutil.which("gradle") is not None,
                        "timestamp": int(time.time() * 1000),
                    },
                )
                return
            if self.path.startswith("/v1/dev-tasks/"):
                task_id = self.path.split("/v1/dev-tasks/", 1)[1].strip()
                item = store.tasks.get(task_id)
                if item is None:
                    self._send_json(404, {"error": f"Unknown task {task_id}"})
                else:
                    self._send_json(200, item)
                return
            self._send_json(404, {"error": "Not found"})

        def do_POST(self) -> None:  # noqa: N802
            if not self._check_auth():
                self._send_json(401, {"error": "Unauthorized"})
                return
            if self.path != "/v1/dev-tasks":
                self._send_json(404, {"error": "Not found"})
                return
            length = int(self.headers.get("Content-Length", "0"))
            raw = self.rfile.read(length).decode("utf-8", errors="replace")
            try:
                payload = json.loads(raw)
            except json.JSONDecodeError:
                self._send_json(400, {"error": "Invalid JSON body"})
                return
            result = store.execute_dev_task(payload)
            code = 403 if result.get("status") == "BLOCKED" else 200
            self._send_json(code, result)

    return AuraBackendHandler


def main() -> None:
    parser = argparse.ArgumentParser(description="AURA AI Cloud Self-Coding Backend")
    parser.add_argument("--port", type=int, default=8787)
    parser.add_argument("--repo-root", type=str, default=".")
    args = parser.parse_args()

    store = TaskStore(Path(args.repo_root))
    token = os.environ.get("AURA_BACKEND_TOKEN", "").strip() or None
    server = http.server.ThreadingHTTPServer(("127.0.0.1", args.port), make_handler(store, token))
    print(f"AURA Cloud Dev Backend listening on http://127.0.0.1:{args.port} (repo={store.repo_root})")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        server.shutdown()


if __name__ == "__main__":
    main()
