"""Independent analytic/failure controls for the INF-19 numerical analysis."""
import math
import unittest
import numpy as np
from branch_qualification import OBS, density, differences, interval, recursion, solve, weights


class QualificationControls(unittest.TestCase):
    def test_simpson_exact_cubic(self):
        x=np.linspace(-2,3,101)
        self.assertAlmostEqual(float(weights(len(x),.05)@(x**3)), (3**4-(-2)**4)/4, places=12)

    def test_even_grid_refused(self):
        with self.assertRaises(AssertionError): weights(100,.1)

    def test_non_grid_interval_and_partition(self):
        x=np.linspace(-4,4,801);p=density(x,0,1)
        got=interval(x,p,-.337,1.123)
        want=.5*(math.erf(1.123/math.sqrt(2))-math.erf(-.337/math.sqrt(2)))
        self.assertLess(abs(got-want),1e-7)
        parts=sum(interval(x,p,a,b) for a,b in zip([-4,-.337,1.123],[-.337,1.123,4]))
        self.assertLess(abs(parts-float(weights(len(x),.01)@p)),1e-7)

    def test_lsoda_analytic_linear(self):
        omega=math.sqrt(1-.075**2)
        for t in (2,6):
            for x in (-3,0,3):
                expected=math.exp(-.075*t)*(.2*math.cos(omega*t)+(x+.015)/omega*math.sin(omega*t))
                self.assertLess(abs(solve(x,t,linear=True)-expected),1e-8)

    def test_sensitivity_vs_finite_difference(self):
        for x in (-2.2,.4,2.3):
            finite=(solve(x+1e-5,6)-solve(x-1e-5,6))/2e-5
            self.assertLess(abs(finite-solve(x,6,tangent=True)),1e-5)

    def test_full_linear_recursion(self):
        x=np.linspace(-8,8,801)
        rows,p=recursion(x,x[:,None],[[]])
        m=0.;v=1.21;logz=0.
        for t,y in enumerate(OBS):
            if t:m*=.7;v=.49*v+.64
            logz+=math.log(float(density(y,m,v+.04)))
            gain=v/(v+.04);m+=gain*(y-m);v*=1-gain
            self.assertLess(abs(rows[t]['mean']-m),1e-9)
            self.assertLess(abs(rows[t]['second']-m*m-v),1e-9)
            self.assertLess(abs(rows[t]['logEvidence']-logz),1e-9)
            self.assertLess(abs(float(weights(len(x),.02)@p[t,:,0])-1),1e-10)

    def test_metric_mismatch_refused(self):
        row=dict(column=0,step=0,mean=0,second=1,positive=.5,tail=0,logEvidence=0,branches=[1])
        self.assertEqual(differences([row],[row]),0)
        self.assertEqual(differences([dict(row,positive=.6)],[row]),.6-.5)
        with self.assertRaises(AssertionError): differences([row],[dict(row,step=1)])


if __name__=='__main__': unittest.main()
