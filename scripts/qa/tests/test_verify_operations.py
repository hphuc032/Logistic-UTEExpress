import contextlib
import io
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch
import xml.etree.ElementTree as ET

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import verify_operations as qa

REV = "a" * 40
TAP = "# tests 12\n# pass 12\n# fail 0\n# cancelled 0\n# skipped 0\n# todo 0\n"


class EvidenceTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.client = self.root / "src/test/js/notifications.test.cjs"
        self.client.parent.mkdir(parents=True)
        self.client.write_text("test fixture", encoding="utf-8")
        self.fixture()
        self.preflight_patch = patch.object(qa,"preflight")
        self.original_preflight = qa.preflight
        self.preflight_patch.start()
        self.addCleanup(self.preflight_patch.stop)
        self.runner_patch = patch.object(qa,"runner_identity",return_value="b"*40)
        self.runner_patch.start()
        self.addCleanup(self.runner_patch.stop)

    def suite(self, folder, name):
        path = self.root / "target" / folder / ("TEST-" + name + ".xml")
        path.parent.mkdir(parents=True, exist_ok=True)
        element = ET.Element("testsuite", name=name, tests="1", failures="0", errors="0", skipped="0")
        ET.SubElement(element, "testcase", name="fixture")
        ET.SubElement(element, "system-out").text = "private-token-secret-sentinel"
        ET.ElementTree(element).write(path, encoding="utf-8")
        return path

    def fixture(self):
        self.unit = self.suite("surefire-reports", "com.uteexpress.ArchitectureTest")
        for name in qa.REQUIRED_IT:
            self.suite("failsafe-reports", name)
        self.summary = self.root / "target/failsafe-reports/failsafe-summary.xml"
        self.summary.write_text('<failsafe-summary timeout="false"><completed>' + str(len(qa.REQUIRED_IT)) +
            '</completed><errors>0</errors><failures>0</failures><skipped>0</skipped><flakes>0</flakes></failsafe-summary>', encoding="utf-8")

    def test_counts_and_sanitization(self):
        report = qa.collect(self.root, REV)
        self.assertEqual(report["java_total"], len(qa.REQUIRED_IT) + 1)
        self.assertNotIn("private-token-secret-sentinel", json.dumps(report))
        self.assertEqual(len(report["unit"]["reports"][0]["sha256"]), 64)

    def test_missing_required_coverage_is_rejected(self):
        next((self.root / "target/failsafe-reports").glob("TEST-*.xml")).unlink()
        with self.assertRaises(qa.EvidenceError): qa.collect(self.root, REV)

    def test_empty_reports_are_rejected(self):
        with self.assertRaises(qa.EvidenceError): qa.collect_group(self.root / "missing")

    def test_skips_errors_flakes_and_inconsistent_counts_are_rejected(self):
        baseline = self.unit.read_bytes()
        for kind in ("failures", "errors", "skipped", "negative", "mismatch", "flakyFailure", "rerunError", "missing-count"):
            with self.subTest(kind=kind):
                element = ET.fromstring(baseline)
                if kind in ("failures", "errors", "skipped"): element.set(kind,"1")
                elif kind == "negative": element.set("tests","-1")
                elif kind == "mismatch": element.set("tests","2")
                elif kind == "missing-count": del element.attrib["tests"]
                else: ET.SubElement(element.find("testcase"),kind)
                ET.ElementTree(element).write(self.unit)
                with self.assertRaises(qa.EvidenceError): qa.collect(self.root, REV)
        self.unit.write_bytes(baseline)

    def test_duplicate_suite_is_rejected(self):
        (self.unit.parent / "TEST-duplicate.xml").write_bytes(self.unit.read_bytes())
        with self.assertRaises(qa.EvidenceError): qa.collect(self.root, REV)

    def test_timeout_incomplete_or_failure_summary_is_rejected(self):
        baseline = self.summary.read_bytes()
        for kind in ("timeout", "completed", "flakes", "failureMessage"):
            with self.subTest(kind=kind):
                element = ET.fromstring(baseline)
                if kind == "timeout": element.set("timeout","true")
                elif kind == "failureMessage": ET.SubElement(element,kind).text="failure"
                else: element.find(kind).text="1"
                ET.ElementTree(element).write(self.summary)
                with self.assertRaises(qa.EvidenceError): qa.collect(self.root, REV)
        self.summary.write_bytes(baseline)

    def test_malformed_xml_and_entities_are_rejected(self):
        for text in ("<bad", '<!DOCTYPE suite [<!ENTITY secret "private">]><testsuite/>'):
            self.unit.write_text(text,encoding="utf-8")
            with self.assertRaises(qa.EvidenceError): qa.collect(self.root, REV)

    def test_node_empty_skipped_or_missing_summary_rejected(self):
        path = self.root / "node.log"
        for text in ("", TAP.replace("# tests 12", "# tests 0"), TAP.replace("# skipped 0", "# skipped 1"), TAP + "# tests 12\n"):
            path.write_text(text,encoding="utf-8")
            with self.assertRaises(qa.EvidenceError): qa.node_counts(path)

    def fake_execute(self, args, root, log):
        log.write_text(TAP if args[0] == "node" else "local-only-private-log",encoding="utf-8")

    def test_run_orders_clean_verify_before_node_and_publishes_only_on_success(self):
        calls=[]
        def execute(*args):
            calls.append(args[0]); self.fake_execute(*args)
        with patch.object(qa,"git_identity",return_value=REV), patch.object(qa,"execute",side_effect=execute), contextlib.redirect_stdout(io.StringIO()):
            result=qa.run(self.root)
        self.assertEqual(calls[0][-4:],["-q","-Ppostgres-it","clean","verify"])
        self.assertEqual(calls[1][0],"node")
        self.assertEqual(result["node"]["tests"],12)
        self.assertEqual(result["tested_source_sha"],REV)
        self.assertEqual(result["tested_revision"],REV)
        self.assertEqual(result["runner_sha"],"b"*40)
        self.assertEqual(result["manifest_schema_version"],2)
        self.assertEqual(len(result["runner_script_sha256"]),64)
        self.assertEqual(result["log_sha256"]["node"],qa.hashlib.sha256((self.root/"target/qa-03/node.log").read_bytes()).hexdigest())
        self.assertTrue((self.root/"target/qa-03/operations-evidence.json").is_file())
        self.assertTrue((self.root/"target/qa-03/maven.log").is_file())

    def test_failed_new_run_removes_stale_pass_and_does_not_execute_node(self):
        output=self.root/"target/qa-03"; output.mkdir()
        (output/"operations-evidence.json").write_text("stale PASS")
        def fail(args, root, log):
            log.write_text("failure")
            raise qa.EvidenceError("failed")
        with patch.object(qa,"git_identity",return_value=REV), patch.object(qa,"execute",side_effect=fail) as execute, contextlib.redirect_stdout(io.StringIO()):
            with self.assertRaises(qa.EvidenceError): qa.run(self.root)
        self.assertEqual(execute.call_count,1)
        self.assertFalse((output/"operations-evidence.json").exists())
        self.assertEqual((output/"maven.log").read_text(),"failure")

    def test_revision_change_prevents_publication(self):
        with patch.object(qa,"git_identity",side_effect=[REV,"b"*40]), patch.object(qa,"execute",side_effect=self.fake_execute), contextlib.redirect_stdout(io.StringIO()):
            with self.assertRaises(qa.EvidenceError): qa.run(self.root)
        self.assertFalse((self.root/"target/qa-03/operations-evidence.json").exists())

    def test_dirty_checkout_prevents_commands_and_removes_stale_pass(self):
        output=self.root/"target/qa-03"; output.mkdir()
        (output/"operations-evidence.json").write_text("stale")
        with patch.object(qa,"git_identity",side_effect=qa.EvidenceError("dirty")), patch.object(qa,"execute") as execute:
            with self.assertRaises(qa.EvidenceError): qa.run(self.root)
        execute.assert_not_called()
        self.assertFalse((output/"operations-evidence.json").exists())

    def test_java_17_preflight_fails_before_other_commands(self):
        import subprocess
        with patch.object(qa.subprocess,"run",return_value=subprocess.CompletedProcess([],0,'','openjdk version "17.0.1"')) as command:
            with self.assertRaises(qa.EvidenceError): self.original_preflight()
        self.assertEqual(command.call_count,1)

    def test_java_21_node_and_docker_preflight(self):
        import subprocess
        values=[subprocess.CompletedProcess([],0,'','openjdk version "21.0.2"'),
                subprocess.CompletedProcess([],0,'v22.0.0\n',''),subprocess.CompletedProcess([],0,'27.0.0\n','')]
        with patch.object(qa.subprocess,"run",side_effect=values) as command:
            self.original_preflight()
        self.assertEqual(command.call_count,3)

    def test_runner_revision_change_prevents_publication(self):
        with patch.object(qa,"git_identity",return_value=REV), patch.object(qa,"runner_identity",side_effect=["b"*40,"c"*40]), patch.object(qa,"execute",side_effect=self.fake_execute), contextlib.redirect_stdout(io.StringIO()):
            with self.assertRaises(qa.EvidenceError): qa.run(self.root)
        self.assertFalse((self.root/"target/qa-03/operations-evidence.json").exists())

    def test_runner_file_change_prevents_publication(self):
        actual_hash=qa.sha256_file
        reads=0
        def digest(path):
            nonlocal reads
            if path == Path(qa.__file__).resolve():
                reads+=1
                return "a"*64 if reads==1 else "b"*64
            return actual_hash(path)
        with patch.object(qa,"git_identity",return_value=REV), patch.object(qa,"sha256_file",side_effect=digest), patch.object(qa,"execute",side_effect=self.fake_execute), contextlib.redirect_stdout(io.StringIO()):
            with self.assertRaises(qa.EvidenceError): qa.run(self.root)
        self.assertFalse((self.root/"target/qa-03/operations-evidence.json").exists())

    def test_cli_failure_never_echoes_private_exception(self):
        stderr=io.StringIO()
        with patch.object(sys,"argv",["verify_operations.py"]), patch.object(qa,"run",side_effect=qa.EvidenceError("private-token-secret-sentinel")), contextlib.redirect_stderr(stderr):
            self.assertEqual(qa.main(),1)
        self.assertNotIn("private-token-secret-sentinel",stderr.getvalue())


if __name__ == "__main__": unittest.main()
