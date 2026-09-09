"""Independent 60-digit identities for observation, mixed and copula contracts.

Research-only mpmath; no Figaro code or copied third-party implementation.
"""
import unittest
import mpmath as m


class ModelingReference(unittest.TestCase):
    def setUp(self):
        m.mp.dps = 60

    def test_extreme_interval(self):
        value = m.log((m.erfc(40 / m.sqrt(2)) - m.erfc(41 / m.sqrt(2))) / 2)
        self.assertAlmostEqual(float(value), -804.6084420137538, places=11)

    def test_gaussian_copula(self):
        r = m.mpf('.6')
        square = (2 + 2 * r) / (1 - r * r)
        value = -m.log(2 * m.pi) - m.log(1-r*r)/2 - square/2
        self.assertAlmostEqual(float(value), -4.1147335150951357, places=13)
        self.assertAlmostEqual(float(-m.log(1-r*r)/2), .22314355131420976, places=14)

    def test_student_copula(self):
        nu, r = m.mpf(5), m.mpf('.6')
        square = (2 + 2*r)/(1-r*r)
        value = (m.loggamma((nu+2)/2)-m.loggamma(nu/2)-m.log(nu*m.pi)
                 -m.log(1-r*r)/2-(nu+2)/2*m.log1p(square/nu))
        self.assertAlmostEqual(float(value), -4.0407486470549443, places=13)

    def test_mixed_law_decomposition(self):
        p, q = m.mpf('.2'), m.mpf('.6')
        affinity = m.sqrt(p*q) + m.sqrt((1-p)*(1-q))
        kl = p*m.log(p/q)+(1-p)*m.log((1-p)/(1-q))
        self.assertGreater(kl, 0)
        self.assertGreater(-m.log(affinity), 0)
        # Continuous mass and spike together normalize; the density at the spike
        # contributes no continuous probability to that singleton.
        slab_mass = m.quad(lambda x: (1-p)*m.exp(-x*x/2)/m.sqrt(2*m.pi), [-m.inf, 0, m.inf])
        self.assertLess(abs(p+slab_mass-1), m.mpf('1e-50'))


if __name__ == '__main__':
    unittest.main()
