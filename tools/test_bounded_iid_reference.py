"""Independent rational proof checks and high-precision bounded-reliability oracles."""
from fractions import Fraction as F
import unittest
import mpmath as mp


class BoundedReferenceTest(unittest.TestCase):
    def test_log_two_rational_upper(self):
        # log(2) = 2*atanh(1/3). Bound the omitted positive series by a geometric tail.
        terms = 20
        partial = 2 * sum((F(1, 3) ** (2*j+1) / (2*j+1) for j in range(terms)), F(0))
        tail = 2 * F(1, 3) ** (2*terms+1) / ((2*terms+1) * (1-F(1, 9)))
        upper = F('0.6931471805599454')
        self.assertLess(partial + tail, upper)

    def test_dyadic_radius_dominates_hoeffding(self):
        with mp.workdps(100):
            for n in (1, 2, 37, 100, 1000, 1000000):
                for alpha in (1e-12, .05, .5):
                    numerator = F(2*n*(n+1))
                    power = F(alpha)
                    k = 0
                    while power < numerator:
                        power *= 2
                        k += 1
                    exact = mp.sqrt(mp.log(2*n*(n+1)/mp.mpf(alpha))/(2*n))
                    conservative = mp.sqrt(k*mp.mpf('0.6931471805599454')/(2*n))
                    self.assertGreater(conservative, exact)
                    self.assertLess(conservative / exact, mp.sqrt(2))

    def test_region_reference(self):
        with mp.workdps(100):
            p = 2*(1-mp.mpf(.1))**100
            self.assertLess(abs(p-mp.mpf('0.000053122797775174921102115585237569325268667999692132816352051625594089452668828535')),mp.mpf('1e-82'))
            self.assertGreater(mp.power(.5, 2000), 0)
            self.assertLess(mp.power(.5, 2000), mp.mpf(float.fromhex('0x0.0000000000001p-1022')))


if __name__ == '__main__':
    unittest.main()
