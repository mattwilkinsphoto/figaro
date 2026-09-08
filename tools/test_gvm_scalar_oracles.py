"""Optional 80-digit controls for every scalar timing fixture; mpmath==1.3.0."""
import math
import unittest
import mpmath as mp
from gvm_bhattacharyya_research import Comparison, Kernel
from summarize_gvm_scalar_performance import ORACLES


class ScalarTimingOracleTest(unittest.TestCase):
    def test_all_eight_oracles_from_physical_binary64_inputs(self):
        with mp.workdps(80):
            def kernel(mu=0., sd=1., alpha=0., beta=0., gamma=0., k=0.):
                # Scala receives sd*sd as covariance, not an exact decimal standard deviation.
                return Kernel(mp.matrix([mp.mpf(mu)]), mp.matrix([[mp.sqrt(mp.mpf(sd*sd))]]),
                              mp.mpf(alpha), mp.matrix([mp.mpf(beta)]), mp.matrix([[mp.mpf(gamma)]]), mp.mpf(k))
            pairs = {
                'gaussian': (kernel(), kernel(mu=2.)),
                'constant-opposed': (kernel(k=50.), kernel(alpha=math.pi, k=50.)),
                'linear-moderate': (kernel(k=4.), kernel(beta=.4, k=4.)),
                'curved-unequal': (kernel(.3, 1.1, .2, .7, .3, 4.5), kernel(-.4, .8, -.5, -.2, -.15, 1.2)),
                'linear-concentrated': (kernel(k=50.), kernel(beta=1., k=50.)),
                'opposed-weak': (kernel(k=50.), kernel(alpha=math.pi, beta=1e-5, k=50.)),
                'opposed-moderate': (kernel(k=50.), kernel(alpha=math.pi, beta=.1, k=50.)),
                'curved-concentrated': (kernel(.5, 1., .25, .5, .25, 50.), kernel(-.5, .5, -.5, -.25, 2., 50.)),
            }
            self.assertEqual(set(pairs), set(ORACLES))
            for name, (p, q) in pairs.items():
                with self.subTest(case=name):
                    comparison = Comparison(p, q)
                    value, tail = comparison.series(128)
                    self.assertGreater(value, 0)
                    self.assertLess(tail/value, mp.mpf('1e-45'))
                    distance = comparison.gaussian_distance-mp.log(value)
                    self.assertLess(abs(distance-mp.mpf(ORACLES[name])), mp.mpf('1e-14'))


if __name__ == '__main__':
    unittest.main()
