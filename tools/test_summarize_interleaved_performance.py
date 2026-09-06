import copy
import io
from contextlib import redirect_stdout
import unittest
import summarize_interleaved_performance as audit
import test_summarize_vector_performance as fixtures


class InterleavedPerformanceTest(unittest.TestCase):
    def rows(self):
        base = fixtures.VectorPerformanceSummaryTest().rows()
        return [dict(row, **dict(zip(audit.PREFIX, map(str, (i, p, pos, variant,
            ('a' if variant == 'baseline' else 'b')*40, ('c' if variant == 'baseline' else 'd')*64)))))
            for i, (p, pos, variant) in enumerate(audit.schedule(4)) for row in base]

    def load(self, rows):
        return audit.load(audit.encode(rows, audit.FIELDS), 4, 1, 100, 20)

    def test_balanced_complete_grid(self):
        self.assertEqual([v for _, _, v in audit.schedule(4)], ['baseline','current','current','baseline']*2)
        self.assertEqual(len(self.load(self.rows())), 8)
        for n in (0, 1, 3, -2):
            with self.assertRaises(ValueError): audit.schedule(n)

    def test_missing_duplicate_identity_order_and_tampering(self):
        rows = self.rows()
        invalid = [rows[1:], rows + [rows[0]]]
        for field, value in [('invocation','8'), ('pair','1'), ('position','1'), ('variant','current'),
                             ('revision','b'*40), ('runtimeHash','e'*64), ('runtimeHash','path/to/file'),
                             ('fingerprint','f'*64), ('evaluations','10001')]:
            changed = copy.deepcopy(rows); changed[0][field] = value; invalid.append(changed)
        for changed in invalid:
            with self.assertRaises(ValueError): self.load(changed)

    def test_pair_summary(self):
        rows = self.rows()
        for row in rows:
            if row['variant'] == 'current':
                row.update(wallSeconds='0.5', constructionSeconds='0.05', samplingSeconds='0.25', diagnosticsSeconds='0.2')
        output = io.StringIO()
        with redirect_stdout(output): audit.summary(self.load(rows), 4, 1)
        self.assertIn('2.000 | 2.000-2.000 | 4/4 | 2.000', output.getvalue())

    def test_reference_work_mismatch(self):
        rows = self.rows()
        fixture = fixtures.VectorPerformanceSummaryTest()
        baseline = audit.vector.load([fixture.encode(fixture.rows())], 1, 100, 20)
        baseline[next(iter(baseline))]['fingerprint'] = 'f'*64
        with self.assertRaises(ValueError): audit.load(audit.encode(rows, audit.FIELDS), 4, 1, 100, 20, baseline)


if __name__ == '__main__': unittest.main()
