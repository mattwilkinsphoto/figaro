"""Independent high-precision exponential-CS formula and rational remainder checks."""
from fractions import Fraction as F
import math
import unittest
import mpmath as mp


def reference(n=1000):
    """Use binary64 predictable choices, then exact inputs and high-precision DIRECT logarithms."""
    with mp.workdps(100):
        threshold = 6*mp.mpf('0.6931471805599454')  # dyadic upper log(2/.05)
        total = mp.mpf(0)
        weights = mp.mpf(0)
        penalty = mp.mpf(0)
        raw = .5
        variance = .25
        prediction = .5
        for i in range(n):
            desired = min(.5, math.sqrt(2*float(threshold)/(variance*math.log(i+2))))
            l = .5
            while l > desired and l > 2**-32:
                l /= 2
            # Exactly representable bounded sequence, independent of any RNG implementation.
            x = .25 + (i % 3) / 16
            delta = mp.mpf(x)-mp.mpf(prediction)
            penalty += delta**2*(-mp.log1p(-mp.mpf(l))-mp.mpf(l))
            total += mp.mpf(l)*mp.mpf(x)
            weights += mp.mpf(l)
            raw += x
            prediction = raw/(i+2)
            variance += (x-prediction)**2
        center = total/weights
        radius = (threshold+penalty)/weights
        return center, center-radius, center+radius


class EmpiricalBernsteinReferenceTest(unittest.TestCase):
    def test_penalty_series_is_an_upper_bound(self):
        # At lambda=2^-32 the remainder is far below 100-digit precision.
        with mp.workdps(700):
            for i in range(1, 33):
                l = F(1,2**i)
                upper = sum((l**j/j for j in range(2,49)),F(0)) + l**49/(49*(1-l))
                exact = -mp.log1p(-mp.mpf(l.numerator)/l.denominator)-mp.mpf(l.numerator)/l.denominator
                self.assertGreaterEqual(mp.mpf(upper.numerator)/upper.denominator, exact)

    def test_direct_log_fixture(self):
        center, lower, upper = reference()
        self.assertTrue(0 < lower < center < upper < 1)
        self.assertLess(float(upper-lower), .04)


if __name__ == '__main__':
    print('Direct-log reference:', *(mp.nstr(x,80) for x in reference()))
    unittest.main()
