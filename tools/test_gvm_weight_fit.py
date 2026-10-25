"""GVM-08 integration controls; deliberately distinguish search and solution status."""
import copy, unittest
from unittest.mock import patch
import numpy as np
import gvm_dimension_controls as c
import gvm_gradient_fit as g
import gvm_wholefit_fit as old
import gvm_wholefit_math as m
import gvm_weight_fit as f


class FitTests(unittest.TestCase):
    def setUp(self):
        _,self.model=c.truth(2,'seam')
        self.data=c.sample_gvm(np.random.Generator(np.random.PCG64DXSM(450001)),self.model,160)

    def test_adapter_matches_unmodified_sequential_baseline(self):
        for k in (1,2):
            a=old.fit(self.data,k,77,method='squarem',maps=8)
            b=f.fit(self.data,k,77,maps=8)
            for key in ('components','trainScore','diagnostic','status','selectedStart','totalWork','angularCalls'):
                self.assertEqual(a[key],b[key],key)
            for x,y in zip(a['starts'],b['starts']):
                for key in x:self.assertEqual(x[key],y[key],key)

    def test_resume_interleaving_equivalence_without_budget_exhaustion(self):
        a=f.fit(self.data,2,77,maps=8,max_work=100000000,polish=True)
        b=f.fit(self.data,2,77,maps=8,max_work=100000000,polish=True,schedule='balanced')
        for key in ('components','trainScore','diagnostic','status','totalWork','angularCalls'):
            self.assertEqual(a[key],b[key],key)
        for x,y in zip(a['starts'],b['starts']):
            for key in ('trace','gradients','polishes','maps'):self.assertEqual(x[key],y[key])
        self.assertEqual(sum(b['workByStart']),b['totalWork'])
        self.assertEqual({e['start'] for e in b['schedule']},{0,1,2})

    def test_hard_limits_initialization_and_retained_score(self):
        for opts,status in ((dict(max_work=0),'WorkBudgetExhausted'),(dict(seconds=1e-12),'TimeBudgetExhausted')):
            fit=f.fit(self.data,2,77,schedule='balanced',**opts)
            self.assertEqual(fit['status'],status)
        fit=f.fit(self.data,2,77,schedule='balanced',maps=4,polish=True)
        self.assertAlmostEqual(fit['trainScore'],float(c.gvm_log(self.data,fit).sum()),places=8)
        self.assertEqual(fit['diagnostic'],m.stationarity(self.data,fit))
        self.assertLessEqual(fit['totalWork'],20000000)
        for record in fit['starts']:
            self.assertLessEqual(record['maps'],4)
            self.assertTrue(np.all(np.diff(record['trace'])>=-1e-10*len(self.data)))

    def test_least_work_ties_expensive_cycles_and_late_improver(self):
        states=['Active']*3;work=[0]*3;count=[0]*3;values=[0]*3
        for _ in range(15):
            i=f.choose(states,work);self.assertEqual(i,min((j for j in range(3) if states[j]=='Active'),key=lambda j:(work[j],j)))
            count[i]+=1;work[i]+=(1,2,4)[i]
            values[i]=10 if i==2 and count[i]>=3 else -10+i
            if count[i]>=5:states[i]='MapLimit'
        self.assertEqual(count,[5,5,5]);self.assertEqual(max(range(3),key=lambda i:values[i]),2)
        self.assertIsNone(f.choose(states,work))

    def test_stationary_retention_does_not_mask_search_exhaustion(self):
        def initialize(s):
            s.budget.charge('score',1)
            diag=dict(maximum=0. if s.index==0 else 1.)
            s.retain(copy.deepcopy(self.model),10.-s.index,diag)
        def cycle(s):
            if s.diag['maximum']==0:s.record['status']='Stationary'
            else:s.budget.charge('score',10)
        with patch.object(f.Start,'initialize',initialize),patch.object(f.Start,'cycle',cycle):
            fit=f.fit(self.data,2,77,max_work=5,schedule='balanced')
        self.assertTrue(fit['retainedQualified']);self.assertEqual(fit['status'],'WorkBudgetExhausted')
        self.assertEqual(fit['selectedStart'],0);self.assertEqual(fit['totalWork'],3)

    def test_low_support_polish_is_explicit_fallback(self):
        with patch.object(f.weights,'solve',return_value=(np.array([1-1e-8,1e-8]),dict(qualified=True,status='Qualified'))):
            fit=f.fit(self.data,2,77,polish=True,maps=1,starts=1)
        self.assertEqual(fit['starts'][0]['polishes'][0]['status'],'LowSupport')
        self.assertGreater(fit['diagnostic']['minimumMass'],30)
        self.assertEqual(fit['diagnostic'],m.stationarity(self.data,fit))

    def test_numerical_polish_refusal_and_all_initializations_fail(self):
        with patch.object(f.weights,'solve',side_effect=ArithmeticError('fixture')):
            fit=f.fit(self.data,2,77,polish=True,maps=1,starts=1)
        self.assertTrue(fit['starts'][0]['polishes'][0]['status'].startswith('NumericalPolishRefusal'))
        with patch.object(f.Start,'initialize',side_effect=g.Limit('InsufficientComponent')):
            fit=f.fit(self.data,2,77,schedule='balanced')
        self.assertEqual(fit['components'],[]);self.assertEqual(len(fit['starts']),3)

    def test_budget_ledger_negative_control(self):
        b=f.Budget(10,120,3);b.active=2;b.charge('weight',7)
        with self.assertRaisesRegex(g.Limit,'WorkBudget'):b.charge('score',4)
        self.assertEqual(sum(b.work.values()),7);self.assertEqual(b.by_start,[0,0,7])


if __name__=='__main__':unittest.main()
