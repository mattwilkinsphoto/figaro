"""GVM-08 research-only conditional convex solve; see GVM_WEIGHT_RESTART_RESEARCH.

SciPy SLSQP is a backend, not a qualification oracle. The certificate is evaluated
separately using compensated summation and an explicit floating-point allowance.
"""
import math
import numpy as np
from scipy.optimize import minimize
import gvm_wholefit_math as m

FLOOR = 1e-8


class LocalLimit(RuntimeError):
    pass


def matrix(logs):
    logs = np.asarray(logs, dtype=float)
    if logs.ndim != 2 or not len(logs) or not 1 <= logs.shape[1] <= 3 or not np.all(np.isfinite(logs)):
        raise ValueError('Invalid fixed-component log densities')
    offsets = logs.max(axis=1)
    return np.exp(logs-offsets[:, None]), offsets


def certificate(a, w):
    """Compensated, independently accumulated gap, not the SLSQP termination test.

    Relative allowance covers exp/dot/divide and compensated accumulation on the
    declared binary64 envelope. High-precision controls validate this envelope;
    this is not an interval-arithmetic guarantee for arbitrary math libraries.
    """
    n, k = a.shape
    if not np.all(np.isfinite(w)) or min(w) < FLOOR-1e-15 or abs(math.fsum(w)-1) > 1e-12:
        raise ValueError('Infeasible weights')
    den = np.array([math.fsum(float(x)*float(y) for x,y in zip(row,w)) for row in a])
    if min(den) <= 0 or not np.all(np.isfinite(den)):
        raise ArithmeticError('Invalid mixture density')
    g = np.array([-math.fsum(float(x) for x in a[:,j]/den)/n for j in range(k)])
    gap = math.fsum(float(x)*float(y) for x,y in zip(w,g)) - (FLOOR*math.fsum(g)+(1-k*FLOOR)*min(g))
    allowance = 128*np.finfo(float).eps*max(1., max(abs(g)))
    if gap < -allowance or not np.isfinite(gap):
        raise ArithmeticError('Invalid convex gap')
    return dict(gap=float(gap), allowance=float(allowance), upper=float(gap+allowance),
                gradient=g.tolist(), objective=-math.fsum(math.log(float(x)) for x in den)/n)


def solve(logs, initial, charge=lambda kind,n: None, max_calls=200, max_work=1000000):
    logs = np.asarray(logs, dtype=float); w = np.asarray(initial, dtype=float)
    if logs.ndim != 2 or len(w) != logs.shape[1] or max_calls < 1 or max_work < 0:
        raise ValueError('Invalid weight configuration')
    local = calls = 0
    def spend(passes=1):
        nonlocal local
        amount = logs.size*passes
        if local+amount > max_work: raise LocalLimit('WeightWorkLimit')
        charge('weight', amount); local += amount
    spend(); a, offsets = matrix(logs)
    if min(w) < FLOOR or abs(sum(w)-1) > 1e-12: raise ValueError('Infeasible initial weights')
    def fg(x):
        nonlocal calls
        if calls >= max_calls: raise LocalLimit('WeightCallLimit')
        spend(2); calls += 1
        den = a@x
        if min(den) <= 0: raise ArithmeticError('Nonpositive denominator')
        return -float(np.log(den).mean()), -(a/den[:,None]).mean(axis=0)
    message = ''; success = False; raw = w.copy()
    try:
        result = minimize(fg, w, jac=True, method='SLSQP', bounds=[(FLOOR,1.)]*len(w),
            constraints=[dict(type='eq',fun=lambda x:float(sum(x)-1),jac=lambda x:np.ones(len(x)))],
            options=dict(maxiter=100,ftol=1e-14))
        raw = result.x; success = bool(result.success); message = str(result.message)
    except LocalLimit as error:
        message = str(error)
        return w, dict(qualified=False,status=message,calls=calls,work=local)
    corrected = m.simplex(raw)
    try:
        spend(3); cert = certificate(a, corrected)
    except LocalLimit as error:
        return w, dict(qualified=False,status=str(error),calls=calls,work=local)
    cert.update(objective=cert['objective']-float(offsets.mean()),
        correction=float(np.max(np.abs(corrected-raw))), calls=calls,work=local,
        backendSuccess=success,message=message)
    cert['qualified'] = cert['upper'] <= 1e-8
    cert['status'] = 'Qualified' if cert['qualified'] else 'WeightUnqualified'
    return corrected, cert
