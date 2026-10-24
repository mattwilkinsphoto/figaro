"""Independent objective differences and bounded failure controls for GVM-07."""
import copy
import unittest
from unittest.mock import patch
import numpy as np
import gvm_dimension_controls as c
import gvm_wholefit_math as m
import gvm_wholefit_fit as f


class WholeFitTest(unittest.TestCase):
    def setUp(self):
        _, self.model = c.truth(2, 'seam')
        self.data = c.sample_gvm(np.random.Generator(np.random.PCG64DXSM(350001)), self.model, 160)

    def test_every_physical_derivative_against_objective_differences(self):
        _, _, grad = m.derivatives(self.data, self.model)
        def objective(model): return -float(c.gvm_log(self.data, model).mean())
        h = 2e-5; errors = []
        for j, component in enumerate(self.model['components']):
            for key in ('weight', 'alpha', 'kappa', 'mean', 'coeff', 'cov'):
                array = np.asarray(component[key])
                for index in np.ndindex(array.shape):
                    if key == 'cov' and index[0] > index[1]: continue
                    plus, minus = copy.deepcopy(self.model), copy.deepcopy(self.model)
                    p, q = array.copy(), array.copy(); p[index] += h; q[index] -= h
                    factor = 1
                    if key == 'cov' and index[0] != index[1]:
                        p[index[::-1]] += h; q[index[::-1]] -= h; factor = 2
                    plus['components'][j][key] = p.tolist()
                    minus['components'][j][key] = q.tolist()
                    estimate = (objective(plus)-objective(minus))/(2*h)
                    errors.append(abs(estimate-factor*np.asarray(grad[j][key])[index]))
        self.assertLess(max(errors), 2e-7)

    def test_projection_kkt_and_covariance_boundaries(self):
        for values in ([5., -2., 3.], [-1., -1., -1.]):
            result = m.simplex(values); self.assertAlmostEqual(result.sum(), 1.)
            self.assertTrue(np.all(result >= 1e-8))
            active = result > 1e-8+1e-12
            dual = np.asarray(values)-result
            self.assertLess(np.ptp(dual[active]), 1e-12)
            if np.any(~active): self.assertLessEqual(max(dual[~active]), min(dual[active])+1e-12)
        self.assertTrue(np.allclose(m.covariance_project(np.diag([-1., 30.])), np.diag([.01, 25.])))
        self.assertTrue(np.allclose(m.covariance_project(np.diag([.01, 25.])), np.diag([.01, 25.])))
        with self.assertRaises(ValueError): m.simplex([np.nan])

    def test_uniform_phase_and_roundtrip(self):
        model = copy.deepcopy(self.model)
        for a in model['components']: a['kappa'] = 0.
        _, _, gradients = m.derivatives(self.data, model)
        for a in gradients:
            self.assertEqual(a['alpha'], 0.); self.assertTrue(np.all(a['coeff'] == 0.))
        restored = m.unpack(m.pack(model), 2, 2)
        self.assertTrue(np.allclose(c.gvm_log(self.data, model), c.gvm_log(self.data, restored)))

    def test_one_sided_concentration_derivative_at_zero(self):
        model=copy.deepcopy(self.model);model['components'][0]['kappa']=0.
        _,_,gradient=m.derivatives(self.data,model);h=1e-5
        values=[]
        for offset in (0,h,2*h):
            candidate=copy.deepcopy(model);candidate['components'][0]['kappa']=offset
            values.append(-float(c.gvm_log(self.data,candidate).mean()))
        self.assertAlmostEqual((-3*values[0]+4*values[1]-values[2])/(2*h),gradient[0]['kappa'],places=7)

    def test_squared_second_difference_and_linear_contraction(self):
        base = copy.deepcopy(self.model); first = copy.deepcopy(base); second = copy.deepcopy(base)
        # A single unconstrained mean coordinate follows F(x)=0.5*x.
        base['components'][0]['mean'][0] = 2.
        first['components'][0]['mean'][0] = 1.
        second['components'][0]['mean'][0] = .5
        result, alpha = f.extrapolate(base, first, second, 2, 2)
        self.assertAlmostEqual(alpha, -2.)
        self.assertAlmostEqual(result['components'][0]['mean'][0], 0.)
        r = m.difference(first, base, 2, 2)
        v = m.difference(second, first, 2, 2)-r
        self.assertTrue(np.allclose(m.pack(base)+2*r+v, m.pack(second)))
        self.assertEqual(f.extrapolate(base, base, base, 2, 2)[1], -1.)

    def test_circle_difference_crosses_seam_locally(self):
        base, fresh = copy.deepcopy(self.model), copy.deepcopy(self.model)
        base['components'][0]['alpha'] = np.pi-.01
        fresh['components'][0]['alpha'] = -np.pi+.01
        self.assertAlmostEqual(m.difference(fresh, base, 2, 2)[7], .02)

    def test_work_map_time_rank_and_input_failures(self):
        result = f.fit(self.data, 2, 9, max_work=0)
        self.assertEqual(result['status'], 'WorkBudgetExhausted')
        self.assertEqual(result['totalWork'], 0)
        result = f.fit(self.data, 2, 9, seconds=1e-12)
        self.assertEqual(result['status'], 'TimeBudgetExhausted')
        result = f.fit(self.data, 2, 9, maps=1, starts=1)
        self.assertLessEqual(result['starts'][0]['maps'], 1)
        self.assertNotEqual(result['status'], 'Stationary')
        rank = self.data.copy(); rank[:, 0] = 0.
        self.assertEqual(f.fit(rank, 1, 9)['status'], 'RankDeficient')
        with self.assertRaises(ValueError): f.fit(self.data, 4, 9)
        with self.assertRaises(ValueError): f.fit(self.data, 1, 9, seconds=float('nan'))

    def test_exact_retained_model_score_and_refresh(self):
        result = f.fit(self.data, 2, 9, maps=4, method='squarem', starts=1)
        self.assertAlmostEqual(result['trainScore'], float(c.gvm_log(self.data, result).sum()), places=8)
        self.assertEqual(result['diagnostic'], m.stationarity(self.data, result))
        for start in result['starts']:
            self.assertTrue(np.all(np.diff(start['trace']) >= -1e-10*len(self.data)))

    def test_rejected_extrapolation_is_charged_and_falls_back(self):
        bad = copy.deepcopy(self.model)
        for a in bad['components']: a['mean'] = [100., 100.]
        with patch.object(f, 'extrapolate', return_value=(bad, -4.)):
            result = f.fit(self.data, 2, 9, maps=4, method='squarem', starts=1)
        self.assertGreater(result['starts'][0]['rejected'], 0)
        self.assertAlmostEqual(result['trainScore'], float(c.gvm_log(self.data, result).sum()), places=8)
        self.assertLessEqual(result['totalWork'], 20000000)


if __name__ == '__main__': unittest.main(verbosity=2)
