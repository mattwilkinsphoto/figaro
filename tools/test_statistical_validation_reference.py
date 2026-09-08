"""Independent posterior controls and convergence checks; no stochastic pass/fail."""
import unittest
import numpy as np
from statistical_validation_reference import observations, posterior


class StatisticalValidationReferenceTest(unittest.TestCase):
    def test_fixed_data(self):
        gamma, dirichlet = observations()
        self.assertEqual(gamma.shape, (200,))
        self.assertEqual(dirichlet.shape, (200, 3))
        self.assertTrue(np.all(gamma > 0))
        self.assertTrue(np.all(dirichlet > 0))
        np.testing.assert_allclose(dirichlet.sum(axis=1), 1, rtol=0, atol=1e-15)
        self.assertAlmostEqual(float(gamma.sum()), 767.2000858136155, places=10)

    def test_complete_prior_box_posterior_controls(self):
        controls = {
            'gamma': [2.107869973169636, 1.844729346924452],
            'dirichlet': [0.841016150722901, 1.910950755236635, 2.640339278723853],
        }
        for family, expected in controls.items():
            coarse_mean, coarse_sd = posterior(family, 144)
            fine_mean, fine_sd = posterior(family, 216)
            np.testing.assert_allclose(fine_mean, expected, rtol=0, atol=1e-10)
            np.testing.assert_allclose(coarse_mean, fine_mean, rtol=0, atol=1e-8)
            np.testing.assert_allclose(coarse_sd, fine_sd, rtol=0, atol=1e-8)
            self.assertTrue(np.all(fine_sd > 0))


if __name__ == '__main__':
    unittest.main()
