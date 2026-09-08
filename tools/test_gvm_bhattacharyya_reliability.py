"""Optional high-precision reliability controls; mpmath==1.3.0, no file writes."""
import math
from pathlib import Path
import unittest
import mpmath as mp
from gvm_bhattacharyya_reliability import comparison, oracle, scala_source, specifications


class ReliabilityOracleTest(unittest.TestCase):
    def test_complete_fixed_matrix_and_scala_fixture_freshness(self):
        specs = specifications()
        self.assertEqual(len(specs),96)
        self.assertEqual(len(set(specs)),96)
        root = Path(__file__).resolve().parents[1]
        path = root/'Figaro/src/test/scala/com/cra/figaro/test/modernization/GvmBhattacharyyaReliabilityFixtures.scala'
        self.assertEqual(path.read_text(encoding='utf-8'),scala_source())

    def test_precision_and_truncation_refinement_across_the_whole_matrix(self):
        with mp.workdps(110):
            for spec in specifications():
                with self.subTest(spec=spec):
                    self.assertLess(abs(oracle(spec,80,128)-oracle(spec,110,192)),mp.mpf('1e-48'))

    def test_positive_integration_controls_include_tiny_opposed_affinities(self):
        specs = [('linear',1,k,math.pi,b,0.) for k,b in
                 [(10.,1e-5),(25.,.1),(50.,1e-5),(50.,.1),(50.,1.)]]
        specs += [('unequal',1,50.,0.,0.,.5)]
        with mp.workdps(60):
            for spec in specs:
                with self.subTest(spec=spec):
                    c = comparison(spec)
                    # Positive integration of the original conditional centers under the
                    # Gaussian overlap measure; no Fourier coefficients or characteristic.
                    value = mp.quad(lambda z: mp.exp(-z*z/2)*c.angular_affinity(c.mean+c.factor*z),
                                    [-16,-8,-4,-2,0,2,4,8,16])/mp.sqrt(2*mp.pi)
                    distance = c.gaussian_distance-mp.log(value)
                    self.assertLess(abs(distance-oracle(spec)),mp.mpf('1e-25'))
                    # Omitted standard-Gaussian probability bounds omitted affinity mass.
                    self.assertLess(mp.erfc(16/mp.sqrt(2))/value,mp.mpf('1e-30'))

    def test_two_dimensional_curvature_against_positive_tensor(self):
        spec = ('dimension',2,10.,0.,.2,.2)
        with mp.workdps(60):
            c = comparison(spec)
            distance = -mp.log(c.quadrature(64))
            self.assertLess(abs(distance-oracle(spec)),mp.mpf('1e-12'))


if __name__ == '__main__': unittest.main()
