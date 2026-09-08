"""Research-only scalar tail/work assessment; optional mpmath==1.3.0, stdout only.

No production radius policy changes. Cell minima bound an integral from below;
the high-precision overlap is used for validation, never to choose the radius.
"""
import math
import mpmath as mp
from gvm_bhattacharyya_reliability import comparison, oracle, specifications


def phase_range(c, left, right):
    """Exact quadratic extrema on a closed interval, evaluated in current precision."""
    constant, linear, quadratic = c.constant, c.linear[0], c.quadratic[0]
    def at(z): return constant+linear*z+quadratic*z*z/2
    values = [at(left), at(right)]
    if quadratic:
        vertex = -linear/quadratic
        if left < vertex < right:
            values.append(at(vertex))
    return min(values), max(values)


def cell_lower_bound(c, cells=64):
    """Lower bound for E[h(Z)] from angular minima over cells covering [-4,4].

    The bound follows from positivity and the monotonicity of I0 on nonnegative
    arguments. Finite-precision evaluation is research arithmetic, not certification.
    """
    if isinstance(cells, bool) or not isinstance(cells, int) or not 1 <= cells <= 1024:
        raise ValueError('cells must be an integer in [1,1024]')
    contributions = []
    for i in range(cells):
        left, right = mp.mpf(-4)+mp.mpf(8)*i/cells, mp.mpf(-4)+mp.mpf(8)*(i+1)/cells
        lo, hi = phase_range(c, left, right)
        # cos(delta/2)^2 vanishes at every odd multiple of pi.
        contains_zero = mp.ceil((lo/mp.pi-1)/2) <= mp.floor((hi/mp.pi-1)/2)
        cos_squared = 0 if contains_zero else min(mp.cos(lo/2)**2, mp.cos(hi/2)**2)
        radius = mp.sqrt((c.a-c.b)**2+4*c.a*c.b*cos_squared)
        minimum = mp.besseli(0, radius)/c.denominator
        mass = (mp.erf(right/mp.sqrt(2))-mp.erf(left/mp.sqrt(2)))/2
        contributions.append(mass*minimum)
    return mp.fsum(contributions)


def radius_for(lower, tolerance=None):
    """Integer radius 4..16 meeting the existing tail allocation, or raise if absent."""
    tolerance = mp.mpf('1e-8') if tolerance is None else mp.mpf(tolerance)
    if not mp.isfinite(lower) or not 0 < lower <= 1 or not mp.isfinite(tolerance) or tolerance <= 0:
        raise ValueError('positive finite affinity lower bound and tolerance required')
    for radius in range(4, 17):
        if mp.erfc(radius/mp.sqrt(2)) <= tolerance*lower/16:
            return radius
    raise ArithmeticError('tail radius cap exhausted')


def initial_panels(c, radius):
    """Count existing phase-aware initial panels; exclude adaptive refinement and cell-bound setup."""
    constant, linear, quadratic = float(c.constant), float(c.linear[0]), float(c.quadratic[0])
    step = min(.5, 1/math.sqrt(max(1., float(c.p.kappa), float(c.q.kappa))))
    pending, panels = [(-float(radius), float(radius))], 0
    def at(z): return constant+linear*z+quadratic*z*z/2
    while pending:
        left, right = pending.pop()
        values = [at(left), at(right)]
        vertex = -linear/quadratic if quadratic else math.inf
        if left < vertex < right:
            values.append(at(vertex))
        if right-left <= 1 and max(values)-min(values) <= step:
            panels += 1
        else:
            mid = (left+right)/2
            if mid in (left, right):
                raise ArithmeticError('panel midpoint unresolved')
            pending.extend(((mid, right), (left, mid)))
        if panels+len(pending) > 40000:
            raise ArithmeticError('research panel cap exceeded')
    return panels


def assess(spec):
    """Return one validated work comparison at 80 digits; use no oracle information in selection."""
    with mp.workdps(80):
        c = comparison(spec)
        global_lower = mp.besseli(0, abs(c.a-c.b))/c.denominator
        lower = max(global_lower, cell_lower_bound(c))
        exact_affinity = mp.exp(c.gaussian_distance-oracle(spec))
        if lower > exact_affinity*(1+mp.mpf('1e-60')):
            raise AssertionError('cell bound exceeds reference affinity')
        old_radius, new_radius = radius_for(global_lower), radius_for(lower)
        return dict(oldRadius=old_radius, candidateRadius=new_radius,
                    oldPanels=initial_panels(c, old_radius), candidatePanels=initial_panels(c, new_radius),
                    tailOverAffinity=float(mp.erfc(new_radius/mp.sqrt(2))/exact_affinity))


def report():
    """Return all 84 scalar rows and a complete-grid summary; does not write files."""
    rows, lines = [], []
    for index, spec in enumerate(specifications()):
        if spec[1] != 1:
            continue
        result = assess(spec)
        rows.append(result)
        lines.append(f'GVM_TAIL case={spec[0]}-{index:02d} '+ ' '.join(f'{key}={value}' for key, value in result.items()))
    assert len(rows) == 84
    lines.append(f'GVM_TAIL_COMPLETE cases={len(rows)} smallerRadius={sum(r["candidateRadius"] < r["oldRadius"] for r in rows)} '
                 f'oldPanels={sum(r["oldPanels"] for r in rows)} candidatePanels={sum(r["candidatePanels"] for r in rows)}')
    return '\n'.join(lines)+'\n'


def main():
    """Print the reproducible report to stdout."""
    print(report(), end='')


if __name__ == '__main__':
    main()
