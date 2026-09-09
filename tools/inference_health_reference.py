# Reference function copied unchanged from ArviZ v0.22.0 arviz/stats/stats.py.
# Copyright ArviZ developers. Apache-2.0; see ../ArviZ-LICENSE.txt.
# Added deterministic fixture driver for Figaro; NumPy is research-only.
import numpy as np

def _gpdfit(ary):
    """Estimate the parameters for the Generalized Pareto Distribution (GPD).

    Empirical Bayes estimate for the parameters of the generalized Pareto
    distribution given the data.

    Parameters
    ----------
    ary: array
        sorted 1D data array

    Returns
    -------
    k: float
        estimated shape parameter
    sigma: float
        estimated scale parameter
    """
    prior_bs = 3
    prior_k = 10
    n = len(ary)
    m_est = 30 + int(n**0.5)

    b_ary = 1 - np.sqrt(m_est / (np.arange(1, m_est + 1, dtype=float) - 0.5))
    b_ary /= prior_bs * ary[int(n / 4 + 0.5) - 1]
    b_ary += 1 / ary[-1]

    k_ary = np.log1p(-b_ary[:, None] * ary).mean(axis=1)  # pylint: disable=no-member
    len_scale = n * (np.log(-(b_ary / k_ary)) - k_ary - 1)
    weights = 1 / np.exp(len_scale - len_scale[:, None]).sum(axis=1)

    # remove negligible weights
    real_idxs = weights >= 10 * np.finfo(float).eps
    if not np.all(real_idxs):
        weights = weights[real_idxs]
        b_ary = b_ary[real_idxs]
    # normalise weights
    weights /= weights.sum()

    # posterior mean for b
    b_post = np.sum(b_ary * weights)
    # estimate for k
    k_post = np.log1p(-b_post * ary).mean()  # pylint: disable=invalid-unary-operand-type,no-member
    # add prior for k_post
    sigma = -k_post / b_post
    k_post = (n * k_post + prior_k * 0.5) / (n + prior_k)

    return k_post, sigma


def fixtures():
    for shape in (-0.25, 0.0, 0.3, 0.7, 1.2):
        n = 2000
        p = (np.arange(n) + 0.5) / n
        x = -np.log1p(-p) if shape == 0 else np.expm1(-shape*np.log1p(-p))/shape
        logs = np.log1p(x)
        shifted = np.sort(logs - logs.max())
        m = int(np.ceil(min(n/5, 3*np.sqrt(n))))
        cutoff = max(shifted[-m-1], np.log(np.finfo(float).tiny))
        excess = np.exp(shifted[shifted > cutoff]) - np.exp(cutoff)
        k, scale = _gpdfit(excess)
        yield shape, float(k), float(scale), len(excess)

if __name__ == '__main__':
    for row in fixtures():
        print(*row, sep=',')
