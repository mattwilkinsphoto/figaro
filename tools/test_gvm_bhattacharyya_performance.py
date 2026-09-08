"""Fail-closed checks for the checked-in timing evidence, without timing assertions."""
import unittest
from pathlib import Path
from summarize_gvm_bhattacharyya_performance import parse


class PerformanceReportTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.text = (Path(__file__).resolve().parents[1]/'docs/GVM_BHATTACHARYYA_PERFORMANCE_RUNS.txt').read_text()

    def test_complete_evidence(self):
        runs = parse(self.text)
        self.assertEqual(sum(len(r['timings']) for r in runs),378)

    def test_truncation(self):
        with self.assertRaises(ValueError): parse(self.text.rsplit('GVM_COMPLETE',1)[0])

    def test_missing_timing(self):
        lines = self.text.splitlines()
        index = next(i for i,v in enumerate(lines) if v.startswith('GVM_TIMING'))
        del lines[index]
        with self.assertRaises(ValueError): parse('\n'.join(lines))

    def test_duplicate_timing(self):
        row = next(v for v in self.text.splitlines() if v.startswith('GVM_TIMING'))
        with self.assertRaises(ValueError): parse(self.text.replace(row,row+'\n'+row,1))

    def test_nonfinite_timing(self):
        import re
        with self.assertRaises(ValueError): parse(re.sub(r'nsPerCall=\S+','nsPerCall=NaN',self.text,count=1))

    def test_mismatched_accuracy(self):
        with self.assertRaises(ValueError): parse(self.text.replace('accepted=false','accepted=true',1))

    def test_reused_jvm(self):
        import re
        pids = re.findall(r'pid=(\d+)',self.text)
        with self.assertRaises(ValueError): parse(self.text.replace('pid='+pids[1],'pid='+pids[0]))

    def test_unmeasured_run(self):
        with self.assertRaises(ValueError): parse(self.text.replace('timed=true','timed=false',1))


if __name__ == '__main__': unittest.main()
