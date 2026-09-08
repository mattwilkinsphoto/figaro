"""Research acceptance for positive scalar integration; optional mpmath==1.3.0."""
import math
import unittest
import mpmath as mp
from gvm_bhattacharyya_positive import compare_phase, log_i0
from gvm_bhattacharyya_reliability import comparison, oracle, specifications
from gvm_bhattacharyya_research import Comparison, scalar


class PositiveIntegrationTest(unittest.TestCase):
    def checked(self,args,expected,tolerance=1e-8):
        result = compare_phase(*args,tolerance=tolerance)
        self.assertEqual(result.status,'Estimated',result)
        self.assertLessEqual(abs(result.distance-expected),tolerance)
        lo,hi = result.estimated_interval
        self.assertLessEqual(lo,expected+8*math.ulp(expected))
        self.assertGreaterEqual(hi,expected-8*math.ulp(expected))
        self.assertLessEqual(max(result.distance-lo,hi-result.distance),tolerance)
        self.assertLessEqual(result.evaluations,50000)
        return result

    def args(self,c):
        return tuple(float(x) for x in (c.gaussian_distance,c.constant,c.linear[0],c.quadratic[0],c.p.kappa,c.q.kappa))

    def test_all_84_scalar_reliability_fixtures(self):
        count = 0
        for spec in specifications():
            if spec[1] != 1: continue
            with mp.workdps(80):
                c = comparison(spec)
                expected = float(oracle(spec))
                args = self.args(c)
            with self.subTest(spec=spec): self.checked(args,expected)
            count += 1
        self.assertEqual(count,84)

    def test_unequal_concentrations_and_phase_sign_symmetry(self):
        with mp.workdps(80):
            for kp in (0,.125,1,10,25):
                for alpha,beta,gamma in [(mp.pi,mp.mpf('1e-5'),0),('.5','.1','.2')]:
                    c = Comparison(scalar(kappa=kp),scalar(alpha=alpha,beta=beta,gamma=gamma,kappa=50))
                    value,_ = c.series(128)
                    expected = float(-mp.log(value))
                    args = self.args(c)
                    forward = self.checked(args,expected)
                    reverse = self.checked((args[0],-args[1],-args[2],-args[3],args[5],args[4]),expected)
                    self.assertAlmostEqual(forward.distance,reverse.distance,places=13)

    def test_tail_control_scales_with_tiny_affinity(self):
        with mp.workdps(80):
            spec = ('linear',1,50.,math.pi,1e-5,0.)
            expected = float(oracle(spec))
            result = self.checked(self.args(comparison(spec)),expected)
            self.assertEqual(result.radius,12)
            self.assertLess(result.gaussian_tail_bound/math.exp(-expected),1e-9)
            # A familiar fixed 8-sigma cutoff has a worst-case tail bound far larger
            # than this entire affinity. It cannot certify relative overlap accuracy.
            self.assertGreater(math.erfc(8/math.sqrt(2))/math.exp(-expected),1e5)

    def test_budget_preflight_and_refinement_never_return_partial_distance(self):
        for budget in (5,320,500):
            result = compare_phase(0,math.pi,1e-5,0,50,50,max_evaluations=budget)
            self.assertEqual(result.status,'BudgetExhausted')
            self.assertIsNone(result.distance)
            self.assertLessEqual(result.evaluations,budget)
        # Oscillation that could alias on a coarse grid is split before evaluation.
        result = compare_phase(0,0,2*math.pi*64,0,50,50,max_evaluations=500)
        self.assertEqual(result.status,'BudgetExhausted')
        self.assertEqual(result.evaluations,0)

    def test_tight_precision_and_range_refusals(self):
        result = compare_phase(0,math.pi,1e-5,0,50,50,tolerance=1e-14)
        self.assertEqual(result.status,'NumericallyUnresolved')
        self.assertIsNone(result.distance)
        for args in [(0,0,0,0,51,50),(0,0,10001,0,1,1),(10001,0,0,0,1,1)]:
            result = compare_phase(*args)
            self.assertEqual(result.status,'UnsupportedRange')
            self.assertIsNone(result.distance)
            self.assertEqual(result.evaluations,0)

    def test_cancellation_at_entry_and_during_work(self):
        with self.assertRaises(InterruptedError): compare_phase(0,0,1,0,50,50,cancelled=lambda: True)
        calls = 0
        def cancelled():
            nonlocal calls
            calls += 1
            return calls > 200
        with self.assertRaises(InterruptedError): compare_phase(0,0,1,0,50,50,cancelled=cancelled)
        self.assertEqual(calls,201)

    def test_invalid_arguments_and_bessel_boundary_accuracy(self):
        for tolerance in (0,-1,math.nan,math.inf):
            with self.assertRaises(ValueError): compare_phase(0,0,0,0,1,1,tolerance=tolerance)
        for budget in (4,200001,1.5,True):
            with self.assertRaises(ValueError): compare_phase(0,0,0,0,1,1,max_evaluations=budget)
        with self.assertRaises(ValueError): compare_phase(-1,0,0,0,1,1)
        with self.assertRaises(ValueError): compare_phase(0,math.inf,0,0,1,1)
        with self.assertRaises(ValueError): compare_phase(0,0,0,0,1,1,cancelled=1)
        with mp.workdps(80):
            for k in (0,1e-12,.125,1,10,25,50):
                self.assertLess(abs(log_i0(k)-float(mp.log(mp.besseli(0,k)))),2e-14)


if __name__ == '__main__': unittest.main()
