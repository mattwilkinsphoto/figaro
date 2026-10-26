"""Separate numerical repair audit, not a rerun or waiver of the frozen pilot.

Independent NumPy/SciPy diagnostics reuse the previously inspected mathematical
definitions, including exact constant-row handling. No candidate imports.
Only retained development traces are accepted; confirmation is never executed.
"""
import argparse
import base64
import gzip
import json
import math
from pathlib import Path
import platform
import struct

import numpy as np
import scipy
from scipy.stats import norm, rankdata
from gvm_study_io import save, sha

FIELDS = ('mean', 'sd', 'rhat', 'bulk', 'tail', 'ess', 'mcse')
RTOL, ATOL = 3e-8, 2e-9  # Original independent audit tolerances, not relaxed.


def means(x):
    return np.array([r[0] if np.ptp(r) == 0 else r.mean() for r in x])


def variance(x):
    return 0.0 if np.ptp(x) == 0 else np.var(x, ddof=1)


def rh(x):
    w = np.mean([variance(r) for r in x]); b = variance(means(x))
    return math.sqrt((x.shape[1]-1)/x.shape[1]+b/w) if w > 0 else math.inf if b > 0 else math.nan


def effective(x):
    n = x.shape[1]
    centered = x-means(x)[:, None]
    spectrum = np.fft.rfft(centered, n=1 << (2*n-1).bit_length(), axis=1)
    ac = np.fft.irfft(spectrum*spectrum.conj(), axis=1)[:, :n]/n
    w = ac[:, 0].mean()*n/(n-1)
    v = w*(n-1)/n+variance(means(x))
    if not v > 0 or not math.isfinite(v):
        return math.nan
    rho = 1-(w-ac.mean(axis=0))/v; rho[0] = 1
    pairs = rho[:n//2*2].reshape(-1, 2).sum(axis=1)
    stop = np.flatnonzero(pairs <= 0)
    if len(stop):
        pairs = pairs[:stop[0]]
    tau = max(1., -1+2*np.minimum.accumulate(pairs).sum()) if len(pairs) else 1.
    return x.size/tau


def summary(a):
    assert a.ndim == 2 and a.shape[0] == 4 and a.shape[1] >= 4 and np.isfinite(a).all()
    n = a.shape[1]//2
    split = lambda x: np.concatenate((x[:, :n], x[:, -n:]), axis=0)
    rank = lambda x: norm.ppf((rankdata(x, method='average').reshape(x.shape)-.375)/(x.size+.25))
    flat = a.ravel(); largest = float(np.max(np.abs(flat)))
    exponent = max(-1022, math.frexp(largest)[1]-1) if largest else -1022
    scale = math.ldexp(1., exponent); small = a/scale
    ordered = np.sort(small.ravel())
    lo, hi = ordered[(len(flat)-1)//2], ordered[len(flat)//2]
    folded = np.abs((small-lo)/2+(small-hi)/2)
    rs = [rh(rank(split(a))), rh(rank(split(folded)))]; rs = [r for r in rs if not math.isnan(r)]
    cuts = np.quantile(flat, [.05, .95], method='linear')
    tails = [effective(split((a <= c).astype(float))) for c in cuts]
    ess = effective(split(small)); sd = math.sqrt(variance(small.ravel()))*scale
    values = [float(flat[0] if np.ptp(flat) == 0 else flat.mean()), sd,
              max(rs) if rs else math.nan, effective(rank(split(a))),
              min(tails) if all(math.isfinite(t) for t in tails) else math.nan,
              ess, sd/math.sqrt(ess) if ess > 0 else math.nan]
    return values, cuts, bool(np.any(np.ptp(a, axis=1) == 0))


def qualifies(query, values, constant):
    mean, sd, rhat, bulk, tail, ess, mcse = values
    required = (rhat, bulk, ess, mcse) + (() if query.startswith('tail') else (tail,))
    return (not constant and all(math.isfinite(v) for v in required) and
            rhat <= 1.01 and bulk >= 400 and ess >= 400 and mcse > 0 and
            (query.startswith('tail') or tail >= 400))


def exact(a, b):
    return (math.isnan(a) and math.isnan(b)) or struct.pack('>d', a) == struct.pack('>d', b)


def check_values(old, new, independent):
    np.testing.assert_allclose(new, independent, rtol=RTOL, atol=ATOL, equal_nan=True)
    assert all(exact(old[i], new[i]) for i in (0, 1, 2, 3, 5, 6)), 'Non-tail diagnostic changed'


def insert_unique(records, key, value):
    assert key not in records, f'Duplicate {key}'
    records[key] = value


def audit(root, replay, output):
    reports = {}; completed = 0
    for line in replay.read_text(encoding='utf-8').splitlines():
        v = line.split('\t')
        if v[0] == 'QR':
            insert_unique(reports, tuple(v[1:6]), (list(map(float, v[6:13])),
                list(map(float, v[13:15])), v[15] == 'true', v[16]))
        elif v[0] == 'REPLAY_COMPLETE':
            assert v[1:] == ['272', '5798']; completed += 1
    assert completed == 1 and len(reports) == 5798, 'Incomplete Scala replay'
    inputs = {}; seen = set(); attempts = set(); changed = []; decisions = []; cutoff_changes = []
    strict_changes = []; attempt_changes = []; all_results = []
    for index in range(8):
        path = root/f'development-{index}.log.gz'
        receipt_path = root/f'development-{index}.json'
        receipt = json.loads(receipt_path.read_text())
        assert receipt['exitCode'] == 0 and receipt['limitExceeded'] is None
        inputs[path.name] = sha(path); inputs[receipt_path.name] = sha(receipt_path)
        assert inputs[path.name] == receipt['sha256'], 'Original log hash mismatch'
        traces = {}; original = {}
        with gzip.open(path, 'rt', encoding='utf-8') as stream:
            for line in stream:
                v = line.rstrip('\r\n').split('\t')
                if v[0] != 'DP':
                    continue
                name, method, seed = v[1:4]
                assert int(seed) == 26011001+index
                if v[4] == 'TRACE' and v[5] != 'training':
                    key = tuple(v[5:7]); c = int(v[7])
                    phase_index = {'qualification': 20, 'production': 30, 'strict-pilot': 40}[v[5]]
                    assert int(v[8]) == int(seed)*100+phase_index+c
                    a = np.frombuffer(base64.b64decode(v[9], validate=True), dtype='>f8').astype(float)
                    insert_unique(traces, (*key, c), a)
                elif v[4] == 'SUMMARY':
                    insert_unique(original, tuple(v[5:7]), (list(map(float, v[7:14])), v[14] == 'true'))
                elif v[4] == 'END':
                    attempt = (name, method, seed)
                    assert attempt not in attempts; attempts.add(attempt)
                    assert v[5] in ('delivered', 'refused')
                    assert set(original) == {(ph, q) for ph, q, c in traces}
                    phase_eligibility = {}
                    for (phase, query), (old, old_eligible) in original.items():
                        key = (*attempt, phase, query)
                        assert key not in seen; seen.add(key)
                        arrays = np.stack([traces[phase, query, c] for c in range(4)])
                        independent, cuts, constant = summary(arrays)
                        new, scala_cuts, scala_constant, warnings = reports[key]
                        assert scala_constant == constant
                        check_values(old, new, independent)
                        # Compare indicator membership, not demand cross-library bitwise interpolation.
                        ordered = np.sort(arrays.ravel())
                        for j, p in enumerate((.05, .95)):
                            np.testing.assert_array_equal(arrays <= scala_cuts[j], arrays <= cuts[j])
                            t = p*(len(ordered)-1); lo = int(t); w = t-lo
                            lower, upper = ordered[lo], ordered[min(lo+1, len(ordered)-1)]
                            previous = lower*(1-w)+upper*w
                            if lower == upper:
                                assert exact(scala_cuts[j], float(lower)), 'Tie not exact'
                            else:
                                assert exact(scala_cuts[j], float(previous)), 'Unequal arithmetic changed'
                            count = int(np.count_nonzero((arrays <= previous) != (arrays <= scala_cuts[j])))
                            if count:
                                assert lower == upper, 'Unexpected non-tied change'
                                cutoff_changes.append(dict(key=key, p=p, affectedDraws=count,
                                    oldCut=float(previous), newCut=scala_cuts[j]))
                        new_eligible = qualifies(query, new, constant)
                        assert old_eligible == qualifies(query, old, constant)
                        assert new_eligible == qualifies(query, independent, constant)
                        phase_eligibility[phase, query] = new_eligible
                        if old_eligible != new_eligible:
                            decisions.append(dict(key=key, old=old_eligible, new=new_eligible))
                        if not exact(old[4], new[4]):
                            changed.append(dict(key=key, oldTail=old[4], newTail=new[4], independentTail=independent[4]))
                        if phase == 'strict-pilot' and query in ('x0', 'x1'):
                            # Default strict calibration uses bulk/tail/mean ESS and R-hat,
                            # not MCSE. Its separate constant guard is retained.
                            def strict(s):
                                return not constant and arrays.shape[1] >= 500 and math.isfinite(s[2]) and s[2] <= 1.01 and all(math.isfinite(s[i]) and s[i] >= 400 for i in (3, 4, 5))
                            if strict(old) != strict(new):
                                strict_changes.append(dict(key=key, old=strict(old), new=strict(new)))
                        all_results.append(dict(key=key, original=old, repaired=new, independent=independent,
                            eligible=new_eligible, warnings=warnings))
                    if method in ('candidate', 'diagonal'):
                        eligible = all(ok for (ph, q), ok in phase_eligibility.items() if ph == 'qualification')
                        if eligible != (v[5] == 'delivered'):
                            attempt_changes.append(dict(key=attempt, original=v[5], eligible=eligible))
                    traces.clear(); original.clear()
        assert not traces and not original, 'Truncated original log'
        print('Independently checked retained file', index, 'summaries', len(seen), flush=True)
    assert len(attempts) == 272 and seen == set(reports), 'Missing/extra records'
    # JSON never serializes NaN/Infinity as unstandardized numeric tokens.
    def clean(x):
        if isinstance(x, (float, np.floating)) and not math.isfinite(x):
            return 'NaN' if math.isnan(x) else 'Infinity' if x > 0 else '-Infinity'
        if isinstance(x, dict): return {k: clean(v) for k, v in x.items()}
        if isinstance(x, (tuple, list)): return [clean(v) for v in x]
        return x
    result = dict(passed=True, attempts=len(attempts), summaries=len(seen),
        inputSha256=inputs, replaySha256=sha(replay), python=platform.python_version(),
        numpy=np.__version__, scipy=scipy.__version__, rtol=RTOL, atol=ATOL,
        tailChanges=changed, cutoffChanges=cutoff_changes, projectionDecisionChanges=decisions,
        attemptDecisionChanges=attempt_changes, strictCalibrationDecisionChanges=strict_changes,
        nonTailDiagnosticsBitExact=True, originalFrozenAuditStillFailed=True,
        pilotPromotion=False, confirmationSeedsConsumed=0, results=all_results)
    save(output, clean(result))
    print(json.dumps({k: v for k, v in result.items() if k not in
        ('results', 'tailChanges', 'cutoffChanges', 'inputSha256')}, indent=2))
    print('Tail changes:', len(changed), 'cutoff membership changes:', len(cutoff_changes))


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('original', type=Path); parser.add_argument('replay', type=Path)
    parser.add_argument('output', type=Path)
    args = parser.parse_args()
    audit(args.original, args.replay, args.output)
