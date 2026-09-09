import unittest
from pathlib import Path
from summarize_joint_proposals import load,validate,summaries

class JointEvidenceTest(unittest.TestCase):
    def setUp(self): self.rows=load((Path(__file__).resolve().parents[1]/'docs/joint-proposal-results.csv').read_text())
    def test_grid_and_zero_events(self):
        self.assertEqual(validate(self.rows),900)
        summary=list(summaries(self.rows))
        self.assertEqual(next(r for r in summary if r['case']=='rare-event' and r['method']=='prior')['zeroEstimates'],84)
    def test_reject_changed_evidence(self):
        for data in (self.rows[:-1],self.rows+[self.rows[0]]):
            with self.assertRaises(ValueError):validate(data)
        for field,value in [('reference','0'),('ess','5000'),('mcse','-1'),('draws','3999'),('mean','NaN')]:
            rows=[dict(r) for r in self.rows];rows[0][field]=value
            with self.subTest(field=field),self.assertRaises(ValueError):validate(rows)

if __name__=='__main__':unittest.main()
