"""Complete-record checks, not a selected-winner performance gate."""
import csv
import math
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]


class ModelingEvidence(unittest.TestCase):
    def rows(self, suffix):
        with (ROOT / 'docs' / f'gvm-mixture-representation-{suffix}.csv').open(newline='') as stream:
            return list(csv.DictReader(stream))

    def test_complete_predeclared_design(self):
        expected = {(f, str(s), str(k), m) for f in ('local', 'curved', 'seam', 'separated')
                    for s in range(103000, 103005) for k in (1, 2, 4)
                    for m in ('gmm-chart-moments', 'gvm-regression')}
        for suffix in 'abc':
            rows = self.rows(suffix)
            self.assertEqual(len(rows), 120)
            self.assertEqual({tuple(r[x] for x in ('fixture', 'seed', 'components', 'method')) for r in rows}, expected)
            for r in rows:
                self.assertIn(r['status'], ('completed', 'refused'))
                self.assertEqual((r['train'], r['heldout'], r['draws']), ('1200', '4000', '4000'))
                self.assertEqual(int(r['parameters']), (7 if r['method']=='gvm-regression' else 6)*int(r['components'])-1)
                if r['status']=='completed':
                    self.assertTrue(math.isfinite(float(r['meanLogScore'])))
                    for key in ('regionProbability', 'referenceRegion'):
                        self.assertTrue(0<=float(r[key])<=1)
                    for key in ('fitMillis', 'scoreMillis', 'drawMillis'):
                        self.assertGreaterEqual(float(r[key]), 0)

    def test_fresh_jvm_statistical_replay(self):
        def statistical(suffix):
            return [{k:v for k,v in row.items() if not k.endswith('Millis')} for row in self.rows(suffix)]
        self.assertEqual(statistical('a'), statistical('b'))
        self.assertEqual(statistical('a'), statistical('c'))

    def test_shared_heldout_reference_and_chart(self):
        grouped = {}
        for r in self.rows('a'):
            key = r['fixture'], r['seed']
            value = r['chartCenter'], r['referenceRegion']
            self.assertEqual(grouped.setdefault(key, value), value)


if __name__ == '__main__':
    unittest.main()
