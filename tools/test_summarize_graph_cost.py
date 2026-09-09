import unittest
from pathlib import Path
from summarize_graph_cost import load,validate,summaries

class GraphCostEvidenceTest(unittest.TestCase):
    def setUp(self): self.rows=load((Path(__file__).resolve().parents[1]/'docs/graph-cost-results.csv').read_text())
    def test_complete_and_repeat_headers(self):
        self.assertEqual(validate(self.rows),1800)
        self.assertEqual(len(list(summaries(self.rows))),30)
        self.assertEqual(load('GC,a,b\nGC,1,2\n'),[{'a':'1','b':'2'}])
    def test_refusals_are_not_silently_filtered(self):
        output=list(summaries(self.rows))
        refused=[r for r in output if r['refusals']]
        self.assertTrue(refused)
        self.assertTrue(all(r['rmse'] is None for r in refused))
    def test_changed_or_incomplete_evidence_is_rejected(self):
        for data in (self.rows[:-1],self.rows+[self.rows[0]]):
            with self.assertRaises(ValueError): validate(data)
        for field,value in [('mean','NaN'),('totalSeconds','-1'),('pilotTransitions','999'),('reference','0'),('status','Ignore')]:
            rows=[dict(r) for r in self.rows]; rows[0][field]=value
            with self.subTest(field=field),self.assertRaises(ValueError): validate(rows)

if __name__=='__main__': unittest.main()
