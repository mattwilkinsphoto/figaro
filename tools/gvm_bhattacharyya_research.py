"""Optional high-precision prototype, NOT a Figaro runtime API.

Requires mpmath==1.3.0. Run with python -B; writes no files. See
docs/GVM_BHATTACHARYYA_RESEARCH.md for derivation and limitations. Only trusted,
small fixtures are supported; explicit inverses are reference operations, not a
proposed production linear-algebra implementation.
"""
from dataclasses import dataclass
from functools import lru_cache
from itertools import product
import mpmath as mp


def mat(rows):
    """Convert decimal fixture rows to an arbitrary-precision matrix."""
    return mp.matrix([[mp.mpf(str(x)) for x in row] for row in rows])


@dataclass
class Kernel:
    """Trusted fixture: mean vector, lower factor A, alpha, beta, gamma, kappa."""
    mean: object
    factor: object
    alpha: object
    beta: object
    gamma: object
    kappa: object

    def center(self, x):
        """Conditional angular center at physical x, before wrapping."""
        z = self.factor**-1 * (x-self.mean)
        return self.alpha + (self.beta.T*z)[0] + (z.T*self.gamma*z)[0]/2


def scalar(mean=0, sd=1, alpha=0, beta=0, gamma=0, kappa=0):
    """One-linear-coordinate trusted fixture; sd is not variance."""
    return Kernel(mat([[mean]]), mat([[sd]]), mp.mpf(str(alpha)), mat([[beta]]),
                  mat([[gamma]]), mp.mpf(str(kappa)))


class Comparison:
    """Gaussian overlap and center difference under an auxiliary integration measure.

    Not an output GVM, posterior update, report ingestion or data-fusion operation.
    """
    def __init__(self, p, q):
        if p.mean.rows != q.mean.rows or min(p.kappa, q.kappa) < 0:
            raise ValueError('same dimensions and nonnegative concentrations required')
        self.p, self.q = p, q
        pp, pq = p.factor*p.factor.T, q.factor*q.factor.T
        ip, iq = pp**-1, pq**-1
        self.covariance = 2*(ip+iq)**-1
        self.mean = (ip+iq)**-1*(ip*p.mean+iq*q.mean)
        self.factor = mp.cholesky(self.covariance)
        average = (pp+pq)/2
        difference = p.mean-q.mean
        self.gaussian_distance = ((difference.T*average**-1*difference)[0]/8
            + mp.log(mp.det(average)/mp.sqrt(mp.det(pp)*mp.det(pq)))/2)
        self.denominator = mp.sqrt(mp.besseli(0,p.kappa)*mp.besseli(0,q.kappa))
        self.a, self.b = p.kappa/2, q.kappa/2

        def phase(kernel):
            d = kernel.factor**-1*(self.mean-kernel.mean)
            b = kernel.factor**-1*self.factor
            return (kernel.center(self.mean), b.T*(kernel.beta+kernel.gamma*d),
                    b.T*kernel.gamma*b)

        cp, lp, hp = phase(p)
        cq, lq, hq = phase(q)
        self.constant, self.linear, self.quadratic = cp-cq, lp-lq, hp-hq
        self.eigenvalues, vectors = mp.eigsy(self.quadratic)
        self.rotated = vectors.T*self.linear

    def characteristic(self, order):
        """E[exp(i*order*delta(X))], with branch continuous from order zero."""
        log_value = 1j*order*self.constant
        for j in range(self.linear.rows):
            term = 1-1j*order*self.eigenvalues[j]
            log_value -= mp.log(term)/2 + order**2*self.rotated[j]**2/(2*term)
        return mp.exp(log_value)

    def angular_affinity(self, x):
        """Exact conditional angular affinity at physical x: positive and at most one."""
        delta = self.p.center(x)-self.q.center(x)
        radius = mp.sqrt((self.a-self.b)**2+4*self.a*self.b*mp.cos(delta/2)**2)
        return mp.besseli(0,radius)/self.denominator

    def coefficient(self, order):
        """Nonnegative Fourier coefficient, including factor two for positive order."""
        return (1 if order == 0 else 2)*mp.besseli(order,self.a)*mp.besseli(order,self.b)/self.denominator

    def series(self, terms):
        """Angular partial sum and analytic truncation bound, excluding roundoff.

        terms is the highest retained harmonic, 0..1000. Infinity means the elementary
        ratio bound is unavailable at this budget, not that the series diverges.
        """
        if isinstance(terms,bool) or not isinstance(terms,int) or not 0 <= terms <= 1000:
            raise ValueError('terms must be an integer in [0,1000]')
        value = mp.fsum(self.coefficient(j)*mp.re(self.characteristic(j)) for j in range(terms+1))
        ratio = self.a*self.b/(4*(terms+2)**2)
        tail = self.coefficient(terms+1)/(1-ratio) if ratio < 1 else mp.inf
        return value, tail

    def distance_interval(self, terms):
        """Truncation-only distance interval; infinity if positive overlap unresolved.

        Not an interval-arithmetic certificate or statistical confidence interval.
        """
        value, tail = self.series(terms)
        lower, upper = max(mp.mpf(0),value-tail), min(mp.mpf(1),value+tail)
        if upper <= 0 or lower > upper:
            raise ArithmeticError('unresolved numerical interval')
        return (self.gaussian_distance-mp.log(upper),
                self.gaussian_distance-mp.log(lower) if lower > 0 else mp.inf)

    def quadrature(self, order):
        """Positive angular-reduced Hermite reference: at most 10000 points, no certificate."""
        n = self.mean.rows
        if not isinstance(order,int) or not 1 <= order <= 96 or order**n > 10000:
            raise ValueError('invalid or excessive tensor order')
        nodes, weights = hermite(order,mp.mp.dps)
        values = []
        for indices in product(range(order),repeat=n):
            z = mp.matrix([mp.sqrt(2)*nodes[j] for j in indices])
            weight = mp.fprod(weights[j]/mp.sqrt(mp.pi) for j in indices)
            values.append(weight*self.angular_affinity(self.mean+self.factor*z))
        return mp.fsum(values)


@lru_cache(maxsize=8)
def hermite(order, precision):
    """Small cached reference rules keyed by working precision; no disk cache."""
    with mp.workdps(precision):
        return mp.gauss_quadrature(order,'hermite')


def main():
    """Print research cases; no runtime API or benchmark speed claim."""
    with mp.workdps(60):
        cases = [
            ('curved-1d',scalar('.3','1.1','.2','.7','.3','4.5'),
             scalar('-.4','.8','-.5','-.2','-.15','1.2')),
            ('opposed-k50',scalar(kappa=50),scalar(alpha=mp.pi,kappa=50)),
            ('concentrated-k1000',scalar(kappa=1000),scalar(alpha='.01',beta='.1',kappa=1000)),
        ]
        for name,p,q in cases:
            comparison = Comparison(p,q)
            for terms in (8,32,128,512):
                value,tail = comparison.series(terms)
                print(name,'terms',terms,'angular',mp.nstr(value,24),'tail',mp.nstr(tail,8),
                      'distanceInterval',tuple(mp.nstr(v,24) for v in comparison.distance_interval(terms)),flush=True)


def scala_fixtures():
    """Reproduce the general Scala regression fixtures with 60-digit arithmetic."""
    with mp.workdps(60):
        cases = []
        for k in ('.01','1','10','50'):
            cases.append(('curved-k'+k,scalar('.3','1.1','.2','.7','.3',k),
                          scalar('-.4','.8','-.5','-.2','-.15',k)))
        cases.append(('two-dimensional',
            Kernel(mat([[0],[0]]),mp.eye(2),mp.mpf('.2'),mat([[.2],[-.1]]),
                   mat([[.1,.04],[.04,-.05]]),mp.mpf(2)),
            Kernel(mat([[.2],[-.1]]),mat([[1.1,0],[.1,.9]]),mp.mpf('-.1'),
                   mat([[-.1],[.2]]),mat([[0,0],[0,.1]]),mp.mpf(3))))
        for n,b,g,k in [(6,mp.mpf('.2'),0,4),(8,0,2,2)]:
            cases.append((str(n)+'-dimensional',
                Kernel(mp.zeros(n,1),mp.eye(n),0,mp.matrix([b]*n),g*mp.eye(n),mp.mpf(k)),
                Kernel(mp.zeros(n,1),mp.eye(n),0,mp.matrix([-b]*n),mp.zeros(n),mp.mpf(k))))
        for name,p,q in cases:
            c = Comparison(p,q)
            value,_ = c.series(128)
            print(name,'distance',mp.nstr(c.gaussian_distance-mp.log(value),40),flush=True)


if __name__ == '__main__':
    main()
    scala_fixtures()
