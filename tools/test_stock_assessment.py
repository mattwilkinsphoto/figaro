"""In-memory audit controls; no downloads, builds, keys or filesystem mutation."""
import json
from pathlib import Path
import sys
import unittest
sys.path.insert(0,str(Path(__file__).resolve().parent/'sbt-worker-repair'))
from stock_assessment import matrix_rows

class MatrixAuditTests(unittest.TestCase):
    def fixture(self):
        jar=Path(__file__).resolve().with_suffix('.jar')
        modes=['response-first','exit-first','interleaved-success','error-first','interleaved-error','nonzero-first','duplicate-exit','wrong-id']
        rows=[dict(mode=m,trials=20,duplicateExceptions=20 if m.startswith('interleaved') else 0) for m in modes]
        return jar,rows
    def text(self,jar,rows):return 'BINARY '+jar.as_uri()+'\n'+'\n'.join(map(json.dumps,rows))
    def test_original_stock_findings_are_retained(self):
        jar,rows=self.fixture();self.assertEqual(matrix_rows(self.text(jar,rows),jar,False),rows)
    def test_missing_ordering_rejected(self):
        jar,rows=self.fixture()
        with self.assertRaises(ValueError):matrix_rows(self.text(jar,rows[:-1]),jar,False)
    def test_wrong_binary_rejected(self):
        jar,rows=self.fixture()
        with self.assertRaises(ValueError):matrix_rows(self.text(jar,rows),jar.with_name('other.jar'),False)
    def test_removed_exception_requires_review(self):
        jar,rows=self.fixture();rows[2]['duplicateExceptions']=0
        with self.assertRaises(ValueError):matrix_rows(self.text(jar,rows),jar,False)
    def test_changed_trial_count_rejected(self):
        jar,rows=self.fixture();rows[0]['trials']=19
        with self.assertRaises(ValueError):matrix_rows(self.text(jar,rows),jar,False)

if __name__=='__main__':unittest.main()
