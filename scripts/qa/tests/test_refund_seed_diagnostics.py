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


if __name__ == '__main__':
    unittest.main()
