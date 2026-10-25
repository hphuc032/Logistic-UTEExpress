"""Local QA-03 rehearsal runner. No approval/deployment or external attestation."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET
from datetime import datetime, timezone


class EvidenceError(Exception):
    pass


REQUIRED_IT = {
    "com.uteexpress.DatabaseBaselineIT",
    "com.uteexpress.shipping.ShipmentAssignmentIT",
    "com.uteexpress.shipping.ShipmentAttemptMigrationIT",
    "com.uteexpress.fulfillment.ShipperFulfillmentIT",
    "com.uteexpress.notification.NotificationSocketIT",
    "com.uteexpress.notification.NotificationRecoveryIT",
    "com.uteexpress.engagement.ReviewIntegrationIT",
    "com.uteexpress.engagement.ReviewFoundationMigrationIT",
}


def git_identity(root):
    def git(*args):
        return subprocess.check_output(["git", *args], cwd=root, encoding="utf-8", stderr=subprocess.DEVNULL).strip()
    if Path(git("rev-parse", "--show-toplevel")).resolve() != root.resolve():
        raise EvidenceError("Checkout must be a Git workspace root")
    revision = git("rev-parse", "HEAD")
    if not re.fullmatch(r"[a-f0-9]{40,64}", revision):
        raise EvidenceError("Invalid Git revision")
    if git("status", "--porcelain", "--untracked-files=normal"):
        raise EvidenceError("Commit or preserve local changes before running QA; working tree must be clean")
    return revision


def xml_root(path):
    raw = path.read_bytes()
    if len(raw) > 32 * 1024 * 1024 or b"<!DOCTYPE" in raw.upper() or b"<!ENTITY" in raw.upper():
        raise EvidenceError("Unsafe or oversized XML report")
    try:
        return ET.fromstring(raw)
    except ET.ParseError:
        raise EvidenceError("Malformed XML report") from None


def collect_group(folder):
    reports, names = [], set()
    for path in sorted(folder.glob("TEST-*.xml")):
        root = xml_root(path)
        name = root.get("name", "")
        if root.tag != "testsuite" or not re.fullmatch(r"com[.]uteexpress[.][A-Za-z0-9_.]+", name) or name in names:
            raise EvidenceError("Invalid or duplicate suite identity")
        names.add(name)
        try:
            counts = {key: int(root.attrib[key]) for key in ("tests", "failures", "errors", "skipped")}
        except (ValueError, KeyError):
            raise EvidenceError("Missing or invalid suite counts") from None
        cases = root.findall("testcase")
        if counts["tests"] <= 0 or len(cases) != counts["tests"] or any(value < 0 for value in counts.values()):
            raise EvidenceError("Empty or inconsistent test suite")
        if any(counts[key] for key in ("failures", "errors", "skipped")):
            raise EvidenceError("Suite has failures, errors or skips")
        if any(next(root.iter(tag), None) is not None for tag in ("failure", "error", "skipped", "flakyFailure", "flakyError", "rerunFailure", "rerunError")):
            raise EvidenceError("Suite contains failed/skipped/flaky/rerun cases")
        reports.append({"suite": name, **counts, "sha256": hashlib.sha256(path.read_bytes()).hexdigest()})
    if not reports:
        raise EvidenceError("Missing test reports")
    return {"classes": len(reports), "tests": sum(row["tests"] for row in reports), "reports": reports}


def collect(root, revision):
    unit = collect_group(root / "target/surefire-reports")
    integration = collect_group(root / "target/failsafe-reports")
    if "com.uteexpress.ArchitectureTest" not in {row["suite"] for row in unit["reports"]}:
        raise EvidenceError("Architecture checks missing")
    if not REQUIRED_IT <= {row["suite"] for row in integration["reports"]}:
        raise EvidenceError("Required Ops/migration integration coverage missing")
    path = root / "target/failsafe-reports/failsafe-summary.xml"
    if not path.is_file():
        raise EvidenceError("Failsafe summary missing")
    summary = xml_root(path)
    try:
        valid = (summary.tag == "failsafe-summary" and summary.get("timeout") == "false"
                 and int(summary.findtext("completed", "-1")) == integration["tests"]
                 and all(int(summary.findtext(key, "-1")) == 0 for key in ("failures", "errors", "skipped", "flakes")))
    except ValueError:
        valid = False
    if not valid or summary.findtext("failureMessage", "").strip():
        raise EvidenceError("Failsafe incomplete, timed out or unsuccessful")
    return {"tested_revision": revision, "created_at_utc": datetime.now(timezone.utc).isoformat(),
            "unit": unit, "postgresql": integration, "java_total": unit["tests"] + integration["tests"],
            "maven_exit_code": 0, "node_exit_code": 0, "failsafe_timeout": False,
            "summary_sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
            "scope": "Local full-suite rehearsal; not final acceptance, CI approval or runner attestation"}


def execute(args, root, log):
    with log.open("wb") as output:
        result = subprocess.run(args, cwd=root, stdout=output, stderr=subprocess.STDOUT, check=False, timeout=120 if args[0] == "node" else 1800)
    if result.returncode:
        raise EvidenceError("Command failed; inspect the local raw log before retrying")


def commands(root):
    wrapper = root / ("mvnw.cmd" if os.name == "nt" else "mvnw")
    maven = [str(wrapper)] if os.name == "nt" else ["sh", str(wrapper)]
    return [maven + ["-q", "-Ppostgres-it", "clean", "verify"],
            ["node", "--test", "--test-reporter=tap", "src/test/js/notifications.test.cjs"]]


def node_counts(path):
    text = path.read_text(encoding="utf-8", errors="replace")
    counts = {}
    for key in ("tests", "pass", "fail", "cancelled", "skipped", "todo"):
        values = re.findall(r"^# " + key + r" ([0-9]+)$", text, re.MULTILINE)
        if len(values) != 1:
            raise EvidenceError("Missing or ambiguous Node summary")
        counts[key] = int(values[0])
    if counts["tests"] <= 0 or counts["pass"] != counts["tests"] or any(counts[k] for k in ("fail", "cancelled", "skipped", "todo")):
        raise EvidenceError("Node tests empty or unsuccessful")
    return counts


def evidence_dir(root):
    output = (root / "target/qa-03").resolve()
    if not output.is_relative_to(root.resolve()):
        raise EvidenceError("Evidence path escapes checkout")
    return output


def preflight():
    if sys.version_info < (3, 11):
        raise EvidenceError("Python 3.11 or newer required")
    java = str(Path(os.environ["JAVA_HOME"]) / "bin" / ("java.exe" if os.name == "nt" else "java")) if os.environ.get("JAVA_HOME") else "java"
    result = subprocess.run([java, "-version"], capture_output=True, text=True, check=False, timeout=15)
    version = re.search(r'version "([0-9]+)', result.stdout + result.stderr)
    if result.returncode or version is None or version[1] != "21":
        raise EvidenceError("Java 21 required")
    result = subprocess.run(["node", "--version"], capture_output=True, text=True, check=False, timeout=15)
    version = re.fullmatch(r"v([0-9]+)[.][0-9]+[.][0-9]+", result.stdout.strip())
    if result.returncode or version is None or int(version[1]) < 18:
        raise EvidenceError("Supported Node runtime required")
    result = subprocess.run(["docker", "info", "--format", "{{.ServerVersion}}"], capture_output=True, text=True, check=False, timeout=15)
    if result.returncode or not result.stdout.strip():
        raise EvidenceError("Docker engine unavailable")


def run(root):
    output = evidence_dir(root)
    destination = output / "operations-evidence.json"
    destination.unlink(missing_ok=True)  # stale PASS must not survive a failed new invocation
    revision = git_identity(root)
    if not (root / "src/test/js/notifications.test.cjs").is_file():
        raise EvidenceError("WS-02 client tests missing; use an approved integration/merged checkout")
    preflight()
    # Logs live outside target during Maven clean; publication happens only after all checks pass.
    with tempfile.TemporaryDirectory(prefix="uteexpress-qa-") as temporary:
        logs = Path(temporary)
        try:
            for index, command in enumerate(commands(root)):
                print("Running " + ("full PostgreSQL clean verify" if index == 0 else "Node client tests"), flush=True)
                execute(command, root, logs / ("maven.log" if index == 0 else "node.log"))
            if git_identity(root) != revision:
                raise EvidenceError("Source revision changed during verification")
            client = node_counts(logs / "node.log")
            result = collect(root, revision)
            result["node"] = client
            if git_identity(root) != revision:
                raise EvidenceError("Source changed while collecting reports")
            # No environment properties, raw testcase outputs or exception details enter the JSON.
            output.mkdir(parents=True, exist_ok=True)
            staging = destination.with_suffix(".tmp")
            staging.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
            staging.replace(destination)
        finally:
            output.mkdir(parents=True, exist_ok=True)
            for log in logs.glob("*.log"):
                shutil.copyfile(log, output / log.name)
    print("PASS: Java/PostgreSQL tests=" + str(result["java_total"]) + "; Node exit=0; revision=" + revision)
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--plan", action="store_true", help="Print fixed commands without executing tests")
    parser.add_argument("--checkout", type=Path, default=Path(__file__).resolve().parents[2], help="Clean checkout to verify; source HEAD is never modified")
    args = parser.parse_args()
    root = args.checkout.resolve()
    try:
        if args.plan:
            print(json.dumps({"commands": commands(root), "required_integration_suites": sorted(REQUIRED_IT),
                              "prerequisites": ["clean committed checkout", "Java 21", "Node", "Python 3.11+", "Docker engine"]}, indent=2))
        else:
            run(root)
    except (EvidenceError, OSError, subprocess.SubprocessError):
        # No exception text: subprocess/XML errors may contain private output or paths.
        print("QA verification failed; check prerequisites and local target/qa-03 logs. No PASS evidence issued.", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
