import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import Mock, patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import rehearse_integrated_seed as seed


class RefundDiagnosticTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        (self.root / 'target/test-classes/qa02/seed').mkdir(parents=True)
        self.runner = seed.Seed.__new__(seed.Seed)
        self.runner.root = self.root
        self.runner.out = self.root / 'evidence'
        self.runner.out.mkdir()
        self.runner.java = Path('fixture-java')
        self.runner.jar = Path('fixture.jar')
        self.runner.flags = 0
        self.runner.password = 'PRIVATE_PASSWORD_SENTINEL'
        self.runner.app_env = {}
        self.runner.result = {}
        self.facts = [{'order_id': 13, 'paid_not_after_cancellation': False}]
        self.runner.sql = Mock(return_value=json.dumps(self.facts))

    def failure(self, args, **kwargs):
        kwargs['stdout'].write(b'PRIVATE_TOKEN_SENTINEL PRIVATE_PROOF_SENTINEL')
        return subprocess.CompletedProcess(args, 7)

    def test_failure_retains_exit_and_predicates_without_exporting_private_log(self):
        with patch.object(seed.subprocess, 'run', side_effect=self.failure):
            with self.assertRaisesRegex(seed.SeedError, '^QA02_FIXTURE_PROCESS_FAILED_1$'):
                self.runner.refund_fixture_process(1)
        diagnostic = self.runner.result['refund_fixture_failures'][0]
        self.assertEqual(diagnostic['application_exit_code'], 7)
        self.assertEqual(diagnostic['persisted_facts'], self.facts)
        self.assertEqual(diagnostic['capture_status'], 'CAPTURED')
        self.assertEqual(len(diagnostic['log_sha256']), 64)
        self.assertNotIn('PRIVATE_', json.dumps(self.runner.result))

    def test_database_capture_failure_cannot_mask_original_fixture_failure(self):
        self.runner.sql.side_effect = RuntimeError('PRIVATE_PASSWORD_SENTINEL')
        with patch.object(seed.subprocess, 'run', side_effect=self.failure):
            with self.assertRaisesRegex(seed.SeedError, '^QA02_FIXTURE_PROCESS_FAILED_2$'):
                self.runner.refund_fixture_process(2)
        diagnostic = self.runner.result['refund_fixture_failures'][0]
        self.assertEqual(diagnostic['capture_status'], 'UNAVAILABLE')
        self.assertEqual(diagnostic['capture_error_type'], 'RuntimeError')
        self.assertEqual(diagnostic['application_exit_code'], 7)
        self.assertNotIn('PRIVATE_', json.dumps(self.runner.result))

    def test_timeout_is_recorded_and_reraised_instead_of_becoming_a_pass(self):
        timeout = subprocess.TimeoutExpired(['fixture-java'], 180)
        with patch.object(seed.subprocess, 'run', side_effect=timeout):
            with self.assertRaises(subprocess.TimeoutExpired) as error:
                self.runner.refund_fixture_process(1)
        self.assertIs(error.exception, timeout)
        diagnostic = self.runner.result['refund_fixture_failures'][0]
        self.assertTrue(diagnostic['timed_out'])
        self.assertIsNone(diagnostic['application_exit_code'])
        self.assertNotIn('PASS', json.dumps(self.runner.result))

    def clock_fixture(self):
        self.runner.refund_clock_skew_seconds = -2
        target = self.root / 'target/test-classes/qa02/investigation/ClockSkewConfiguration.class'
        target.parent.mkdir(parents=True)
        target.write_bytes(b'fixture-bytecode')

    def successful_child(self, marker):
        def run(args, **kwargs):
            self.assertIn('--spring.main.sources=qa02.investigation.ClockSkewConfiguration', args)
            if marker:
                kwargs['stdout'].write(b'Q70_CONTROLLED_CLOCK_OFFSET_SECONDS=-2\n')
            kwargs['stdout'].write(b'QA02_REFUND_SEED_RESULT={"negativeChecks":{"fixture":"PASS"}}\n')
            return subprocess.CompletedProcess(args, 0)
        return run

    def test_controlled_clock_cannot_claim_pass_without_activation_marker(self):
        self.clock_fixture()
        with patch.object(seed.subprocess, 'run', side_effect=self.successful_child(False)):
            with self.assertRaisesRegex(seed.SeedError, '^CONTROLLED_CLOCK_MARKER_MISSING$'):
                self.runner.refund_fixture_process(1)

    def test_controlled_clock_is_child_scoped_and_activation_is_recorded(self):
        self.clock_fixture()
        with patch.object(seed.subprocess, 'run', side_effect=self.successful_child(True)):
            result = self.runner.refund_fixture_process(1)
        self.assertTrue(result['persisted_diagnostics']['controlled_clock_marker_confirmed'])
        self.assertEqual(result['persisted_diagnostics']['clock_skew_seconds'], -2)
        self.assertNotIn('SPRING_MAIN_SOURCES', self.runner.app_env)
        self.assertTrue((self.runner.out / 'qa02-test-classes/qa02/investigation/ClockSkewConfiguration.class').is_file())

    def test_clock_mode_without_refund_fixtures_fails_before_resource_creation(self):
        with patch.object(sys, 'argv', ['seed', '--disposable-demo', '--refund-clock-skew-seconds=-2']):
            with patch.object(seed, 'Seed') as constructor:
                with self.assertRaises(SystemExit) as error:
                    seed.main()
        self.assertEqual(error.exception.code, 2)
        constructor.assert_not_called()


if __name__ == '__main__':
    unittest.main()
