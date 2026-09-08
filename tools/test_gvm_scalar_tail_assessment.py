"""Research checks for cell-minimum lower bounds; not a production fallback policy."""
import math
import unittest
from pathlib import Path
import mpmath as mp
from gvm_bhattacharyya_reliability import comparison, specifications
from gvm_bhattacharyya_research import Comparison, scalar
from gvm_scalar_tail_assessment import assess, cell_lower_bound, phase_range, radius_for, report


class ScalarTailAssessmentTest(unittest.TestCase):
    def test_checked_in_report_is_complete_and_fresh(self):
        path = Path(__file__).resolve().parents[1]/'docs/GVM_SCALAR_TAIL_ASSESSMENT_RUNS.txt'
        self.assertEqual(path.read_text(encoding='utf-8'), report())

    def test_complete_grid_has_valid_bounds_and_no_larger_radius(self):
        rows = [assess(spec) for spec in specifications() if spec[1] == 1]
        self.assertEqual(len(rows), 84)
        for row in rows:
            self.assertLessEqual(row['candidateRadius'], row['oldRadius'])
            self.assertLessEqual(row['tailOverAffinity'], 1e-8/16)

    def test_opposed_weak_coupling_keeps_large_radius(self):
        row = assess(('linear', 1, 50., math.pi, 1e-5, 0.))
        self.assertEqual((row['oldRadius'], row['candidateRadius']), (12, 12))

    def test_curvature_can_use_a_tighter_bound_without_an_oracle_selected_radius(self):
        row = assess(('unequal', 1, 50., 0., 0., 2.))
        self.assertLess(row['candidateRadius'], row['oldRadius'])
        self.assertLess(row['candidatePanels'], row['oldPanels'])

    def test_bound_refines_monotonically_and_handles_phase_extrema(self):
        with mp.workdps(80):
            c = comparison(('unequal', 1, 50., 0., 0., 2.))
            bounds = [cell_lower_bound(c, n) for n in (1, 2, 4, 8, 16, 32, 64)]
            self.assertTrue(all(a <= b for a, b in zip(bounds, bounds[1:])))
            lo, hi = phase_range(c, mp.mpf(-4), mp.mpf(4))
            for i in range(101):
                z = mp.mpf(-4)+mp.mpf(8)*i/100
                phase = c.constant+c.linear[0]*z+c.quadratic[0]*z*z/2
                self.assertLessEqual(lo, phase)
                self.assertGreaterEqual(hi, phase)

    def test_invalid_configuration(self):
        with mp.workdps(80):
            c = comparison(('linear', 1, 1., 0., .1, 0.))
            for n in (0, 1025, True, 1.5):
                with self.assertRaises(ValueError): cell_lower_bound(c, n)
            for bound in (0, -1, mp.inf, mp.nan, 2):
                with self.assertRaises(ValueError): radius_for(bound)

    def test_unequal_concentrations_and_phase_reversal(self):
        with mp.workdps(80):
            for k in (0, .125, 1, 10, 25):
                for alpha, beta, gamma in ((mp.pi, mp.mpf('1e-5'), 0), (.5, .1, .2)):
                    p, q = scalar(kappa=k), scalar(alpha=alpha, beta=beta, gamma=gamma, kappa=50)
                    forward, reverse = Comparison(p, q), Comparison(q, p)
                    lower, reversed_lower = cell_lower_bound(forward), cell_lower_bound(reverse)
                    value, tail = forward.series(128)
                    self.assertLessEqual(lower, value+tail+mp.mpf('1e-70'))
                    self.assertLessEqual(abs(lower-reversed_lower), mp.mpf('1e-70'))


if __name__ == '__main__':
    unittest.main()
