"""Fail-closed evidence controls; no machine-specific timing thresholds."""
import re
import unittest
from pathlib import Path
from summarize_gvm_scalar_performance import parse, summarize


class ScalarPerformanceTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.text = (Path(__file__).resolve().parents[1]/'docs/GVM_SCALAR_PERFORMANCE_RUNS.txt').read_text()

    def rejected(self, text):
        with self.assertRaises(ValueError):
            parse(text)

    def test_complete(self):
        runs = parse(self.text)
        self.assertEqual(sum(len(run['timings']) for run in runs), 336)
        self.assertEqual(len(runs[0]['methods']), 16)

    def test_missing_round(self):
        self.rejected(re.sub(r'^GVM_SCALAR_TIMING[^\n]*\n', '', self.text, count=1, flags=re.M))

    def test_duplicate_round(self):
        row = next(line for line in self.text.splitlines() if line.startswith('GVM_SCALAR_TIMING'))
        self.rejected(self.text.replace(row, row+'\n'+row, 1))

    def test_missing_completion(self):
        self.rejected(self.text.rsplit('GVM_SCALAR_COMPLETE', 1)[0])

    def test_duplicate_method(self):
        row = next(line for line in self.text.splitlines() if line.startswith('GVM_SCALAR_METHOD'))
        self.rejected(self.text.replace(row, row+'\n'+row, 1))

    def test_wrong_oracle_and_false_error(self):
        self.rejected(self.text.replace('oracle=0.5', 'oracle=0.6', 1))
        self.rejected(self.text.replace('distance=0.5', 'distance=0.6', 1))
        self.rejected(self.text.replace('error=0.0', 'error=1e-4', 1))

    def test_refusal_cannot_be_reported_as_success(self):
        self.rejected(self.text.replace('status=NumericallyUnresolved distance=none',
                                        'status=Resolved distance=0.0', 1))
        self.rejected(self.text.replace('refused=2', 'refused=0', 1))

    def test_wrong_work_units_and_budget(self):
        self.rejected(self.text.replace('unit=harmonics', 'unit=evaluations', 1))
        self.rejected(self.text.replace('work=0', 'work=50001', 1))
        self.rejected(self.text.replace('path=gaussian', 'path=positive-integration', 1))

    def test_invalid_timings_and_batch(self):
        for value in ('NaN', 'Infinity', '0', '-1'):
            self.rejected(re.sub(r'nsPerCall=\S+', 'nsPerCall='+value, self.text, count=1))
        self.rejected(re.sub(r'batch=\d+', 'batch=3', self.text, count=1))
        self.rejected(re.sub(r'round=\d+', 'round=31', self.text, count=1))

    def test_reused_jvm_and_unmeasured_run(self):
        pids = re.findall(r'pid=(\d+)', self.text)
        self.rejected(self.text.replace('pid='+pids[1], 'pid='+pids[0]))
        self.rejected(self.text.replace('timed=true', 'timed=false', 1))
        self.rejected(self.text.replace('tolerance=1.0E-8', 'tolerance=1.0E-6', 1))

    def test_duplicate_field_and_unknown_record(self):
        self.rejected(self.text.replace('rounds=7', 'rounds=7 rounds=3', 1))
        self.rejected(self.text.replace('GVM_SCALAR_TIMING', 'GVM_SCALAR_UNKNOWN', 1))

    def test_refusals_are_excluded_from_success_ratios(self):
        report = summarize(parse(self.text))
        self.assertEqual(report.count('no matched-success speed ratio'), 2)
        self.assertEqual(report.count('positive/fourier time ratio='), 6)
        self.assertEqual(report.count('REFUSAL-COST'), 2)


if __name__ == '__main__':
    unittest.main()
