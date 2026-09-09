import unittest
import csv
import math
from pathlib import Path
import numpy as np
from inference_health_reference import fixtures

class InferenceHealthReferenceTest(unittest.TestCase):
    def test_pinned_arviz_fixtures(self):
        rows = list(fixtures())
        expected = [-.18098173657398042, .04591366679702962, .31712924231012535,
                    .6786439979721525, 1.1305491440927482]
        np.testing.assert_allclose([r[1] for r in rows], expected, rtol=0, atol=2e-12)
        self.assertTrue(all(r[2] > 0 and r[3] == 135 for r in rows))

    def test_complete_fixed_workload_evidence(self):
        path = Path(__file__).resolve().parents[1] / 'docs' / 'inference-health-results.csv'
        with path.open(newline='', encoding='utf-8') as stream:
            rows = list(csv.DictReader(stream))
        expected = {(family, str(1009 + i * 7919))
                    for family in ('gamma', 'dirichlet') for i in range(30)}
        self.assertEqual(len(rows), 60)
        self.assertEqual({(r['family'], r['seed']) for r in rows}, expected)
        for row in rows:
            self.assertEqual(row['draws'], '2000')
            self.assertEqual(row['status'], 'Danger')
            for key in ('ess', 'maxWeight', 'k', 'mean', 'error', 'rawMcse'):
                self.assertTrue(math.isfinite(float(row[key])))
            self.assertTrue(1 <= float(row['ess']) < 100)
            self.assertTrue(0 < float(row['maxWeight']) <= 1)
            self.assertGreaterEqual(float(row['k']), .7)
            self.assertGreaterEqual(float(row['rawMcse']), 0)
            self.assertIn('ParetoDanger', row['issues'].split('|'))

if __name__ == '__main__':
    unittest.main()
