"""GVM-05 independent Python references and soft-winding Gaussian controls.

Experiment tooling only. No Figaro inference, sampler or public defaults change.
The wrapped EM is independently specialized from the latent-winding likelihood;
no external implementation source is copied. See the protocol/reference index.
"""
import math
import time
import numpy as np
from scipy.linalg import solve_triangular
from scipy.special import i0e, logsumexp

TAU = 2 * math.pi


def wrap(a):
    return (np.asarray(a) + math.pi) % TAU - math.pi


def normal_log(x, mu, cov):
    lower = np.linalg.cholesky(cov)
    z = solve_triangular(lower, (x - mu).T, lower=True).T
    return -.5 * (len(mu) * math.log(TAU) + np.sum(z*z, axis=1)) - np.log(np.diag(lower)).sum()


def phase_features(x):
    d = x.shape[1]
    return np.column_stack([x] + [(.5 if i == j else 1.) * x[:, i] * x[:, j]
                                  for i in range(d) for j in range(i, d)])


def matrix_from_coeff(coeff, d):
    matrix = np.zeros((d, d)); index = d
    for i in range(d):
        for j in range(i, d):
            matrix[i, j] = matrix[j, i] = coeff[index]; index += 1
    return matrix


def resultant_objective(coeff, features, angles, weights):
    weights = weights / weights.sum()
    residual = angles - features @ coeff
    c = np.sum(weights * np.cos(residual)); s = np.sum(weights * np.sin(residual))
    gradient = -2 * (features.T @ (weights * (c*np.sin(residual) - s*np.cos(residual))))
    return -(c*c+s*s), gradient


def gvm_log(data, model):
    features = phase_features(data[:, :-1]); terms = []
    for c in model['components']:
        k = c['kappa']
        terms.append(math.log(c['weight']) + normal_log(data[:, :-1], np.array(c['mean']), np.array(c['cov']))
                     + k*np.cos(data[:, -1] - c['alpha'] - features @ np.array(c['coeff']))
                     - math.log(TAU) - (math.log(float(i0e(k))) + k))
    return logsumexp(np.array(terms), axis=0)


def winding_terms(angle, mean, variance, tolerance=1e-12, max_images=64):
    """Conditional Gaussian images and a geometric upper bound on omitted density.

    The largest first omitted term uses distance 2pi(M+1)-pi. Later ratios
    decrease, so twice its geometric series bounds both tails for every point.
    Compare to the actual included sum; never infer log-density error from mass.
    """
    if not np.isfinite(variance) or variance <= 0 or not np.all(np.isfinite(mean)):
        raise ValueError('Invalid conditional normal')
    center = np.rint((mean-angle) / TAU)
    for m in range(max_images+1):
        values = angle[:, None] + TAU * (center[:, None] + np.arange(-m, m+1))
        logs = -.5*((values-mean[:, None])**2/variance + math.log(TAU*variance))
        included = logsumexp(logs, axis=1)
        distance = TAU*(m+1)-math.pi
        log_ratio = -(2*TAU*distance+TAU*TAU)/(2*variance)
        log_tail = math.log(2)-.5*(distance*distance/variance+math.log(TAU*variance))-math.log(-math.expm1(log_ratio))
        bound = float(np.max(np.exp(np.minimum(700, log_tail-included))))
        if bound <= tolerance:
            return values, logs, included, bound
    raise ArithmeticError('WindingBudgetExhausted')


def gaussian_parts(data, component, wrapped):
    mu, cov = np.array(component['mean']), np.array(component['cov'])
    if not wrapped:
        return data[:, -1, None], normal_log(data, mu, cov)[:, None], 0.
    d = len(mu)-1
    regression = np.linalg.solve(cov[:d, :d], cov[:d, d])
    conditional_mean = mu[d] + (data[:, :d]-mu[:d]) @ regression
    conditional_variance = float(cov[d, d]-cov[d, :d] @ regression)
    values, angular_logs, _, bound = winding_terms(data[:, d], conditional_mean, conditional_variance)
    logs = normal_log(data[:, :d], mu[:d], cov[:d, :d])[:, None] + angular_logs
    return values, logs, bound


def gaussian_log(data, model, wrapped=True):
    terms = [math.log(c['weight']) + logsumexp(gaussian_parts(data, c, wrapped)[1], axis=1)
             for c in model['components']]
    return logsumexp(np.array(terms), axis=0)


def bounded_covariance(cov):
    eigen, vectors = np.linalg.eigh((cov+cov.T)/2)
    return (vectors*np.clip(eigen, .01, 25.)) @ vectors.T, int(np.sum((eigen < .01) | (eigen > 25.)))


def partitions(data, k, start, seed):
    rng = np.random.Generator(np.random.PCG64DXSM(seed))
    key = data[:, 0] if start == 0 else (wrap(data[:, -1]) if start == 1 else rng.random(len(data)))
    labels = np.empty(len(data), dtype=int)
    labels[np.argsort(key, kind='stable')] = np.minimum(k-1, np.arange(len(data))*k//len(data))
    return labels


def fit_gaussian(data, k, wrapped, seed, starts=3, iterations=50, max_seconds=120):
    if data.ndim != 2 or not 2 <= data.shape[1] <= 4 or len(data) < 30 or not np.all(np.isfinite(data)):
        raise ValueError('Invalid data')
    if not 1 <= k <= 4 or not 1 <= starts <= 3 or iterations < 1:
        raise ValueError('Invalid configuration')
    n, dimension = data.shape
    if np.linalg.matrix_rank(np.cov(data[:, :-1].T).reshape(dimension-1, dimension-1)) < dimension-1:
        return dict(status='RankDeficient', components=[], starts=[], seconds=0., densityPoints=0)
    begun = time.perf_counter(); records = []; best = None; density_points = 0
    chart_center = math.atan2(np.sin(data[:, -1]).mean(), np.cos(data[:, -1]).mean())
    train = data.copy()
    if not wrapped:
        train[:, -1] = chart_center + wrap(data[:, -1]-chart_center)
    minimum = max(30, 5*dimension)
    for start in range(starts):
        components = []; hits = 0
        labels = partitions(data, k, start, seed+start)
        for j in range(k):
            block = train[labels == j].copy()
            if wrapped:
                center = math.atan2(np.sin(block[:, -1]).mean(), np.cos(block[:, -1]).mean())
                block[:, -1] = center + wrap(block[:, -1]-center)
            covariance, h = bounded_covariance(np.cov(block.T, bias=True)); hits += h
            components.append(dict(weight=len(block)/n, mean=block.mean(axis=0).tolist(), cov=covariance.tolist()))
        model = dict(components=components)
        trace = [float(gaussian_log(train, model, wrapped).sum())]; density_points += n*k
        status = 'IterationLimit'; max_tail = 0.
        try:
            for iteration in range(iterations):
                if time.perf_counter()-begun > max_seconds:
                    status = 'TimeBudgetExhausted'; break
                parts = [gaussian_parts(train, c, wrapped) for c in components]
                max_tail = max(max_tail, max(p[2] for p in parts))
                log_components = np.array([math.log(c['weight'])+logsumexp(p[1], axis=1)
                                           for c, p in zip(components, parts)])
                normalizer = logsumexp(log_components, axis=0)
                updated = []
                for c, (values, log_joint, _) in zip(components, parts):
                    responsibilities = np.exp(math.log(c['weight'])+log_joint-normalizer[:, None])
                    r = responsibilities.sum(axis=1); mass = float(r.sum()); ess = mass*mass/float(r @ r)
                    if mass < minimum or ess < minimum:
                        status = 'InsufficientComponent'; break
                    linear = train[:, :-1]
                    angular_sum = np.sum(responsibilities*values, axis=1)
                    mean = np.r_[r @ linear, angular_sum.sum()]/mass
                    second = np.empty((dimension, dimension))
                    second[:-1, :-1] = (linear.T*r) @ linear
                    second[:-1, -1] = second[-1, :-1] = linear.T @ angular_sum
                    second[-1, -1] = np.sum(responsibilities*values*values)
                    covariance, h = bounded_covariance(second/mass - np.outer(mean, mean)); hits += h
                    updated.append(dict(weight=mass/n, mean=mean.tolist(), cov=covariance.tolist()))
                density_points += sum(n*p[1].shape[1] for p in parts)
                if len(updated) != k:
                    break
                candidate = dict(components=updated)
                score = float(gaussian_log(train, candidate, wrapped).sum()); density_points += n*k
                if not math.isfinite(score):
                    status = 'NumericallyUnresolved'; break
                if score < trace[-1]-1e-8*n:
                    status = 'Stalled'; break
                delta = score-trace[-1]; components = updated; model = candidate; trace.append(score)
                if delta/n <= 1e-5:
                    status = 'Converged'; break
        except (ArithmeticError, np.linalg.LinAlgError) as error:
            status = 'NumericallyUnresolved:' + str(error)
        record = dict(start=start, status=status, score=trace[-1], trace=trace, covarianceBoundHits=hits,
                      maximumRelativeWindingTail=max_tail)
        records.append(record)
        if best is None or trace[-1] > best['trainScore']:
            best = dict(status=status, components=components, trainScore=trace[-1])
        if time.perf_counter()-begun > max_seconds:
            best['status'] = 'TimeBudgetExhausted'; break
    best.update(starts=records, seconds=time.perf_counter()-begun, densityPoints=density_points,
                chartCenter=chart_center, optimizedLikelihood='wrapped' if wrapped else 'ordinary-chart')
    return best


def sample_gvm(rng, model, n):
    labels = rng.choice(len(model['components']), n, p=[c['weight'] for c in model['components']])
    d = len(model['components'][0]['mean']); out = np.empty((n, d+1))
    for j, c in enumerate(model['components']):
        indices = np.flatnonzero(labels == j)
        x = rng.multivariate_normal(c['mean'], c['cov'], len(indices))
        angle = c['alpha'] + phase_features(x) @ c['coeff'] + rng.vonmises(0., c['kappa'], len(indices))
        out[indices, :-1] = x; out[indices, -1] = wrap(angle)
    return out


def sample_gaussian(rng, model, n):
    labels = rng.choice(len(model['components']), n, p=[c['weight'] for c in model['components']])
    dimension = len(model['components'][0]['mean']); out = np.empty((n, dimension))
    for j, c in enumerate(model['components']):
        indices = np.flatnonzero(labels == j)
        out[indices] = rng.multivariate_normal(c['mean'], c['cov'], len(indices))
    out[:, -1] = wrap(out[:, -1]); return out


def truth(d, family):
    if family in ('curved', 'seam'):
        cov = np.eye(d)*.8 + .2*np.ones((d, d)); components = []
        for j in range(1 if family == 'curved' else 2):
            coeff = np.r_[np.linspace(.35, -.2, d),
                          [(.55 if a == b else -.4) * (1 if j == 0 else -.7)
                           for a in range(d) for b in range(a, d)]]
            mu = np.zeros(d); mu[0] = 0 if family == 'curved' else (-1.3 if j == 0 else 1.3)
            components.append(dict(weight=1. if family == 'curved' else (.6 if j == 0 else .4),
                mean=mu.tolist(), cov=cov.tolist(), alpha=.4 if family == 'curved' else 3.0+j*.3,
                coeff=coeff.tolist(), kappa=8. if family == 'curved' else 12.))
        return 'gvm', dict(components=components)
    components = []
    for j in range(1 if family == 'local' else 2):
        lower = np.eye(d+1); lower[-1, :-1] = np.linspace(.5, -.2, d)
        lower[-1, -1] = .25 if family == 'local' else .55
        mean = np.zeros(d+1); mean[0] = 0 if family == 'local' else (-1.2 if j == 0 else 1.2)
        mean[-1] = .2 if family == 'local' else 3.0+j*.2
        components.append(dict(weight=1. if family == 'local' else .5,
                               mean=mean.tolist(), cov=(lower @ lower.T).tolist()))
    return 'gaussian', dict(components=components)


def whitening(data):
    center = data[:, :-1].mean(axis=0)
    covariance = np.cov(data[:, :-1].T, bias=True)
    if not np.all(np.isfinite(data)) or np.linalg.eigvalsh(np.atleast_2d(covariance)).min() <= 1e-10:
        raise ValueError('RankDeficient')
    lower = np.linalg.cholesky(np.atleast_2d(covariance))
    return center, lower


def transform(data, center, lower):
    return np.column_stack((solve_triangular(lower, (data[:, :-1]-center).T, lower=True).T, data[:, -1]))
