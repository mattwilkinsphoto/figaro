"""Independent stage-one controls for GVM-05 (no candidate fitter imported)."""
import math
import unittest
import mpmath as mp
import numpy as np
from scipy.optimize import minimize
from scipy.special import i0e, i1e
import gvm_dimension_controls as c


class ReferenceControls(unittest.TestCase):
    def test_winding_density_and_moments_against_high_precision(self):
        with mp.workdps(70):
            for variance in (.01, .3, 2., 10., 25.):
                for angle in (-math.pi, -1., 0., math.pi-1e-12):
                    mean = 2.7
                    values, logs, result, bound = c.winding_terms(np.array([angle]), np.array([mean]), variance)
                    exact = []
                    for k in range(-80, 81):
                        y = mp.mpf(angle) + 2*mp.pi*k
                        p = mp.exp(-(y-mean)**2/(2*variance))/mp.sqrt(2*mp.pi*variance)
                        exact.append((y, p))
                    total = mp.fsum(p for _, p in exact)
                    self.assertLess(abs(result[0]-float(mp.log(total))), 1e-8)
                    weights = np.exp(logs[0]-result[0])
                    for power in (1, 2):
                        moment = float(mp.fsum(y**power*p for y, p in exact)/total)
                        self.assertLess(abs(np.sum(weights*values[0]**power)-moment), 1e-8)
                    self.assertLessEqual(bound, 1e-12)

    def test_winding_failure_and_seam(self):
        with self.assertRaises(ArithmeticError):
            c.winding_terms(np.array([0.]), np.array([1.]), 25., max_images=0)
        with self.assertRaises(ValueError):
            c.winding_terms(np.array([0.]), np.array([0.]), -1.)
        for shift in (-4, 0, 3):
            value = c.winding_terms(np.array([.3+shift*c.TAU]), np.array([2.]), 5.)[2][0]
            reference = c.winding_terms(np.array([.3]), np.array([2.]), 5.)[2][0]
            self.assertAlmostEqual(value, reference, places=12)

    def test_gvm_density_high_precision(self):
        with mp.workdps(60):
            for d in (1, 2, 3):
                _, model = c.truth(d, 'curved'); comp = model['components'][0]
                mu = mp.matrix(comp['mean']); p = mp.matrix(comp['cov']); inv = p**-1
                for scale in (0., .2, 2., 8.):
                    x = np.arange(1, d+1)*scale; angle = 3.1
                    delta = mp.matrix(x.tolist())-mu
                    normal = -mp.mpf('.5')*(d*mp.log(2*mp.pi)+mp.log(mp.det(p))+(delta.T*inv*delta)[0])
                    phase = mp.mpf(comp['alpha'])
                    for i in range(d):
                        phase += mp.mpf(comp['coeff'][i])*x[i]
                    q = d
                    for i in range(d):
                        for j in range(i, d):
                            phase += (mp.mpf('.5') if i == j else 1)*comp['coeff'][q]*x[i]*x[j]; q += 1
                    k = comp['kappa']
                    expected = normal+k*mp.cos(mp.mpf(angle)-phase)-mp.log(2*mp.pi*mp.besseli(0, k))
                    self.assertLess(abs(c.gvm_log(np.array([np.r_[x, angle]]), model)[0]-float(expected)), 1e-8)

    def test_coordinate_export_cross_terms_and_gradient(self):
        rng = np.random.default_rng(91803)
        for d in (1, 2, 3):
            x = rng.normal(size=(80, d)); q = d+d*(d+1)//2
            coeff = rng.uniform(-.6, .6, q); matrix = c.matrix_from_coeff(coeff, d)
            b = rng.normal(size=(d, d)); a = rng.normal(size=d)
            y = a + x @ b.T
            intercept = .4+coeff[:d]@a+.5*a@matrix@a
            beta = b.T @ (coeff[:d]+matrix@a); gamma = b.T@matrix@b
            original = .4+c.phase_features(y)@coeff
            exported = intercept+x@beta+.5*np.einsum('ni,ij,nj->n', x, gamma, x)
            np.testing.assert_allclose(original, exported, rtol=1e-12, atol=1e-12)
            theta = rng.uniform(-math.pi, math.pi, len(x)); w = rng.uniform(.1, 1., len(x))
            features = c.phase_features(x)
            _, derivative = c.resultant_objective(coeff, features, theta, w)
            for j in range(q):
                h = np.zeros(q); h[j] = 1e-6
                fd = (c.resultant_objective(coeff+h, features, theta, w)[0]-c.resultant_objective(coeff-h, features, theta, w)[0])/2e-6
                self.assertLess(abs(fd-derivative[j]), 1e-8)

    def test_normal_special_case_and_jacobian(self):
        rng = np.random.default_rng(92071)
        for d in (2, 3):
            _, model = c.truth(d, 'curved'); model['components'][0]['kappa'] = 0.
            data = rng.normal(size=(60, d+1)); comp = model['components'][0]
            np.testing.assert_allclose(c.gvm_log(data, model),
                c.normal_log(data[:, :-1], np.array(comp['mean']), np.array(comp['cov']))-math.log(c.TAU), atol=1e-12)
            center, lower = c.whitening(data); z = c.transform(data, center, lower)
            expected = c.normal_log(z[:, :-1], np.zeros(d), np.eye(d))-np.log(np.diag(lower)).sum()
            np.testing.assert_allclose(expected, c.normal_log(data[:, :-1], center, lower@lower.T), atol=1e-12)

    def test_gaussian_mle_and_wrapped_likelihood(self):
        rng = np.random.default_rng(92519)
        for d in (2, 3):
            _, model = c.truth(d, 'local'); data = c.sample_gaussian(rng, model, 400)
            ordinary = c.fit_gaussian(data, 1, False, 12, starts=1)
            self.assertEqual(ordinary['status'], 'Converged')
            np.testing.assert_allclose(ordinary['components'][0]['mean'], data.mean(axis=0), atol=1e-10)
            np.testing.assert_allclose(ordinary['components'][0]['cov'], np.cov(data.T, bias=True), atol=1e-10)
        _, model = c.truth(2, 'wrapped'); data = c.sample_gaussian(rng, model, 600)
        fitted = c.fit_gaussian(data, 2, True, 13)
        self.assertNotIn(fitted['status'], ('Stalled', 'InsufficientComponent'))
        self.assertTrue(np.isfinite(c.gaussian_log(data, fitted)).all())
        for record in fitted['starts']:
            self.assertTrue(np.all(np.diff(record['trace']) >= -6e-6))
            self.assertLessEqual(record['maximumRelativeWindingTail'], 1e-12)

    def test_wrapped_em_against_independent_direct_optimizer(self):
        # Different parameterization/objective evaluation: explicit joint normal
        # images, not the conditional-normal helper used by EM.
        rng = np.random.default_rng(93211)
        covariance = np.array([[1., .45], [.45, .6]])
        data = rng.multivariate_normal([.2, 3.], covariance, 240); data[:, 1] = c.wrap(data[:, 1])
        fitted = c.fit_gaussian(data, 1, True, 17, starts=1)
        def direct(v):
            lower = np.array([[math.exp(v[2]), 0.], [v[3], math.exp(v[4])]])
            inverse = np.linalg.inv(lower); parts = []
            for winding in range(-6, 7):
                points = data-np.array(v[:2]); points[:, 1] += c.TAU*winding
                z = points @ inverse.T
                parts.append(-math.log(c.TAU)-v[2]-v[4]-.5*np.sum(z*z, axis=1))
            from scipy.special import logsumexp
            return -float(logsumexp(parts, axis=0).sum())
        start = [.2, 3., 0., .45, math.log(math.sqrt(.6-.45**2))]
        reference = minimize(direct, start, method='BFGS', options={'gtol': 1e-5, 'maxiter': 150})
        self.assertLess(abs(-reference.fun-c.gaussian_log(data, fitted).sum()), 1e-5)

    def test_sampler_moments_and_refusals(self):
        rng = np.random.default_rng(94019); _, model = c.truth(2, 'curved')
        data = c.sample_gvm(rng, model, 40000); comp = model['components'][0]
        residual = data[:, -1]-comp['alpha']-c.phase_features(data[:, :-1])@comp['coeff']
        self.assertLess(abs(np.cos(residual).mean()-i1e(8.)/i0e(8.)), .004)
        np.testing.assert_allclose(data[:, :-1].mean(axis=0), comp['mean'], atol=.02)
        self.assertEqual(c.fit_gaussian(np.zeros((100, 3)), 1, True, 1)['status'], 'RankDeficient')
        with self.assertRaises(ValueError):
            c.fit_gaussian(np.full((100, 3), np.nan), 1, True, 1)
        _, gaussian = c.truth(2, 'local'); sample = c.sample_gaussian(rng, gaussian, 100)
        self.assertEqual(c.fit_gaussian(sample, 1, True, 1, max_seconds=-1)['status'], 'TimeBudgetExhausted')


if __name__ == '__main__':
    unittest.main(verbosity=2)
