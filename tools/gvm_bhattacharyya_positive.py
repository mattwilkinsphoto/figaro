"""Research-only positive integration of a scalar Gaussian quadratic phase.

Standard-library binary64 integrator; not a Figaro API, automatic fallback, or
certified quadrature. See docs/GVM_BHATTACHARYYA_POSITIVE_RESEARCH.md.
"""
from dataclasses import dataclass
import heapq
import math


@dataclass(frozen=True)
class Result:
    status: str
    distance: float | None
    estimated_interval: tuple[float,float] | None
    evaluations: int
    radius: float
    gaussian_tail_bound: float
    quadrature_error_estimate: float
    roundoff_estimate: float


def log_i0(x):
    """Positive defining series, only for this study's x in [0,50]."""
    if not math.isfinite(x) or not 0 <= x <= 50:
        raise ValueError('Bessel argument outside study range')
    term = total = 1.
    for j in range(1,1001):
        term *= (x/2)**2/(j*j)
        total += term
        if term <= total*1e-17: return math.log(total)
    raise ArithmeticError('Bessel series guard exhausted')


def compare_phase(gaussian_distance, constant, linear, quadratic, kappa_p, kappa_q,
                  tolerance=1e-8, max_evaluations=50000, cancelled=None):
    """Estimate DG-log E[h(c+l*Z+q*Z^2/2)] for standard-normal scalar Z.

    Inputs are already transformed phase coefficients, NOT physical GVM kernels.
    Positive Simpson panel refinement uses a heuristic discrepancy, not a proof.
    No distance is exposed unless that estimate meets the absolute nat tolerance.
    cancelled is an optional no-argument predicate; True raises InterruptedError.
    """
    values = (gaussian_distance,constant,linear,quadratic,kappa_p,kappa_q,tolerance)
    if not all(math.isfinite(x) for x in values) or gaussian_distance < 0 or tolerance <= 0:
        raise ValueError('finite inputs, nonnegative Gaussian distance and positive tolerance required')
    if isinstance(max_evaluations,bool) or not isinstance(max_evaluations,int) or not 5 <= max_evaluations <= 200000:
        raise ValueError('evaluation budget must be an integer in [5,200000]')
    if cancelled is not None and not callable(cancelled): raise ValueError('cancelled must be callable')
    def interrupted():
        if cancelled is not None and cancelled(): raise InterruptedError('positive integration cancelled')
    interrupted()
    def unavailable(status, evaluations=0, radius=0., tail=math.inf):
        return Result(status,None,None,evaluations,radius,tail,math.inf,math.inf)
    if not 0 <= min(kappa_p,kappa_q) <= max(kappa_p,kappa_q) <= 50:
        return unavailable('UnsupportedRange')
    if max(abs(constant),abs(linear),abs(quadratic)) > 1e4 or gaussian_distance > 1e4:
        return unavailable('UnsupportedRange')
    a,b = kappa_p/2,kappa_q/2
    log_den = (log_i0(kappa_p)+log_i0(kappa_q))/2
    # h(delta) lies between I0(|a-b|)/den and 1, so even tiny overlaps
    # receive a relative tail allowance rather than a fixed absolute cutoff.
    minimum_affinity = math.exp(log_i0(abs(a-b))-log_den)
    radius = 4.
    while math.erfc(radius/math.sqrt(2)) > tolerance*minimum_affinity/16 and radius < 16:
        interrupted()
        radius += 1
    tail = math.erfc(radius/math.sqrt(2))
    if tail > tolerance*minimum_affinity/16:
        return unavailable('NumericallyUnresolved',radius=radius,tail=tail)

    def phase(z): return constant+linear*z+quadratic*z*z/2
    def phase_span(left,right):
        extrema = [phase(left),phase(right)]
        if quadratic != 0:
            vertex = -linear/quadratic
            if left < vertex < right: extrema.append(phase(vertex))
        return max(extrema)-min(extrema)

    # Do not let matching coarse samples declare a highly oscillatory phase flat.
    # This prepartition is a safeguard, not a general anti-aliasing theorem.
    phase_step = min(.5,1/math.sqrt(max(1.,kappa_p,kappa_q)))
    pending = [(-radius,radius)]
    intervals = []
    while pending:
        interrupted()
        left,right = pending.pop()
        if right-left <= 1 and phase_span(left,right) <= phase_step:
            intervals.append((left,right))
        else:
            mid = (left+right)/2
            if mid in (left,right): return unavailable('NumericallyUnresolved',radius=radius,tail=tail)
            pending.extend(((mid,right),(left,mid)))
        if 5*(len(pending)+len(intervals)) > max_evaluations:
            return unavailable('BudgetExhausted',radius=radius,tail=tail)

    evaluations = 0
    def integrand(z):
        nonlocal evaluations
        interrupted()
        if evaluations >= max_evaluations: raise AssertionError('internal budget overrun')
        evaluations += 1
        r = math.hypot(a-b,2*math.sqrt(a*b)*math.cos(phase(z)/2))
        # hypot can round infinitesimally above a+b at a coincident center.
        r = min(a+b,r)
        return math.exp(-z*z/2+log_i0(r)-log_den)/math.sqrt(2*math.pi)

    def panel(left,right,values=None):
        width = right-left
        if values is None: values = tuple(integrand(left+width*i/4) for i in range(5))
        coarse = width*(values[0]+4*values[2]+values[4])/6
        fine = width*(values[0]+4*values[1]+2*values[2]+4*values[3]+values[4])/12
        # Keep the positive fine rule; no signed Richardson extrapolation.
        return fine,abs(fine-coarse),(left,right,values)

    heap = []
    serial = 0
    for left,right in intervals:
        value,error,data = panel(left,right)
        heapq.heappush(heap,(-error,serial,value,data))
        serial += 1

    while True:
        interrupted()
        # fsum avoids drift from repeatedly subtracting parent contributions.
        value = math.fsum(row[2] for row in heap)
        error = math.fsum(-row[0] for row in heap)
        rounding = 64*math.ulp(1.)*(evaluations+1)*(1+abs(constant)+abs(linear)+abs(quadratic))*value
        allowance = error+rounding
        low,high = max(0.,value-allowance),min(1.,value+allowance+tail)
        interval = None
        estimate = None
        if high > 0 and low <= high:
            interval = (max(0.,gaussian_distance-math.log(high)),
                        gaussian_distance-math.log(low) if low > 0 else math.inf)
            if 0 < value <= 1+allowance:
                estimate = max(0.,gaussian_distance-math.log(min(1.,value)))
        # Gaussian/phase preprocessing is intentionally outside this component study.
        log_rounding = 64*math.ulp(1.)*(1+gaussian_distance)
        if interval is not None:
            interval = (max(0.,interval[0]-log_rounding),interval[1]+log_rounding)
        ok = estimate is not None and max(estimate-interval[0],interval[1]-estimate) <= tolerance
        status = 'Estimated' if ok else ('NumericallyUnresolved' if error <= rounding else 'BudgetExhausted')
        result = Result(status,estimate if ok else None,interval,evaluations,radius,tail,error,rounding)
        if ok or status == 'NumericallyUnresolved' or evaluations+4 > max_evaluations: return result
        _,_,_,(left,right,values) = heapq.heappop(heap)
        width = right-left
        mid = (left+right)/2
        if mid in (left,right): return unavailable('NumericallyUnresolved',evaluations,radius,tail)
        for start,stop,old in ((left,mid,values[:3]),(mid,right,values[2:])):
            new = (old[0],integrand(start+(stop-start)/4),old[1],integrand(start+3*(stop-start)/4),old[2])
            value,error,data = panel(start,stop,new)
            heapq.heappush(heap,(-error,serial,value,data))
            serial += 1


def main():
    # Optional research dependency is used ONLY to prepare phases and reference answers.
    import mpmath as mp
    from gvm_bhattacharyya_reliability import comparison, oracle, specifications
    count = 0
    estimated = 0
    max_error = 0.
    min_work,max_work = 200001,0
    for spec in specifications():
        if spec[1] != 1: continue
        with mp.workdps(80):
            c = comparison(spec)
            expected = float(oracle(spec))
            args = tuple(float(x) for x in (c.gaussian_distance,c.constant,c.linear[0],c.quadratic[0],c.p.kappa,c.q.kappa))
        result = compare_phase(*args)
        error = abs(result.distance-expected) if result.distance is not None else math.nan
        print(f'GVM_POSITIVE family={spec[0]} kappa={spec[2]} alpha={spec[3]} beta={spec[4]} gamma={spec[5]} status={result.status} evaluations={result.evaluations} radius={result.radius} error={error}',flush=True)
        if result.distance is not None and error > 1e-8: raise AssertionError('oracle accuracy failure')
        if result.distance is not None:
            estimated += 1
            max_error = max(max_error,error)
        min_work,max_work = min(min_work,result.evaluations),max(max_work,result.evaluations)
        count += 1
    if count != 84: raise AssertionError('incomplete scalar grid')
    print(f'GVM_POSITIVE_COMPLETE comparisons={count} estimated={estimated} maxError={max_error} minEvaluations={min_work} maxEvaluations={max_work}')


if __name__ == '__main__': main()
