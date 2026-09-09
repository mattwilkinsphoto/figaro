import unittest
from mixture_fit_reference import reference

class MixtureReferenceTest(unittest.TestCase):
    def test_independent_matrix_oracle(self):
        r=reference()
        self.assertEqual(r['iterations'],7)
        self.assertAlmostEqual(r['averageLogDensity'],-1.9732677597299386,places=12)
        self.assertAlmostEqual(sum(r['weights']),1,places=14)
        for c in r['covariance']:
            self.assertGreater(c[0][0]*c[1][1]-c[0][1]*c[1][0],0)

if __name__=='__main__': unittest.main()
