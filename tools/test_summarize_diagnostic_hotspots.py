import copy
import io
from contextlib import redirect_stdout
import unittest
import summarize_diagnostic_hotspots as study


class DiagnosticHotspotsTest(unittest.TestCase):
    def rows(self):
        return [dict(zip(study.FIELDS, map(str, (j, 'row', shape, n, r, stage, max(1,64000//n),
                0.5 if stage.startswith('radix') else 1, 1024, ('a' if 'Sort' in stage else 'b' if stage!='summary' else 'c')*64))))
                for j in range(3) for n in study.SIZES for shape in study.SHAPES for r in range(-5,1) for stage in study.STAGES]

    def load(self, rows): return study.load(study.encode(rows, study.FIELDS), rounds=1)

    def test_complete_grid_and_summary(self):
        rows = self.load(self.rows())
        self.assertEqual(len(rows), 1620)
        output = io.StringIO()
        with redirect_stdout(output): study.summarize(rows, 3, 1)
        self.assertIn('2.000 [2.000-2.000]', output.getvalue())

    def test_missing_duplicate_and_changed_work_or_output(self):
        base = self.rows()
        invalid = [base[1:], base + [base[0]]]
        for field,value in [('jvm','3'), ('shape','unexpected'), ('values','2048'), ('round','1'),
                            ('stage','other'), ('iterations','1'), ('fingerprint','d'*64),
                            ('fingerprint','C:/private'), ('seconds','NaN'), ('seconds','0'), ('allocatedBytes','-1')]:
            rows = copy.deepcopy(base); rows[0][field] = value; invalid.append(rows)
        for rows in invalid:
            with self.assertRaises(ValueError): self.load(rows)

    def test_optional_allocation_counter(self):
        rows = self.rows()
        for row in rows: row['allocatedBytes'] = 'NaN'
        output = io.StringIO()
        with redirect_stdout(output): study.summarize(self.load(rows), 3, 1)
        self.assertIn('N/A -> N/A', output.getvalue())

    def test_wrong_schema_and_study_size(self):
        with self.assertRaises(ValueError): study.load('unexpected\n')
        for kwargs in ({'jvms':0}, {'rounds':0}, {'work':10}):
            with self.assertRaises(ValueError): study.load('', **kwargs)


if __name__ == '__main__': unittest.main()
