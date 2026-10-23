import unittest,math
import numpy as np
from multicomponent_reference import grid,oracle,kalman,normal,spec,NAMES

class ReferenceTest(unittest.TestCase):
    def test_partition_quadrature(self):
        x,q=grid(10,.025);p=normal(x,0,1)
        self.assertAlmostEqual(float(q(-10,-1)@p+q(-1,1)@p+q(1,10)@p),1,places=12)
        self.assertAlmostEqual(float(q(0,10)@p),.5,places=12)
    def test_affine_independent_recursion(self):
        for a,b in zip(oracle('affine',10,.00625),kalman()):
            for k in b:self.assertLess(abs(a[k]-b[k]),1e-9)
    def test_constant_likelihood(self):
        s=spec('flat');v=1.21;z=0.
        for t,a in enumerate(oracle('flat',10,.025)):
            if t:v=.49*v+.64
            z+=math.log(float(normal(s['observations'][t],0,.5)))
            self.assertAlmostEqual(a['logEvidence'],z,places=10)
            self.assertAlmostEqual(a['second'],v,places=9)
    def test_declared_truth_not_anchor_approximations(self):
        self.assertEqual(oracle('omitted',10,.025),oracle('symmetric',10,.025))
        self.assertEqual(len(NAMES),11)
    def test_three_regions_are_nontrivial(self):
        a=oracle('three',10,.0125)[0]
        self.assertAlmostEqual(a['r0'],a['r2'],places=10)
        self.assertTrue(all(a[k]>.01 for k in ('r0','r1','r2')))

if __name__=='__main__':unittest.main()
