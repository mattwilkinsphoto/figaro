import unittest
import numpy as np
from nonlinear_proposal_reference import rule,oracle,kalman,spec,NAMES

class NonlinearReferenceTest(unittest.TestCase):
    def test_polynomial_and_positive_quadrature(self):
        x,w,p=rule(2,.1)
        self.assertAlmostEqual(w@x**2,16/3,12)
        self.assertAlmostEqual(p@x**2,8/3,12)
        self.assertAlmostEqual(p@np.ones_like(x),2,12)
    def test_affine_independent_kalman(self):
        for a,b in zip(oracle('affine',10,.00625),kalman()):
            for k in a:self.assertAlmostEqual(a[k],b[k],8)
    def test_symmetric_conditional(self):
        for a in oracle('symmetric',10,.025):self.assertAlmostEqual(a['positive'],.5,10)
    def test_declared_inventory(self):
        self.assertEqual(sum(len(spec(n)['observations']) for n in NAMES),38)
        self.assertEqual(len(NAMES),8)

if __name__=='__main__':unittest.main()
