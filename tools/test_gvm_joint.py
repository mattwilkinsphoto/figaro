"""Independent coordinate differences and bounded joint-fit failure controls."""
import copy,unittest
from types import SimpleNamespace
from unittest.mock import patch
import numpy as np
import gvm_dimension_controls as c
import gvm_wholefit_math as m
import gvm_joint_fit as f
import gvm_joint_audit as audit
from gvm_dimension_audit import reference_log


class JointTests(unittest.TestCase):
    def setUp(self):
        _,self.model=c.truth(2,'seam')
        self.data=c.sample_gvm(np.random.Generator(np.random.PCG64DXSM(550001)),self.model,160)

    def test_roundtrip_and_all_transformed_derivatives(self):
        for d in (2,3):
            _,model=c.truth(d,'seam');coords=f.Coordinates(d,len(model['components']))
            data=c.sample_gvm(np.random.Generator(np.random.PCG64DXSM(550002)),model,100)
            x=coords.pack(model);restored=coords.unpack(x)
            self.assertLess(np.max(abs(c.gvm_log(data,model)-c.gvm_log(data,restored))),1e-12)
            _,_,gradient=m.derivatives(data,restored);analytic=coords.pullback(x,gradient)
            for i in range(len(x)):
                h=1e-5;plus=x.copy();minus=x.copy();plus[i]+=h;minus[i]-=h
                numeric=(-c.gvm_log(data,coords.unpack(plus)).mean()+c.gvm_log(data,coords.unpack(minus)).mean())/(2*h)
                self.assertLess(abs(numeric-analytic[i]),2e-6)
            for point in data[:2]:self.assertLess(abs(c.gvm_log(point[None,:],restored)[0]-reference_log(point,restored,'gvm')),1e-8)

    def test_spectral_jacobian_and_simplex(self):
        coords=f.Coordinates(2,2);x=coords.pack(self.model)
        jac=coords.spectral(x,True)
        for i in range(len(x)):
            plus=x.copy();minus=x.copy();plus[i]+=1e-6;minus[i]-=1e-6
            numeric=(coords.spectral(plus)-coords.spectral(minus))/2e-6
            self.assertLess(np.max(abs(numeric-jac[:,i])),2e-6)
        self.assertTrue(coords.feasible(x));x[0]+=.1;self.assertFalse(coords.feasible(x))

    def test_multiplicity_boundaries_periodicity_and_zero_concentration(self):
        model=copy.deepcopy(self.model);coords=f.Coordinates(2,2)
        for a in model['components']:a.update(cov=(np.eye(2)*.01).tolist(),kappa=0.)
        x=coords.pack(model);self.assertTrue(coords.feasible(x))
        self.assertTrue(np.all(np.isfinite(coords.spectral(x,True))))
        _,_,grad=m.derivatives(self.data,model)
        self.assertTrue(all(a['alpha']==0 and np.all(a['coeff']==0) for a in grad))
        for a in model['components']:a['alpha']+=2*np.pi
        self.assertLess(np.max(abs(c.gvm_log(self.data,model)-c.gvm_log(self.data,coords.unpack(x)))),1e-10)
        model['components'][0]['cov']=(np.eye(2)*.009).tolist()
        self.assertFalse(coords.feasible(coords.pack(model)))

    def test_limits_and_invalid_inputs(self):
        for options,status in ((dict(max_work=0),'WorkBudgetExhausted'),(dict(seconds=1e-12),'TimeBudgetExhausted')):
            result=f.fit(self.data,2,17,**options);self.assertEqual(result['status'],status)
        self.assertEqual(f.fit(np.ones((40,3)),2,17)['status'],'RankDeficient')
        for data in (np.array([[np.nan]*3]*40),np.ones((2,3))):
            with self.assertRaises(ValueError):f.fit(data,2,17)

    def test_joint_call_limit_ledger_and_monotone_retention(self):
        result=f.fit(self.data,2,17,starts=1,prefix_maps=2,calls=1)
        self.assertIn(result['status'],('JointCallLimit','Stationary'))
        self.assertEqual(result['totalWork'],sum(e['units'] for e in result['ledger']))
        self.assertEqual(result['totalWork'],sum(result['workByStart']))
        self.assertAlmostEqual(result['trainScore'],c.gvm_log(self.data,result).sum(),places=8)
        self.assertTrue(np.all(np.diff(result['starts'][0]['trace'])>=-1e-8))

    def test_solver_success_is_not_physical_qualification(self):
        with patch.object(f,'minimize',return_value=SimpleNamespace(success=True,message='fake success',nit=0)):
            result=f.fit(self.data,2,17,starts=1,prefix_maps=1)
        self.assertEqual(result['status'],'SolverStoppedUnqualified')
        self.assertGreater(result['diagnostic']['maximum'],1e-4)

    def test_global_exhaustion_cannot_be_hidden_by_qualified_checkpoint(self):
        def initialize(s):
            s.budget.charge('score',1)
            if s.index==1:s.budget.charge('score',100)
            s.retain(copy.deepcopy(self.model),10.,dict(maximum=0.))
        with patch.object(f.prefix.Start,'initialize',initialize):result=f.fit(self.data,2,17,max_work=3)
        self.assertEqual(result['status'],'WorkBudgetExhausted')
        self.assertEqual(result['diagnostic']['maximum'],0.)

    def test_low_support_and_infeasible_trials_are_not_retained(self):
        def fake(objective,x,**kwargs):
            y=x.copy();y[0]+=.1;objective(y)
            return SimpleNamespace(success=True,message='infeasible trial',nit=1)
        with patch.object(f,'minimize',side_effect=fake):result=f.fit(self.data,2,17,starts=1,prefix_maps=1)
        self.assertEqual(result['starts'][0]['joint']['infeasibleTrials'],1)
        self.assertAlmostEqual(sum(a['weight'] for a in result['components']),1.)
        original=f.prefix.Start.evaluate
        def reject(s,model):
            if 'joint' in s.record:raise f.g.Limit('InsufficientComponent')
            return original(s,model)
        def same(objective,x,**kwargs):
            objective(x);return SimpleNamespace(success=True,message='support refusal',nit=1)
        with patch.object(f,'minimize',side_effect=same),patch.object(f.prefix.Start,'evaluate',reject):
            result=f.fit(self.data,2,17,starts=1,prefix_maps=1)
        self.assertEqual(result['starts'][0]['joint']['lowSupportTrials'],1)

    def test_inventory_and_omitted_joint_work_are_rejected(self):
        cfg=dict(dimensions=[2],families=['seam'],components=[2],arms=['baseline','joint'])
        rows=[dict(d=2,family='seam',k=2,arm=a) for a in cfg['arms']]
        audit.inventory(rows,cfg)
        for bad in (rows[:-1],rows+[rows[0]]):
            with self.assertRaises(ValueError):audit.inventory(bad,cfg)
        result=f.fit(self.data,2,17,starts=1,prefix_maps=1,calls=1)
        audit.ledger(result,len(self.data),2)
        result['ledger']=result['ledger'][:-1]
        with self.assertRaises(AssertionError):audit.ledger(result,len(self.data),2)

if __name__=='__main__':unittest.main()
