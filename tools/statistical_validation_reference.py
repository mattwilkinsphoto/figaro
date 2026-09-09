"""Independent fixed-data posterior quadrature (research only; requires NumPy).

No Figaro density, sampler, or special-function implementation is used.
The Java-compatible generator ONLY defines reproducible observations, not an oracle.
Run with python -B tools/statistical_validation_reference.py.
"""
import math
import numpy as np


class FixtureRandom:
    def __init__(self, seed):
        self.state = (seed ^ 0x5DEECE66D) & ((1 << 48) - 1)

    def bits(self, n):
        self.state = (self.state * 0x5DEECE66D + 11) & ((1 << 48) - 1)
        return self.state >> (48 - n)

    def uniform(self):
        return ((self.bits(26) << 27) + self.bits(27)) / float(1 << 53)

    def gamma_integer(self, shape):
        return sum(-math.log1p(-self.uniform()) for _ in range(shape))


def observations(seed_offset=0):
    g, d = FixtureRandom(104729 + seed_offset), FixtureRandom(130363 + seed_offset)
    gamma = np.array([2 * g.gamma_integer(2) for _ in range(200)])
    rows = np.array([[d.gamma_integer(a) for a in (1, 2, 3)] for _ in range(200)])
    return gamma, rows / rows.sum(axis=1)[:, None]


def posterior(family, order, seed_offset=0):
    """Tensor Gauss-Legendre integral over the COMPLETE Uniform(0,10) prior box.

    Returns posterior mean and SD; increasing-order agreement is not a certificate.
    Slices the third dimension to bound peak memory. Omits constant prior density.
    """
    gamma, dirichlet = observations(seed_offset)
    x, w = np.polynomial.legendre.leggauss(order)
    x, w = 5 * (x + 1), 5 * w
    lg = np.vectorize(math.lgamma)
    a, b = x[:, None], x[None, :]
    weights = w[:, None] * w[None, :]
    logs = np.log(dirichlet).sum(axis=0)
    dims = 2 if family == 'gamma' else 3
    total = np.zeros(1 + 2 * dims)
    # Dynamic rescaling of the full integral, including previously accumulated slices.
    maximum = -math.inf
    for c, wc in ([(0., 1.)] if dims == 2 else zip(x, w)):
        if dims == 2:
            ll = ((a - 1) * np.log(gamma).sum() - gamma.sum() / b
                  - 200 * (lg(a) + a * np.log(b)))
            coordinates = [a, b]
        else:
            ll = (200 * (lg(a+b+c) - lg(a) - lg(b) - math.lgamma(c))
                  + (a-1)*logs[0] + (b-1)*logs[1] + (c-1)*logs[2])
            coordinates = [a, b, c]
        new_maximum = max(maximum, float(ll.max()))
        total *= math.exp(maximum - new_maximum)
        maximum = new_maximum
        mass = np.exp(ll - maximum) * weights * wc
        total[0] += mass.sum()
        for i, coordinate in enumerate(coordinates):
            total[1+i] += (mass*coordinate).sum()
            total[1+dims+i] += (mass*coordinate**2).sum()
    mean = total[1:1+dims] / total[0]
    sd = np.sqrt(total[1+dims:] / total[0] - mean**2)
    return mean, sd


if __name__ == '__main__':
    gamma, dirichlet = observations()
    print('gamma sufficient statistics:', gamma.sum(), np.log(gamma).sum())
    print('dirichlet sufficient statistics:', np.log(dirichlet).sum(axis=0))
    for family in ('gamma', 'dirichlet'):
        for order in (64, 96, 144, 216):
            mean, sd = posterior(family, order)
            print(family, order, 'mean', [float(v) for v in mean], 'sd', [float(v) for v in sd])
