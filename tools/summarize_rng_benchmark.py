"""Validate a full rotated RNG benchmark and report timing at matched work/accuracy.

Usage: python -B tools/summarize_rng_benchmark.py LOG_OR_CSV [--export CSV]
No third-party dependencies. Never discard unfavorable runs.
"""
import argparse
import csv
import io
import math
from pathlib import Path
from statistics import median, mean

ALGORITHMS = ['L64X128MixRandom', 'Xoshiro256PlusPlus', 'PCG_RXS_M_XS_64', 'MT19937', 'Random']
PHILOX_ALGORITHMS = ['L64X128MixRandom', 'PHILOX_4X64']
HEADER = 'RNG,workload,algorithm,rep,seed,draws,seconds,mean,error,mcse,ess,maxWeight,accurate,covered95'


def parse(text, algorithms=ALGORITHMS):
    lines = [x.strip() for x in text.splitlines() if x.startswith('RNG,') and not x.startswith('RNG,workload,')]
    rows = list(csv.DictReader(io.StringIO(HEADER+'\n'+'\n'.join(lines))))
    keys = set()
    for r in rows:
        for field in ('rep', 'seed', 'draws'):
            r[field] = int(r[field])
        r['seconds'] = float(r['seconds'])
        if not math.isfinite(r['seconds']) or r['seconds'] <= 0:
            raise ValueError('Invalid elapsed time')
        if r['seed'] != 65537 + 104729*r['rep']:
            raise ValueError('Unplanned seed')
        key = (r['workload'], r['algorithm'], r['rep'], r['draws'])
        if key in keys:
            raise ValueError('Duplicate experiment')
        keys.add(key)
        if r['workload'] == 'inference':
            for field in ('mean', 'error', 'mcse', 'ess', 'maxWeight'):
                r[field] = float(r[field])
                if not math.isfinite(r[field]):
                    raise ValueError('Nonfinite diagnostic')
            if abs(r['error'] - (r['mean']-30/21)) > 1e-12:
                raise ValueError('Wrong oracle')
            if not (r['mcse'] >= 0 and 1 <= r['ess'] <= r['draws'] and 1/r['draws'] <= r['maxWeight'] <= 1):
                raise ValueError('Invalid diagnostics')
            for field, expected in [('accurate', abs(r['error']) <= .1/math.sqrt(21)),
                                    ('covered95', abs(r['error']) <= 1.959963984540054*r['mcse'])]:
                if r[field] != str(expected).lower():
                    raise ValueError('Inconsistent '+field)
                r[field] = expected
        elif any(r[f] != 'NA' for f in ('mean', 'error', 'mcse', 'ess', 'maxWeight', 'accurate', 'covered95')):
            raise ValueError('Unexpected primitive diagnostic')
    expected = {(kind, a, rep, n) for a in algorithms for rep in range(10)
                for kind in ('uniform', 'gaussian', 'inference')
                for n in ([2000, 8000, 32000] if kind == 'inference' else [1000000])}
    if expected != keys:
        raise ValueError(f'Incomplete/unexpected study: missing={len(expected-keys)}, extra={len(keys-expected)}')
    return rows, lines


def summarize(rows, algorithms=ALGORITHMS):
    for algorithm in algorithms:
        for kind in ('uniform', 'gaussian', 'inference'):
            for n in ([2000, 8000, 32000] if kind == 'inference' else [1000000]):
                group = [r for r in rows if (r['algorithm'], r['workload'], r['draws']) == (algorithm, kind, n)]
                timings = [r['seconds'] for r in group]
                stats = f'medianSeconds={median(timings):.6f},min={min(timings):.6f},max={max(timings):.6f}'
                if kind == 'inference':
                    stats += (f",rmse={math.sqrt(mean(r['error']**2 for r in group)):.6f},"
                              f"accurate={sum(r['accurate'] for r in group)}/10,coverage={sum(r['covered95'] for r in group)}/10,"
                              f"medianESS={median(r['ess'] for r in group):.2f}")
                print(algorithm, kind, n, stats, sep=',')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('input', type=Path)
    parser.add_argument('--export', type=Path)
    parser.add_argument('--profile', choices=['legacy', 'philox'], default='legacy')
    args = parser.parse_args()
    algorithms = PHILOX_ALGORITHMS if args.profile == 'philox' else ALGORITHMS
    rows, lines = parse(args.input.read_text(encoding='utf-8-sig'), algorithms)
    if args.export:
        args.export.write_text(HEADER+'\n'+'\n'.join(lines)+'\n', encoding='utf-8')
    print(f'Validated {len(rows)} complete benchmark rows.')
    summarize(rows, algorithms)
