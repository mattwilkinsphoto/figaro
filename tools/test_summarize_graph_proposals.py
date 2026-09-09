import unittest
from pathlib import Path
from summarize_graph_proposals import load,validate,summaries

class GraphEvidenceTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.rows=load((Path(__file__).resolve().parents[1]/'docs/graph-proposal-results.csv').read_text(encoding='utf-8'))
    def test_complete_grid(self):
        self.assertEqual(validate(self.rows),600)
        self.assertEqual(len(list(summaries(self.rows))),3)
    def test_incomplete_or_changed_evidence_rejected(self):
        for rows in (self.rows[:-1],self.rows+[self.rows[0]]):
            with self.assertRaises(ValueError): validate(rows)
        for field,value in (('reference','nan'),('mean','nan'),('ess','99999'),('priorCalls','0'),
                            ('proposalDraws','0'),('attempts','1000'),('accurate','false'),('health','NotRun'),('rejected','-1')):
            rows=[dict(r) for r in self.rows]; rows[0][field]=value
            with self.subTest(field=field),self.assertRaises(ValueError): validate(rows)
    def test_danger_is_not_filtered_out(self):
        rows=[dict(r,health='Danger') for r in self.rows]
        self.assertEqual(validate(rows),600)
        self.assertEqual(list(summaries(rows)),list(summaries(self.rows)))

if __name__=='__main__': unittest.main()
