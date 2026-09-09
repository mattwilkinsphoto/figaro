"""Read-only validation of all recorded capability-expansion benchmark rows."""
import csv
import math
from pathlib import Path
import unittest

class CapabilityEvidence(unittest.TestCase):
    def read(self,prefix):
        root=Path(__file__).resolve().parents[1]/'docs'
        runs=[]
        for s in 'abc':
            with (root/f'{prefix}-{s}.csv').open(newline='',encoding='utf-8') as stream:
                runs.append(list(csv.DictReader(stream)))
        return runs
    def test_weighted_completeness_budgets_and_refusals(self):
        for rows in self.read('weighted-rare-event'):
            self.assertEqual(len(rows),360)
            self.assertEqual(len({(r['case'],r['seed'],r['method']) for r in rows}),360)
            self.assertEqual({int(r['seed']) for r in rows},set(range(95000,95030)))
            for r in rows:
                calls=int(r['pilotCalls'])+int(r['productionCalls'])
                if r['status'] in ('Fitted','Completed'):
                    self.assertEqual(calls,12000)
                    self.assertTrue(math.isfinite(float(r['estimate'])))
                else:
                    self.assertEqual(int(r['productionCalls']),0)
                    self.assertTrue(math.isnan(float(r['estimate'])))
                    self.assertLessEqual(calls,8000)
                self.assertLessEqual(int(r['componentCalls']),5000000)
                self.assertGreater(float(r['seconds']),0)
            for name,failures in [('common',8),('one_tail',4),('two_tail',0)]:
                self.assertEqual(sum(r['case']==name and r['method']=='weighted' and r['status']!='Fitted' for r in rows),failures)
    def test_cross_jvm_replay_not_independent_statistical_replicates(self):
        def canonical(rows): return sorted(tuple((k,v) for k,v in sorted(r.items()) if k!='seconds') for r in rows)
        for prefix in ('weighted-rare-event','static-proposal'):
            a,b,c=self.read(prefix)
            self.assertEqual(canonical(a),canonical(b)); self.assertEqual(canonical(a),canonical(c))
    def test_graph_complete_pairs_and_owned_static_agreement(self):
        for rows in self.read('static-proposal'):
            self.assertEqual(len(rows),240)
            self.assertEqual(len({(r['model'],r['method'],r['seed']) for r in rows}),240)
            self.assertEqual({int(r['seed']) for r in rows},set(range(98000,98020)))
            for r in rows:
                self.assertEqual(int(r['draws']),6000); self.assertGreater(float(r['seconds']),0)
                self.assertTrue(math.isfinite(float(r['estimate'])))
            for model in '012':
                for seed in range(98000,98020):
                    selected=[r for r in rows if r['model']==model and r['seed']==str(seed) and r['method']!='1']
                    self.assertEqual(len(selected),3)
                    self.assertLess(max(float(r['estimate']) for r in selected)-min(float(r['estimate']) for r in selected),1e-12)
    def test_region_gain_and_simple_case_counterexamples(self):
        def rmse(rows): return math.sqrt(sum((float(r['estimate'])/float(r['truth'])-1)**2 for r in rows)/len(rows))
        rows=self.read('weighted-rare-event')[0]
        single=[r for r in rows if r['case']=='two_tail' and r['method']=='single']
        weighted=[r for r in rows if r['case']=='two_tail' and r['method']=='weighted']
        self.assertLess(rmse(weighted),rmse(single)/2)
        self.assertTrue(any(r['case']=='common' and r['method']=='weighted' and r['status']!='Fitted' for r in rows))

if __name__=='__main__': unittest.main()
