"""GVM-06 test-only bounded gradient fitter. No library or default changes.

SciPy supplies L-BFGS-B; the weighted-resultant derivative and GEM orchestration
are independently written adaptations. Frozen GVM-05 files are not modified.
"""
import time
import numpy as np
from scipy.optimize import minimize, brentq
from scipy.special import i0e, i1e, logsumexp
import gvm_dimension_controls as c


class Limit(RuntimeError):
    pass


class Budget:
    def __init__(self, points=20000000, seconds=120):
        self.limit = points
        self.deadline = time.perf_counter() + seconds
        self.points = self.calls = self.density = 0

    def check(self):
        if time.perf_counter() > self.deadline:
            raise Limit('TimeBudgetExhausted')

    def phase(self, n):
        self.check()
        if self.points + n > self.limit:
            raise Limit('PhaseBudgetExhausted')
        self.points += n
        self.calls += 1


def objective(beta, features, angles, weights):
    """Negative squared weighted resultant and its analytic derivative."""
    w = weights / weights.sum()
    residual = angles - features @ beta
    cosine, sine = np.cos(residual), np.sin(residual)
    real, imag = w @ cosine, w @ sine
    derivative = -2 * ((w * (real*sine - imag*cosine)) @ features)
    return float(-real*real-imag*imag), derivative


def projected_gradient(beta, gradient):
    # Gradient mapping for a unit projected step; zero iff box KKT holds.
    return float(np.max(np.abs(beta - np.clip(beta-gradient, -4., 4.))))


def concentration(resultant):
    if resultant <= 1e-14:
        return 0.
    if resultant >= float(i1e(50.)/i0e(50.)):
        return 50.
    return float(brentq(lambda k: i1e(k)/i0e(k)-resultant, 0., 50., xtol=1e-13))


def angular(initial, features, angles, weights, budget, analytic=True, max_calls=400):
    count = 0
    best = None

    def evaluate(beta):
        nonlocal count, best
        if count >= max_calls:
            raise Limit('AngularBudgetExhausted')
        budget.phase(len(angles)); count += 1
        value, grad = objective(beta, features, angles, weights)
        if not np.isfinite(value) or not np.all(np.isfinite(grad)):
            raise Limit('NumericallyUnresolved')
        if best is None or value < best[0]:
            best = (value, beta.copy(), grad.copy())
        return (value, grad) if analytic else value

    message = ''; success = False
    try:
        result = minimize(evaluate, initial, method='L-BFGS-B', jac=analytic,
                          bounds=[(-4.,4.)]*len(initial),
                          options={'maxiter':400, 'maxfun':400, 'maxls':30,
                                   'ftol':1e-14, 'gtol':1e-7, 'maxcor':10})
        success = bool(result.success); message = str(result.message)
    except Limit as error:
        if str(error) != 'AngularBudgetExhausted' or best is None:
            raise
        message = str(error)
    value, beta, grad = best
    pg = projected_gradient(beta, grad)
    # A solver success flag alone is not a first-order qualification.
    qualified = success and pg <= 1e-6
    budget.phase(len(angles))
    residual = angles-features @ beta; w = weights/weights.sum()
    real, imag = w @ np.cos(residual), w @ np.sin(residual)
    return dict(coeff=beta.tolist(), alpha=float(np.arctan2(imag,real)),
                kappa=concentration(min(1.,float(np.hypot(real,imag))))), dict(
                calls=count, objective=value, projectedGradient=pg,
                qualified=qualified, solverSuccess=success, message=message)


def moments(data, weights):
    n, dimension = data.shape; d = dimension-1
    mass = float(weights.sum()); square = float(weights @ weights)
    if mass < max(30,5*d) or square == 0 or mass*mass/square < max(30,5*d):
        raise Limit('InsufficientComponent')
    x = data[:,:d]; mean = weights @ x/mass; delta = x-mean
    cov, hits = c.bounded_covariance((delta.T*weights) @ delta/mass)
    return dict(weight=mass/n, mean=mean.tolist(), cov=cov.tolist()), hits


def fit(data, k, seed, max_points=20000000, iterations=50, seconds=120,
        analytic=True, max_calls=400, third_keys=None):
    data = np.asarray(data)
    if (data.ndim != 2 or not 2 <= data.shape[1] <= 4 or not 30 <= len(data) <= 5000
        or not np.all(np.isfinite(data)) or not 1 <= k <= 3 or iterations < 1
        or max_calls < 1 or max_points < 0 or seconds <= 0):
        raise ValueError('Invalid data/configuration')
    n, dimension = data.shape; d = dimension-1
    started = time.perf_counter(); budget = Budget(max_points,seconds)
    if np.linalg.eigvalsh(np.atleast_2d(np.cov(data[:,:d].T,bias=True))).min() <= 1e-10:
        return dict(status='RankDeficient', components=[], starts=[], phasePoints=0, seconds=0.)
    features = c.phase_features(data[:,:d]); records=[]; best=None; global_failure=None

    def score(model, responsibilities=False):
        budget.check(); budget.density += n*k
        terms = np.array([c.gvm_log(data,dict(components=[dict(a,weight=1.)]))+np.log(a['weight'])
                          for a in model['components']])
        norm = logsumexp(terms,axis=0)
        if not np.all(np.isfinite(norm)):
            raise Limit('NumericallyUnresolved')
        return float(norm.sum()), np.exp(terms-norm) if responsibilities else None

    for start in range(3):
        trace=[]; inner=[]; current=None; status='IterationLimit'; hits=0
        try:
            if start == 2 and third_keys is not None:
                labels=np.empty(n,dtype=int)
                labels[np.argsort(third_keys,kind='stable')]=np.minimum(k-1,np.arange(n)*k//n)
            else:
                labels=c.partitions(data,k,start,seed+start)
            components=[]
            for j in range(k):
                r=(labels==j).astype(float); component,h= moments(data,r); hits+=h
                budget.phase(n)
                a=float(r @ np.cos(data[:,-1])/r.sum()); b=float(r @ np.sin(data[:,-1])/r.sum())
                component.update(coeff=np.zeros(features.shape[1]).tolist(),alpha=float(np.arctan2(b,a)),
                                 kappa=concentration(min(1.,float(np.hypot(a,b)))))
                components.append(component)
            current=dict(components=components); trace.append(score(current)[0])
            for iteration in range(iterations):
                _, responsibilities=score(current,True); updated=[]; phase_records=[]
                for component,r in zip(current['components'],responsibilities):
                    fresh,h=moments(data,r); hits+=h
                    phase,record=angular(np.array(component['coeff']),features,data[:,-1],r,
                                         budget,analytic,max_calls)
                    fresh.update(phase); updated.append(fresh); phase_records.append(record)
                candidate=dict(components=updated); value=score(candidate)[0]
                inner.append(phase_records)
                if value < trace[-1]-1e-8*n:
                    status='Stalled'; break
                delta=value-trace[-1]; current=candidate; trace.append(value)
                if delta/n <= 1e-5:
                    status='Converged' if all(r['qualified'] for r in phase_records) else 'AngularUnqualified'
                    break
        except Limit as error:
            status=str(error)
            if status in ('PhaseBudgetExhausted','TimeBudgetExhausted'):
                global_failure=status
        except (ArithmeticError,np.linalg.LinAlgError) as error:
            status='NumericallyUnresolved:'+str(error)
        record=dict(start=start,status=status,trace=trace,angular=inner,covarianceBoundHits=hits)
        records.append(record)
        if trace and (best is None or trace[-1]>best['trainScore']):
            best=dict(components=current['components'],trainScore=trace[-1],status=status)
        if global_failure:
            break
    if best is None:
        best=dict(components=[],trainScore=None,status=records[-1]['status'])
    if global_failure:
        best['status']=global_failure
    best.update(starts=records,phasePoints=budget.points,angularCalls=budget.calls,
                densityPoints=budget.density,seconds=time.perf_counter()-started)
    return best
