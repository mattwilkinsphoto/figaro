import unittest
from pathlib import Path
from summarize_importance_calibration import load, validate, summaries

class CalibrationEvidenceTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.text=(Path(__file__).resolve().parents[1]/'docs/importance-calibration-results.csv').read_text(encoding='utf-8')
        cls.rows=load(cls.text)

    def test_complete_grid_and_negative_controls(self):
        self.assertEqual(validate(self.rows),6600)
        result=list(summaries(self.rows)); self.assertEqual(len(result),33)
        self.assertTrue(all(r['accurate']==0 for r in result if r['case']=='missed-mode'))
        rare=next(r for r in result if r['case']=='rare' and r['draws']==2000)
        tilted=next(r for r in result if r['case']=='rare-tilted' and r['draws']==2000)
        self.assertEqual(rare['accurate'],0); self.assertEqual(tilted['accurate'],200)

    def test_missing_duplicate_smoke_rejected(self):
        for rows in (self.rows[:-1],self.rows+[self.rows[0]],self.rows[:33]):
            with self.assertRaises(ValueError): validate(rows)

    def test_corruption_rejected(self):
        for field,value in (('reference','9'),('tolerance','9'),('mean','nan'),('rawMcse','-1'),
                            ('batchMcse','-1'),('varianceEss','99999999'),('queryK','nan'),
                            ('coveredRaw','false'),('accurate','false'),('pilotCalls','1'),
                            ('health','Success'),('queryFlag','true')):
            rows=[dict(r) for r in self.rows]; rows[0][field]=value
            with self.subTest(field=field),self.assertRaises(ValueError): validate(rows)

    def test_log_envelope(self):
        self.assertEqual(load('noise\n'+'\n'.join('IC,'+s for s in self.text.splitlines())),self.rows)

if __name__=='__main__': unittest.main()
