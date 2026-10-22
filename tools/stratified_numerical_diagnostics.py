"""INF-15 diagnostic arithmetic, not a runtime sampler or release gate."""
import argparse
from bisect import bisect_right
from collections import Counter
from decimal import Decimal as D, localcontext, MAX_EMAX, MIN_EMIN
import hashlib
import json
import math
from pathlib import Path
import sys
import time
from continuous_filter_reference import Output


def scientific_rows(path):
    rows = {}
    for line in Path(path).open(encoding='utf-8-sig'):
        if 'SCHEDULE_ROW ' not in line:
            continue
        fields = line.split('SCHEDULE_ROW ', 1)[1].rstrip('\r\n').split('\t')
        assert len(fields) == 25
        if not fields[1].startswith('strat-'):
            continue
        key = tuple(fields[:5])
        assert key not in rows
        rows[key] = fields[:23] + fields[24:]  # Timing only is excluded.
    return rows


def high_precision(logs, precision):
    with localcontext() as ctx:
        ctx.prec = precision
        ctx.Emax, ctx.Emin = MAX_EMAX, MIN_EMIN
        peak = D.from_float(max(logs))
        terms = [(D.from_float(x)-peak).exp() if math.isfinite(x) else D(0) for x in logs]
        total = sum(terms, D(0))
        weights = [x/total for x in terms]
        cumulative = []
        acc = D(0)
        for w in weights:
            acc += w
            cumulative.append(acc)
        cumulative[-1] = D(1)
        return weights, cumulative


def cdf_from_weights(weights, logs, compensated=False):
    last = max(i for i, x in enumerate(logs) if math.isfinite(x))
    acc = correction = 0.0
    result = []
    for i, w in enumerate(weights):
        if i >= last:
            acc = 1.0
        elif compensated:
            y = w-correction
            t = acc+y
            correction = (t-acc)-y
            acc = t
        else:
            acc += w
        result.append(acc)
    return result


def flags(logs, weights, cdf):
    last = max(i for i, x in enumerate(logs) if math.isfinite(x))
    events = []
    for i, (l, w, c) in enumerate(zip(logs, weights, cdf)):
        if not math.isfinite(l):
            continue
        before = cdf[i-1] if i else 0.0
        reasons = []
        if w == 0:
            reasons.append('ordinary_underflow')
        if c > 1:
            reasons.append('cumulative_overshoot')
        if c <= before:
            reasons.append('endpoint_collapse' if i == last else 'interior_stagnation')
        if reasons:
            events.append((i, reasons))
    return events


def metrics(logs, ordinary, cdf, p, ref):
    with localcontext() as ctx:
        ctx.prec = 140
        before = [D(0)] + [D.from_float(x) for x in cdf[:-1]]
        q = [D.from_float(x)-a for x, a in zip(cdf, before)]
        monotone = all(x >= 0 for x in q) and cdf[-1] == 1 and max(cdf) <= 1
        return dict(
            lost_mass=sum((w for w, v in zip(p, q) if v <= 0), D(0)),
            underflow_mass=sum((w for w, v in zip(p, ordinary) if v == 0), D(0)),
            max_cdf_error=max(abs(D.from_float(c)-r) for c, r in zip(cdf, ref)),
            normalization_l1=sum((abs(D.from_float(w)-r) for w, r in zip(ordinary, p)), D(0)),
            total_variation=sum((abs(w-r) for w, r in zip(q, p)), D(0))/2 if monotone else None,
            monotone=monotone)


def describe(logs, ordinary, cdf):
    p80, c80 = high_precision(logs, 80)
    p140, c140 = high_precision(logs, 140)
    with localcontext() as ctx:
        ctx.prec = 140
        difference = max(abs(a-b) for a, b in zip(p80+c80, p140+c140))
    assert difference < D('1e-60'), 'Reference precision screen failed'
    events = flags(logs, ordinary, cdf)
    candidates = dict(original=cdf,
                      compensated=cdf_from_weights(ordinary, logs, True),
                      rounded_high_precision=[float(x) for x in c140])
    probes = sorted(set([(j+.5)/len(logs) for j in range(len(logs))] +
                        [u for i, _ in events[:1] for u in
                         (math.nextafter(cdf[i], 0), cdf[i], math.nextafter(cdf[i], 1)) if 0 <= u < 1]))
    reports = {}
    for name, candidate in candidates.items():
        m = metrics(logs, ordinary, candidate, p140, c140)
        # These forced boundary points are diagnostics, not a probability estimate.
        m['probe_mismatches'] = sum(bisect_right(candidate, u) != bisect_right(c140, D.from_float(u)) for u in probes) if m['monotone'] else None
        m['strict_guard_refuses'] = bool(flags(logs, ordinary, candidate))
        reports[name] = {k: str(v) if isinstance(v, D) else v for k, v in m.items()}
    return dict(first_index=events[0][0] if events else None,
                first_flags=events[0][1] if events else [],
                all_flags=dict(Counter(r for _, rs in events for r in rs)),
                log_range=max(logs)-min(x for x in logs if math.isfinite(x)),
                precision_difference=str(difference), probes=len(probes), methods=reports)


def analyze(replay, original, out):
    baseline, actual = scientific_rows(original), scientific_rows(replay)
    assert actual == baseline, 'Replay changed scientific/status/work fields'
    assert len(actual) == 155648
    records = []
    for line in Path(replay).open(encoding='utf-8-sig'):
        if 'STRAT_DIAG ' not in line:
            continue
        f = line.split('STRAT_DIAG ', 1)[1].strip().split('\t')
        assert len(f) == 9
        logs, ordinary, cdf = [[float.fromhex(x) for x in v.split(',')] for v in f[6:]]
        assert len(logs) == len(ordinary) == len(cdf) == 256
        assert cdf == cdf_from_weights(ordinary, logs)
        result = describe(logs, ordinary, cdf)
        assert bool(result['first_flags']) == (f[4] == 'refused')
        result.update(scenario=f[0], method=f[1], seed=int(f[2]), step=int(f[3]), kind=f[4], previous_relative_ess=float(f[5]))
        records.append(result)
        if len(records) % 256 == 0:
            print('ANALYZED', len(records), flush=True)
    assert len(records) == 6144
    assert len({(r['scenario'], r['method'], r['seed']) for r in records}) == 6144
    failed = [r for r in records if r['kind'] == 'refused']
    assert len(failed) == 5468
    groups = []
    for scenario in sorted({r['scenario'] for r in records}):
        for method in sorted({r['method'] for r in records}):
            rs = [r for r in records if r['scenario'] == scenario and r['method'] == method]
            fs = [r for r in rs if r['kind'] == 'refused']
            groups.append(dict(scenario=scenario, method=method, vectors=len(rs), refused=len(fs),
                               first_reason=dict(Counter(r['first_flags'][0] for r in fs))))
    controls = []
    for name, logs in [('dominant_last_tiny',[0.,-40.]),('tiny_first',[-40.,0.]),
                       ('underflow_last',[0.,-1000.]),('underflow_first',[-1000.,0.]),
                       ('true_zeros',[-math.inf,0.,-math.inf]),('equal',[0.]*256)]:
        terms = [math.exp(x-max(logs)) for x in logs]
        weights = [x/math.fsum(terms) for x in terms]
        controls.append(dict(name=name, result=describe(logs,weights,cdf_from_weights(weights,logs))))
    summary = dict(scientific_rows_matched=len(actual), vectors=len(records), refused=len(failed),
                   first_reason=dict(Counter(r['first_flags'][0] for r in failed)),
                   first_flag_combinations=dict(Counter('+'.join(r['first_flags']) for r in failed)),
                   groups=groups, controls=controls, precision='80/140 decimal digits',
                   max_precision_difference=str(max(D(r['precision_difference']) for r in records)),
                   python=sys.version, original_sha256=sha(original), replay_sha256=sha(replay))
    summary['methods'] = {}
    for name in records[0]['methods']:
        rs = [r['methods'][name] for r in records]
        summary['methods'][name] = dict(strict_refusals=sum(r['strict_guard_refuses'] for r in rs),
            nonmonotone=sum(not r['monotone'] for r in rs),
            max_lost_mass=str(max(D(r['lost_mass']) for r in rs)),
            max_cdf_error=str(max(D(r['max_cdf_error']) for r in rs)),
            max_total_variation=str(max(D(r['total_variation']) for r in rs if r['total_variation'] is not None)),
            probe_mismatches=sum(r['probe_mismatches'] or 0 for r in rs))
    out.json('vectors.json', records)
    out.json('summary.json', summary)
    print(json.dumps({k: summary[k] for k in ('scientific_rows_matched','vectors','refused','first_reason','methods','max_precision_difference')}), flush=True)


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--replay', required=True)
    parser.add_argument('--original', required=True)
    parser.add_argument('--output', required=True)
    parser.add_argument('--acl-script')
    args = parser.parse_args()
    print('START_UTC', time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime()), flush=True)
    analyze(args.replay, args.original, Output(args.output, args.acl_script))
    print('COMPLETE_UTC', time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime()), flush=True)


if __name__ == '__main__':
    main()
