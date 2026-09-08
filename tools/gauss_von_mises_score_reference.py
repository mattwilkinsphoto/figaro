"""Independent 80-digit score fixtures; requires mpmath==1.3.0, writes no files.

Run with python -B. Integrates over the physical angle with tanh-sinh quadrature,
not the production scaled-coordinate adaptive Simpson integration.
"""
import mpmath as mp

mp.mp.dps = 80


def probabilities(n, concentration, score):
    k, s = mp.mpf(str(concentration)), mp.mpf(str(score))
    shape = mp.mpf(n) / 2
    cut = mp.pi if s >= 4*k else 2*mp.asin(mp.sqrt(s/(4*k)))
    norm = mp.pi*mp.besseli(0, k)

    def integrand(angle, upper):
        residual = max(mp.mpf(0), s-4*k*mp.sin(angle/2)**2)
        probability = (mp.gammainc(shape, residual/2, mp.inf) if upper
                       else mp.gammainc(shape, 0, residual/2)) / mp.gamma(shape)
        return mp.exp(k*mp.cos(angle))/norm*probability

    lower = mp.quad(lambda a: integrand(a, False), [0, cut/2, cut])
    upper = mp.quad(lambda a: integrand(a, True), [0, cut/2, cut])
    if cut < mp.pi:
        upper += mp.quad(lambda a: mp.exp(k*mp.cos(a))/norm, [cut, mp.pi])
    return lower, upper


for n, k, s in [(1, .1, .2), (1, 1, 4), (2, 4.5, 5), (5, 3, 12),
                (1, 50, 7), (20, .5, 30), (2, 4.5, 40)]:
    lower, upper = probabilities(n, k, s)
    print(n, k, s, 'cdf', mp.nstr(lower, 40), 'survival', mp.nstr(upper, 40), flush=True)
