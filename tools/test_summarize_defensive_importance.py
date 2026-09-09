import unittest
from pathlib import Path
from summarize_defensive_importance import read, validate, summarize, wilson

class DefensiveEvidenceTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.root=Path(__file__).resolve().parents[1]/'docs'
        cls.rows=read((cls.root/'defensive-importance-results.csv').read_text(encoding='utf-8'))

    def test_complete_grid_and_summary(self):
        self.assertEqual(validate(self.rows),1080)
        summaries=list(summarize(self.rows))
        self.assertEqual(len(summaries),36)
        chosen=[r for r in summaries if r['method']=='defensive' and r['budget']==20000]
        self.assertEqual(sum(r['allAccurate'] for r in chosen),120)
        self.assertEqual(sum(r['fallbacks'] for r in summaries if r['method']=='defensive' and r['budget']==2000),120)

    def test_missing_duplicate_and_smoke_rejected(self):
        for rows in (self.rows[:-1],self.rows+[self.rows[0]],self.rows[:30]):
            with self.assertRaises(ValueError): validate(rows)

    def test_changed_work_evidence_and_outcomes_rejected(self):
        for key,value in (('budget','123'),('evaluations','1999'),('pilotCalls','1'),
                          ('mean','nan'),('error','1000'),('accurate','false'),
                          ('seconds','0'),('status','Success'),('draws','123')):
            changed=[dict(r) for r in self.rows]
            changed[0][key]=value
            with self.subTest(field=key),self.assertRaises(ValueError): validate(changed)

    def test_log_envelope_and_repeated_metadata(self):
        text=(self.root/'defensive-importance-results.csv').read_text(encoding='utf-8')
        log='noise\n'+'\n'.join('DI,'+line for line in text.splitlines())+'\nmore noise'
        self.assertEqual(read(log),self.rows)
        changed=[dict(r) for r in self.rows]
        changed[1]['seconds']='10'
        with self.assertRaises(ValueError): validate(changed)

    def test_wilson_controls(self):
        low,high=wilson(190,200)
        self.assertLess(low,.95)
        self.assertGreater(high,.95)
        self.assertAlmostEqual(wilson(0,200)[0],0,places=14)
        self.assertAlmostEqual(wilson(200,200)[1],1,places=14)

    def test_complete_fresh_seed_coverage(self):
        rows=read((self.root/'defensive-importance-coverage.csv').read_text(encoding='utf-8'))
        self.assertEqual(validate(rows,coverage=True),400)
        with self.assertRaises(ValueError): validate(rows[:-1],coverage=True)
        with self.assertRaises(ValueError): validate(rows)
        with self.assertRaises(ValueError): wilson(201,200)
        changed=[dict(r) for r in self.rows]
        changed[0]['status']='ChecksPassed'
        with self.assertRaises(ValueError): validate(changed)

if __name__=='__main__': unittest.main()
