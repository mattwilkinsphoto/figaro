"""Check complete, replayable 6.1 representation evidence, including refusals."""
import csv
import itertools
import math
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]


class Release61Evidence(unittest.TestCase):
    def setUp(self):
        self.runs = []
        for label in 'abc':
            with (ROOT / f'docs/release61-representation-{label}.csv').open(newline='') as stream:
                self.runs.append(list(csv.DictReader(stream)))

    def test_full_attempt_cartesian_product(self):
        expected = set(itertools.product(
            ('gvm-curved', 'gvm-seam', 'gvm-separated', 'gaussian-local', 'gaussian-wrapped'),
            map(str, range(5)), ('6', '13', '27'),
            ('gvm-em', 'gmm-chart-em', 'gmm-wrapped-em')))
        for rows in self.runs:
            keys = [(r['fixture'], r['seed'], r['parameterBudget'], r['method']) for r in rows]
            self.assertEqual(len(keys), 225)
            self.assertEqual(set(keys), expected)
            self.assertEqual(len(set(keys)), len(keys))

    def test_seeded_replay_except_wall_clock(self):
        def statistical(rows):
            return [{k: v for k, v in r.items() if not k.endswith('Millis')} for r in rows]
        self.assertEqual(statistical(self.runs[0]), statistical(self.runs[1]))
        self.assertEqual(statistical(self.runs[0]), statistical(self.runs[2]))

    def test_budgets_and_explicit_refusals(self):
        for rows in self.runs:
            refused = limited = 0
            for r in rows:
                self.assertEqual((r['train'], r['heldout']), ('800', '3000'))
                k = int(r['components'])
                self.assertEqual(int(r['parameters']), (7 if r['method'] == 'gvm-em' else 6)*k-1)
                self.assertLessEqual(int(r['parameters']), int(r['parameterBudget']))
                self.assertGreaterEqual(int(r['angularEvaluations']), 0)
                self.assertGreaterEqual(float(r['fitMillis']), 0)
                self.assertTrue(0 <= float(r['referenceRegion']) <= 1)
                if r['status'].startswith('refused-'):
                    refused += 1
                    for name in ('meanLogScore', 'scoreMCSE', 'regionProbability', 'scoreMillis', 'sampleMillis'):
                        self.assertEqual(r[name], '')
                else:
                    self.assertIn(r['status'], ('Converged', 'IterationLimit', 'AngularBudgetExhausted', 'Stalled', 'Fitted'))
                    limited += r['status'] == 'IterationLimit'
                    for name in ('meanLogScore', 'scoreMCSE', 'regionProbability', 'scoreMillis', 'sampleMillis'):
                        self.assertTrue(math.isfinite(float(r[name])))
                    self.assertGreaterEqual(float(r['scoreMCSE']), 0)
                    self.assertTrue(0 <= float(r['regionProbability']) <= 1)
                    self.assertGreaterEqual(float(r['scoreMillis']), 0)
                    self.assertGreaterEqual(float(r['sampleMillis']), 0)
            self.assertGreater(refused, 0)
            self.assertGreater(limited, 0)


if __name__ == '__main__':
    unittest.main()
