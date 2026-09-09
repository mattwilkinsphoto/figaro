"""Read-only integrity checks for the complete three-JVM rare-event cost study."""
import csv
import math
from pathlib import Path
import unittest

class RareEventEvidenceTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        root=Path(__file__).resolve().parents[1]/'docs'
        cls.runs=[]
        for label in 'abc':
            with (root/f'rare-event-cost-jvm-{label}.csv').open(newline='') as f:
                cls.runs.append(list(csv.DictReader(f)))

    def test_complete_pairs_and_retained_refusals(self):
        for rows in self.runs:
            self.assertEqual(len(rows),360)
            keys={(r['case'],int(r['seed']),r['method']) for r in rows}
            self.assertEqual(len(keys),360)
            self.assertEqual({k[0] for k in keys},{'common','tail3','tail5','two_tail4'})
            self.assertEqual({k[1] for k in keys},set(range(90000,90030)))
            self.assertEqual(sum(r['status']=='InsufficientElite' for r in rows),22)

    def test_total_work_and_no_estimate_on_refusal(self):
        for rows in self.runs:
            for r in rows:
                calls=int(r['pilotCalls'])+int(r['productionCalls'])
                self.assertEqual(int(r['densityCalls']),2*calls)
                self.assertGreater(float(r['seconds']),0)
                if r['status'] in ('Completed','Fitted'):
                    self.assertEqual(calls,10000)
                    self.assertTrue(math.isfinite(float(r['estimate'])))
                else:
                    self.assertLessEqual(calls,5000)
                    self.assertEqual(int(r['productionCalls']),0)
                    self.assertTrue(math.isnan(float(r['estimate'])))

    def test_replay_without_counting_timing_repeats_as_independent_trials(self):
        def canonical(rows):
            return sorted(tuple((k,v) for k,v in sorted(r.items()) if k!='seconds') for r in rows)
        self.assertEqual(canonical(self.runs[0]),canonical(self.runs[1]))
        self.assertEqual(canonical(self.runs[0]),canonical(self.runs[2]))

    def test_favorable_and_unfavorable_results_remain_visible(self):
        rows=self.runs[0]
        def rmse(case,method):
            group=[r for r in rows if r['case']==case and r['method']==method]
            return math.sqrt(sum((float(r['estimate'])/float(r['truth'])-1)**2 for r in group)/len(group))
        self.assertLess(rmse('tail3','ce'),.03)
        self.assertGreater(rmse('tail3','prior'),.2)
        self.assertEqual(sum(r['case']=='tail5' and r['method']=='prior' and int(r['hits'])==0 for r in rows),30)
        self.assertLess(rmse('two_tail4','supplied'),.05)

if __name__=='__main__': unittest.main()
