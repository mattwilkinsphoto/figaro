import unittest
from pathlib import Path
from summarize_mixture_proposals import load,validate,summaries

class MixtureEvidenceTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.rows=load((Path(__file__).resolve().parents[1]/'docs/mixture-proposal-results.csv').read_text(encoding='utf-8'))
    def test_complete_pairing(self):
        self.assertEqual(validate(self.rows),1200)
        result=list(summaries(self.rows)); self.assertEqual(len(result),6)
        self.assertEqual(sum(r['multi']['accurate'] for r in result if r['draws']==10000),300)
    def test_incomplete_or_changed_evidence_rejected(self):
        for rows in (self.rows[:-1],self.rows+[self.rows[0]],self.rows[:12]):
            with self.assertRaises(ValueError): validate(rows)
        for field,value in (('reference','nan'),('mean','nan'),('ess','99999'),('pilotCalls','0'),
                            ('fitCalls','1'),('productionCalls','0'),('accurate','false'),('health','NotRun')):
            rows=[dict(r) for r in self.rows]; rows[0][field]=value
            with self.subTest(field=field),self.assertRaises(ValueError): validate(rows)
    def test_all_fit_refusals_remain_reportable(self):
        rows=[dict(r) for r in self.rows]
        for r in rows:
            if r['method']=='multi': r.update(fit='IterationLimit',productionCalls='0',mean='NA',ess='NA',accurate='false',health='NotRun')
        self.assertEqual(validate(rows),1200)
        self.assertTrue(all(r['multi']['medianEss'] is None for r in summaries(rows)))

if __name__=='__main__': unittest.main()
