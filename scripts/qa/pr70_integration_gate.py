"""Exact-source PR70 hosted regression; raw artifacts are encrypted, never printed.

Uses the existing approved evidence validators. No production/bootstrap/guard changes.
"""
import argparse
import base64
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import signal
import subprocess
import sys
import tarfile
import tempfile

import verify_operations as qa

ROOT = Path(__file__).resolve().parents[2]
TARGETS = (
    "CancelledPaidRefundIT", "CancelledPaidRefundMigrationIT", "ManualCodRefundIT",
    "RefundLedgerMigrationIT", "ReturnReceiptMigrationIT", "ReturnToSenderMigrationIT",
    "VendorRevenueIT", "AdminRevenueIT",
)


def now():
    return datetime.now(timezone.utc).isoformat()


def digest(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def identity(expected):
    sha = qa.git_identity(ROOT)
    tree = subprocess.check_output(["git", "rev-parse", "HEAD^{tree}"], cwd=ROOT, text=True).strip()
    if sha != expected:
        raise qa.EvidenceError("Unexpected source identity")
    return {"sha": sha, "tree": tree, "clean": True}


def execute(name, argv, private, expected, timeout=1800):
    record = {"name": name, "argv": argv, "start_utc": now(),
              "source_before": identity(expected), "timeout_seconds": timeout}
    log = private / (name + ".log")
    print("Starting " + name, flush=True)
    with log.open("wb") as output:
        process = subprocess.Popen(argv, cwd=ROOT, stdout=output, stderr=subprocess.STDOUT,
                                   start_new_session=os.name != "nt")
        try:
            record["exit_code"] = process.wait(timeout=timeout)
            record["timeout"] = False
        except subprocess.TimeoutExpired:
            if os.name == "nt":
                process.kill()
            else:
                os.killpg(process.pid, signal.SIGKILL)
            record["exit_code"] = process.wait()
            record["timeout"] = True
    record.update(end_utc=now(), log=log.name, log_sha256=digest(log),
                  source_after=identity(expected))
    return record


def snapshot(private, name, full, successful):
    destination = private / name
    destination.mkdir()
    for folder in ("surefire-reports", "failsafe-reports"):
        source = ROOT / "target" / folder
        if source.exists():
            shutil.copytree(source, destination / folder)
    if not successful:
        return {"status": "FAIL", "raw_reports_retained": True}
    if full:
        collected = qa.collect(ROOT, qa.git_identity(ROOT))
        result = {key: collected[key] for key in
                  ("unit", "postgresql", "java_total", "summary_sha256", "failsafe_timeout")}
    else:
        unit = qa.collect_group(destination / "surefire-reports")
        postgres = qa.collect_group(destination / "failsafe-reports")
        actual = {row["suite"].rsplit(".", 1)[-1] for row in postgres["reports"]}
        if actual != set(TARGETS):
            raise qa.EvidenceError("Targeted refund coverage incomplete")
        summary_path = destination / "failsafe-reports/failsafe-summary.xml"
        summary = qa.xml_root(summary_path)
        if (summary.get("timeout") != "false"
                or int(summary.findtext("completed", "-1")) != postgres["tests"]
                or any(int(summary.findtext(key, "-1")) for key in ("errors", "failures", "skipped", "flakes"))
                or summary.findtext("failureMessage", "").strip()):
            raise qa.EvidenceError("Targeted Failsafe summary invalid")
        cases = {}
        for path in (destination / "failsafe-reports").glob("TEST-*.xml"):
            root = qa.xml_root(path)
            cases[root.get("name")] = [c.get("name") for c in root.findall("testcase")]
        result = {"unit": unit, "postgresql": postgres, "testcases": cases,
                  "summary_sha256": digest(summary_path), "failsafe_timeout": False,
                  "overlaps_full_run": True}
    result["status"] = "PASS"
    return result


def seal(private, public, metadata):
    certificate = os.environ["QA70_RECIPIENT_CERT"].encode("ascii")
    recipient = private.parent / "recipient.pem"
    recipient.write_bytes(certificate)
    archive = private.parent / "raw-artifacts.tar.gz"
    with tarfile.open(archive, "w:gz") as package:
        package.add(private, arcname="raw")
    encrypted = public / "raw-artifacts.cms.der"
    subprocess.run(["openssl", "cms", "-encrypt", "-binary", "-aes-256-cbc",
                    "-in", str(archive), "-out", str(encrypted), "-outform", "DER", str(recipient)],
                   check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    metadata["raw_archive"] = {"format": "OpenSSL CMS DER / AES-256-CBC / RSA3072 recipient",
        "encrypted_file": encrypted.name, "encrypted_sha256": digest(encrypted),
        "plaintext_archive_sha256": digest(archive),
        "public_certificate_sha256": hashlib.sha256(certificate).hexdigest(),
        "private_key_committed_or_uploaded": False,
        "retention_days": 30}


def run():
    expected = os.environ["QA70_EXPECTED_HEAD"]
    initial = identity(expected)
    public = ROOT / "target/qa70-public"
    state = {"schema_version": 1, "run_id": "pr70-" + now().replace(":", ""),
             "started_utc": now(), "source": initial, "runs": [], "status": "RUNNING",
             "execution_environment": "GitHub-hosted Linux runner with Docker; not Codex sandbox",
             "manual_ops_acceptance": "NOT RUN", "combined_seed_pr69": "NOT RUN",
             "q70_s01": "ACCEPTED RISK - LIMITED; unchanged; not RESOLVED"}
    private = Path(tempfile.mkdtemp(prefix="pr70-private-", dir=os.environ.get("RUNNER_TEMP")))
    try:
        for name, argv in (
                ("java-version", ["java", "-version"]),
                ("maven-version", ["sh", "./mvnw", "-v"]),
                ("docker-context", ["docker", "context", "show"]),
                ("docker-version", ["docker", "version", "--format", "{{json .}}"]),
                ("docker-info", ["docker", "info", "--format", "{{.ServerVersion}}"]),
                ("node-version", ["node", "--version"]),
                ("python-version", [sys.executable, "--version"])):
            record = execute(name, argv, private, expected, 90)
            state["runs"].append(record)
            if record["exit_code"] or record["timeout"]:
                raise qa.EvidenceError("Preflight failed")
        java = (private / "java-version.log").read_text()
        if not re.search(r'version "21[.]', java):
            raise qa.EvidenceError("Java21 required")
        docker = json.loads((private / "docker-version.log").read_text())
        state["versions"] = {"java": re.search(r'version "([^\"]+)', java)[1],
            "maven": re.search(r'Apache Maven ([0-9.]+)', (private / "maven-version.log").read_text())[1],
            "docker_client": docker["Client"]["Version"], "docker_server": docker["Server"]["Version"],
            "docker_context": (private / "docker-context.log").read_text().strip(),
            "node": (private / "node-version.log").read_text().strip(),
            "python": (private / "python-version.log").read_text().strip()}
        failures = []
        commands = (
            ("full-maven", ["sh", "./mvnw", "-B", "--no-transfer-progress", "-Ppostgres-it", "clean", "verify"], 1800),
            ("targeted-refund", ["sh", "./mvnw", "-B", "--no-transfer-progress", "-Ppostgres-it",
                                   "-Dit.test=" + ",".join(TARGETS), "clean", "verify"], 1200),
            ("node-all", ["node", "--test", "--test-reporter=tap", "src/test/js/notifications.test.cjs",
                          "src/test/js/buyer-cancellation.test.cjs"], 180),
            ("qa-tooling", [sys.executable, "-B", "-m", "unittest", "discover", "-s", "scripts/qa/tests", "-v"], 180),
        )
        for name, argv, timeout in commands:
            record = execute(name, argv, private, expected, timeout)
            state["runs"].append(record)
            successful = record["exit_code"] == 0 and not record["timeout"]
            if not successful:
                failures.append(name)
            if name in ("full-maven", "targeted-refund"):
                state[name] = snapshot(private, name, name == "full-maven", successful)
                log = (private / (name + ".log")).read_text(errors="replace")
                state[name]["postgresql_versions_observed"] = sorted(set(re.findall(r'\(PostgreSQL ([0-9.]+)\)', log)))
                state[name]["cancelled_paid_pg_blocking_observations"] = len(re.findall(r'QA02 cancelled-paid[^\n]*pg_blocking_pids=verified', log))
            elif name == "node-all" and successful:
                state["node"] = qa.node_counts(private / (name + ".log"))
            elif name == "qa-tooling" and successful:
                log = (private / (name + ".log")).read_text()
                count = re.search(r'Ran ([0-9]+) tests', log)
                if count is None or not log.rstrip().endswith("OK"):
                    raise qa.EvidenceError("Missing tooling summary")
                state["tooling"] = {"tests": int(count[1]), "status": "PASS"}
        state["status"] = "FAIL" if failures else "PASS"
        state["failed_stages"] = failures
    except Exception as error:
        state["status"] = "FAIL"
        state["failure_type"] = type(error).__name__  # Never publish exception details/private output.
    finally:
        state["finished_utc"] = now()
        try:
            state["source_after"] = identity(expected)
        except qa.EvidenceError:
            state["source_after"] = {"guard": "FAIL"}
            state["status"] = "FAIL"
        public.mkdir(parents=True, exist_ok=True)
        (private / "execution-state.json").write_text(json.dumps(state, indent=2) + "\n")
        seal(private, public, state)
        (public / "execution.json").write_text(json.dumps(state, indent=2) + "\n")
        sums = [digest(p) + "  " + p.name for p in sorted(public.iterdir()) if p.is_file()]
        (public / "SHA256SUMS").write_text("\n".join(sums) + "\n")
    # Connector-readable transport when direct artifact downloads are unavailable.
    # Only the same sanitized metadata and already-public ciphertext are emitted.
    # Raw plaintext, credentials and the recipient private key never enter CI logs.
    encoded = base64.b64encode(json.dumps(state, separators=(",", ":")).encode()).decode("ascii")
    print("QA70_EVIDENCE_B64=" + encoded, flush=True)
    encrypted = base64.b64encode((public / "raw-artifacts.cms.der").read_bytes()).decode("ascii")
    for index in range(0, len(encrypted), 60000):
        print("QA70_RAW_CMS_B64_%04d=" % (index // 60000) + encrypted[index:index + 60000], flush=True)
    print("PR70 " + state["status"] + "; sanitized evidence and encrypted raw archive retained", flush=True)
    return 0 if state["status"] == "PASS" else 1


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--execute", action="store_true")
    args = parser.parse_args()
    if not args.execute:
        print(json.dumps({"targeted_classes": TARGETS, "requires": ["clean exact source", "Java21", "Docker", "public recipient certificate"]}))
        raise SystemExit(0)
    raise SystemExit(run())
