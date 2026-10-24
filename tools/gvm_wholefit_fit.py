"""GVM-07 bounded whole-fit experiment; not a public fitter or inference change.

Squared extrapolation is independently adapted from Du & Varadhan (2020),
Algorithm 1. Projection, circular differences, conservative step cap and strict
safeguards differ from CRAN SQUAREM. No convergence theorem is transferred.
"""
import time
import numpy as np
import gvm_dimension_controls as c
import gvm_gradient_fit as g
import gvm_wholefit_math as m


class Budget:
    def __init__(self, limit, seconds):
        self.limit = limit; self.deadline = time.perf_counter() + seconds
        self.work = dict(phase=0, score=0, moments=0, gradient=0)
        self.calls = 0

    def check(self):
        if time.perf_counter() > self.deadline:
            raise g.Limit('TimeBudgetExhausted')

    def charge(self, kind, n):
        self.check()
        if sum(self.work.values()) + n > self.limit:
            raise g.Limit('WorkBudgetExhausted')
        self.work[kind] += n

    def phase(self, n):
        self.charge('phase', n); self.calls += 1


def extrapolate(base, first, second, d, k):
    """Second difference is essential: alpha=-1 recovers the second map."""
    r = m.difference(first, base, d, k)
    v = m.difference(second, first, d, k) - r
    denominator = np.linalg.norm(v)
    if denominator <= 1e-14 or not np.isfinite(denominator):
        return second, -1.
    alpha = -float(np.clip(np.linalg.norm(r) / denominator, 1., 4.))
    return m.unpack(m.pack(base) - 2*alpha*r + alpha*alpha*v, d, k), alpha


def fit(data, k, seed, method='em', max_work=20000000, maps=200,
        seconds=120, tolerance=1e-4, starts=3):
    data = np.asarray(data)
    if (data.ndim != 2 or not 2 <= data.shape[1] <= 4 or not 30 <= len(data) <= 5000
        or not np.all(np.isfinite(data)) or not 1 <= k <= 3 or maps < 1
        or max_work < 0 or seconds <= 0 or tolerance <= 0 or not np.all(np.isfinite([seconds,tolerance,max_work,maps,starts])) or not 1 <= starts <= 3
        or method not in ('em', 'squarem')):
        raise ValueError('Invalid data/configuration')
    n, dimension = data.shape; d = dimension-1
    if np.linalg.eigvalsh(np.atleast_2d(np.cov(data[:, :-1].T, bias=True))).min() <= 1e-10:
        return dict(status='RankDeficient', components=[], starts=[], work={}, seconds=0.)
    begun = time.perf_counter(); budget = Budget(max_work, seconds)
    features = c.phase_features(data[:, :-1]); records = []; best = None; global_failure = None

    def evaluate(model):
        # Logical component-row visits, not equivalent FLOPs or old phase-only work.
        budget.charge('score', n*k); budget.charge('gradient', n*k)
        diagnostic = m.stationarity(data, model)
        if not np.isfinite(diagnostic['maximum']):
            raise g.Limit('NumericallyUnresolved')
        if min(diagnostic['minimumMass'], diagnostic['minimumEss']) < max(30, 5*d):
            raise g.Limit('InsufficientComponent')
        budget.check()
        return -n*diagnostic['objective'], diagnostic

    for start in range(starts):
        trace = []; gradients = []; current = None; diagnostic = None; value = None
        count = 0; accepted = rejected = 0; angular_unqualified = 0; alphas = []
        status = 'MapLimit'

        def step(model):
            nonlocal count, angular_unqualified
            if count >= maps:
                raise g.Limit('MapLimit')
            count += 1
            budget.charge('score', n*k)
            terms = np.array([np.log(a['weight']) + c.gvm_log(data, dict(components=[dict(a, weight=1.)]))
                              for a in model['components']])
            normalizer = g.logsumexp(terms, axis=0)
            if not np.all(np.isfinite(normalizer)):
                raise g.Limit('NumericallyUnresolved')
            responsibilities = np.exp(terms-normalizer); components = []
            for component, r in zip(model['components'], responsibilities):
                budget.charge('moments', n)
                fresh, _ = g.moments(data, r)
                phase, report = g.angular(np.array(component['coeff']), features, data[:, -1], r, budget)
                angular_unqualified += int(not report['qualified'])
                fresh.update(phase); components.append(fresh)
            candidate = dict(components=components)
            score, diag = evaluate(candidate)
            return candidate, score, diag

        def retain(model, score, diag):
            nonlocal current, value, diagnostic
            current, value, diagnostic = model, score, diag
            trace.append(score); gradients.append(diag['maximum'])

        try:
            labels = c.partitions(data, k, start, seed+start); components = []
            for j in range(k):
                r = (labels == j).astype(float); budget.charge('moments', n)
                component, _ = g.moments(data, r); budget.phase(n)
                real, imag = r @ np.cos(data[:, -1])/r.sum(), r @ np.sin(data[:, -1])/r.sum()
                component.update(coeff=np.zeros(features.shape[1]).tolist(), alpha=float(np.arctan2(imag, real)),
                                 kappa=g.concentration(min(1., float(np.hypot(real, imag)))))
                components.append(component)
            initial = dict(components=components); score, diag = evaluate(initial); retain(initial, score, diag)
            while count < maps:
                if diagnostic['maximum'] <= tolerance:
                    status = 'Stationary'; break
                base = current
                first, score, diag = step(base)
                if score < value-1e-10*n:
                    status = 'NonmonotoneMap'; break
                retain(first, score, diag)
                if method == 'em' or diag['maximum'] <= tolerance or count >= maps:
                    continue
                second, score, diag = step(first)
                if score < value-1e-10*n:
                    status = 'NonmonotoneMap'; break
                retain(second, score, diag)
                if diag['maximum'] <= tolerance or count >= maps:
                    continue
                trial, alpha = extrapolate(base, first, second, d, k); alphas.append(alpha)
                try:
                    trial_score, _ = evaluate(trial)
                    if trial_score < value-1e-10*n:
                        rejected += 1; continue
                    stable, stable_score, stable_diag = step(trial)
                    if stable_score < max(value, trial_score)-1e-10*n:
                        rejected += 1; continue
                    retain(stable, stable_score, stable_diag); accepted += 1
                except g.Limit as error:
                    if str(error) not in ('InsufficientComponent', 'NumericallyUnresolved'):
                        raise
                    rejected += 1
                except (ArithmeticError, np.linalg.LinAlgError):
                    rejected += 1
            if diagnostic is not None and diagnostic['maximum'] <= tolerance:
                status = 'Stationary'
        except g.Limit as error:
            status = str(error)
            if status in ('WorkBudgetExhausted', 'TimeBudgetExhausted'):
                global_failure = status
        except (ArithmeticError, np.linalg.LinAlgError) as error:
            status = 'NumericallyUnresolved:' + str(error)
        records.append(dict(start=start, status=status, trace=trace, gradients=gradients,
            maps=count, accepted=accepted, rejected=rejected, alphas=alphas,
            angularUnqualified=angular_unqualified))
        # Select likelihood, not convergence status; record the actual retained iterate.
        if value is not None and (best is None or value > best['trainScore']):
            best = dict(components=current['components'], trainScore=value,
                        diagnostic=diagnostic, status=status, selectedStart=start)
        if global_failure:
            break
    if best is None:
        best = dict(components=[], trainScore=None, diagnostic=None, status=records[-1]['status'])
    if global_failure:
        best['status'] = global_failure
    best.update(starts=records, work=budget.work, totalWork=sum(budget.work.values()),
                angularCalls=budget.calls, seconds=time.perf_counter()-begun)
    return best
