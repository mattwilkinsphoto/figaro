import math
import unittest
from decimal import Decimal as D
from decision_stratified_validation import reference


class DecisionOracleTests(unittest.TestCase):
    def test_equal_exact_grid(self):
        for n in range(2,17):
            for u in (0.,.5,math.nextafter(1.,0.)):
                a,_,_=reference([0.]*n,[u]*n,100)
                self.assertEqual(a,list(range(n)))

    def test_near_tie(self):
        a,_,_=reference([0.,-1e-17],[0.,0.],100)
        self.assertEqual(a,[0,0])

    def test_zero_and_tiny_support(self):
        a,_,_=reference([-math.inf,-1000.,0.,-math.inf],[0.,.5,.5,math.nextafter(1.,0.)],180)
        self.assertEqual(a,[1,2,2,2])

    def test_symbolic_symmetry(self):
        a,_,_=reference([0.,-1.,0.,-1.],[0.]*4,100)
        self.assertEqual(a,[0,0,2,2])


if __name__=='__main__':unittest.main()
