"""Small deterministic tests for the independent continuous benchmark reference."""
import unittest
import numpy as np
from continuous_filter_reference import fixture, grid_filter, normal, self_test, simpson_uniform


class ContinuousReferenceTest(unittest.TestCase):
    def test_gaussian_and_mixture_controls(self):
        self.assertEqual(self_test()["linear_gaussian_steps"], 5)

    def test_fixed_input_generation_is_replayable(self):
        self.assertEqual(fixture(), fixture())
        self.assertEqual(sum(len(d["rows"]) for d in fixture().values()), 60)

    def test_symmetry_and_absolute_moment_integration(self):
        x=np.linspace(-10,10,1601)
        density=normal(x,0,1)
        value=simpson_uniform(-x[:801]*density[:801],.0125)+simpson_uniform(x[800:]*density[800:],.0125)
        self.assertAlmostEqual(value,np.sqrt(2/np.pi),places=8)
        rows=grid_filter("persistent-modes",[4.,3.5,4.5],15,.025)
        for row in rows:
            self.assertAlmostEqual(row["positive"],.5,places=10)
            self.assertAlmostEqual(row["mean"],0,places=10)

    def test_simpson_rejects_incompatible_grid(self):
        with self.assertRaises(AssertionError):
            simpson_uniform(np.ones(10),.1)


if __name__ == "__main__": unittest.main()
