"""Independent differentiation, solve and failure controls for GVM-06."""
import unittest
import mpmath as mp
import numpy as np
from scipy.optimize import minimize
import gvm_gradient_fit as g
import gvm_dimension_controls as c
from gvm_dimension_audit import reference_log


class GradientControls(unittest.TestCase):
    def fixture(self,d=2,n=400):
        rng=np.random.Generator(np.random.PCG64DXSM(240001+d))
        _,model=c.truth(d,'curved'); raw=c.sample_gvm(rng,model,n)
        center,lower=c.whitening(raw)
        return c.transform(raw,center,lower)

    def test_high_precision_derivatives(self):
        for d in (1,2,3):
            data=self.fixture(d,40); features=c.phase_features(data[:,:-1]); q=features.shape[1]
            weights=np.linspace(.02,2.,len(data)); beta=np.linspace(-.3,.4,q)
            with mp.workdps(60):
                w=list(map(mp.mpf,weights.tolist())); mass=mp.fsum(w)
                def objective(*args):
                    # Complex resultant; direct polynomial construction, not candidate features.
                    terms=[]
                    for row,weight in zip(data,w):
                        phase=mp.fsum(args[j]*mp.mpf(float(row[j])) for j in range(d)); index=d
                        for a in range(d):
                            for b in range(a,d):
                                phase+=args[index]*mp.mpf(float(row[a]))*mp.mpf(float(row[b]))*(mp.mpf('.5') if a==b else 1);index+=1
                        terms.append(weight*mp.exp(1j*(mp.mpf(float(row[-1]))-phase))/mass)
                    z=mp.fsum(terms); return -abs(z)**2
                value,gradient=g.objective(beta,features,data[:,-1],weights)
                self.assertLess(abs(value-float(objective(*map(mp.mpf,beta.tolist())))),1e-12)
                for j in range(q):
                    orders=tuple(int(i==j) for i in range(q))
                    exact=float(mp.diff(objective,tuple(map(mp.mpf,beta.tolist())),orders))
                    self.assertLess(abs(gradient[j]-exact),1e-10)
                    delta=np.eye(q)[j]*1e-5
                    finite=(g.objective(beta+delta,features,data[:,-1],weights)[0]-g.objective(beta-delta,features,data[:,-1],weights)[0])/2e-5
                    self.assertLess(abs(gradient[j]-finite),1e-7)

    def test_independent_solver_and_derivative_ablation(self):
        for d in (1,2,3):
            data=self.fixture(d); f=c.phase_features(data[:,:-1]); w=np.ones(len(data)); b=np.zeros(f.shape[1])
            analytic,record=g.angular(b,f,data[:,-1],w,g.Budget())
            numeric,nrecord=g.angular(b,f,data[:,-1],w,g.Budget(),analytic=False,max_calls=4000)
            # Independently written old resultant evaluator, finite-difference SLSQP.
            ref=minimize(lambda x:c.resultant_objective(x,f,data[:,-1],w)[0],b,
                         method='SLSQP',bounds=[(-4,4)]*len(b),options={'maxiter':400,'ftol':1e-12})
            self.assertTrue(ref.success,ref.message); self.assertTrue(record['qualified'],record)
            self.assertLess(abs(record['objective']-ref.fun),1e-7)
            self.assertLess(abs(record['objective']-nrecord['objective']),1e-7)
            self.assertLess(record['calls'],nrecord['calls'])

    def test_projected_boundary_stationarity(self):
        self.assertEqual(g.projected_gradient(np.array([-4.,4.]),np.array([2.,-2.])),0.)
        self.assertGreater(g.projected_gradient(np.array([-4.,4.]),np.array([-2.,2.])),1.)
        data=self.fixture(); f=c.phase_features(data[:,:-1]);w=np.ones(len(data))
        _,limited=g.angular(np.zeros(f.shape[1]),f,data[:,-1],w,g.Budget(),max_calls=1)
        self.assertFalse(limited['qualified']);self.assertEqual(limited['message'],'AngularBudgetExhausted')

    def test_density_replay_and_monotonicity(self):
        for d in (1,2,3):
            data=self.fixture(d); result=g.fit(data,1,42); replay=g.fit(data,1,42)
            self.assertEqual(result['status'],'Converged',result['status'])
            self.assertEqual(result['components'],replay['components'])
            self.assertEqual(result['starts'],replay['starts'])
            self.assertLess(abs(float(c.gvm_log(data,result).sum())-result['trainScore']),1e-8)
            for point in data[[7,-1]]:
                self.assertLess(abs(reference_log(point,result,'gvm')-c.gvm_log(point[None,:],result)[0]),1e-8)
            for start in result['starts']:
                self.assertTrue(np.all(np.diff(start['trace'])>=-1e-8*len(data)))

    def test_budget_and_invalid_controls(self):
        data=self.fixture()
        self.assertEqual(g.fit(data,1,42,max_points=1)['status'],'PhaseBudgetExhausted')
        self.assertEqual(g.fit(data,1,42,seconds=1e-9)['status'],'TimeBudgetExhausted')
        self.assertEqual(g.fit(data,1,42,iterations=1)['status'],'IterationLimit')
        self.assertEqual(g.fit(np.zeros((100,3)),1,42)['status'],'RankDeficient')
        self.assertEqual(g.fit(data[:60],3,42)['status'],'InsufficientComponent')
        with self.assertRaises(ValueError):g.fit(np.full((100,3),np.nan),1,42)
        with self.assertRaises(ValueError):g.fit(data,4,42)
        result=g.fit(data,2,42,max_points=1200)
        self.assertEqual(result['status'],'PhaseBudgetExhausted')
        self.assertLessEqual(result['phasePoints'],1200)
        self.assertTrue(result['components'])
        # Budget exhaustion preserves the last completely evaluated model.
        self.assertAlmostEqual(float(c.gvm_log(data,result).sum()),result['trainScore'],places=8)


if __name__=='__main__':
    unittest.main(verbosity=2)
