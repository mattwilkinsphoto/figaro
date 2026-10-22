import math
import unittest
from decimal import Decimal as D
from stratified_numerical_diagnostics import cdf_from_weights, describe, flags, high_precision, metrics


class DiagnosticTests(unittest.TestCase):
    def test_exact_equal_and_zero_support(self):
        for logs, expected in [([0.,0.],[D('.5'),D('.5')]),([-math.inf,0.],[D(0),D(1)])]:
            p, c = high_precision(logs,80)
            self.assertEqual(p,expected)
            self.assertEqual(c[-1],D(1))

    def test_endpoint_not_underflow(self):
        logs=[0.,-40.];w=[1.,math.exp(-40)]
        self.assertEqual(flags(logs,w,[1.,1.]),[(1,['endpoint_collapse'])])
        r=describe(logs,w,[1.,1.])
        self.assertGreater(D(r['methods']['original']['lost_mass']),0)
        self.assertEqual(D(r['methods']['original']['underflow_mass']),0)
        self.assertTrue(r['methods']['rounded_high_precision']['strict_guard_refuses'])

    def test_underflow_overlap_and_nonmonotone(self):
        self.assertEqual(flags([0.,-1000.],[1.,0.],[1.,1.])[0][1],['ordinary_underflow','endpoint_collapse'])
        p,c=high_precision([0.,0.,0.],80)
        r=metrics([0.,0.,0.],[1/3]*3,[.5,1.01,1.],p,c)
        self.assertFalse(r['monotone'])
        self.assertIsNone(r['total_variation'])

    def test_cumulative_stagnation(self):
        w=[.5,1e-30,.5];logs=list(map(math.log,w))
        self.assertEqual(flags(logs,w,cdf_from_weights(w,logs)),[(1,['interior_stagnation'])])


if __name__=='__main__':unittest.main()
