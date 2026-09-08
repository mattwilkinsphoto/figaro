"""Validate complete, predeclared study rows and summarize accuracy, not just throughput.

Accepts an sbt log or exported CSV. --export mechanically extracts the evidence CSV.
"""
import argparse
import csv
import io
import math
from pathlib import Path
from statistics import median, mean

SEEDS = [1009 + i*7919 for i in range(30)]
BUDGETS = [2000, 20000, 200000]
RNGS = ['Random', 'L64X128MixRandom']
BACKENDS = ['L64X128MixRandom', 'Xoshiro256PlusPlus', 'PCG_RXS_M_XS_64', 'MT19937', 'Random']
REFERENCES = {'gamma': [2.107869973169636, 1.844729346924452],
              'dirichlet': [0.841016150722901, 1.910950755236635, 2.640339278723853]}
SD = {'gamma': [.1967401705530933, .1970468052314994],
      'dirichlet': [.0608399843880491, .1434315061608727, .2005600807508122]}
HEADER = 'SV,mode,family,rng,seed,draws,parameter,mean,error,mcse,ess,maxWeight,covered95,withinPointOnePosteriorSd,seconds,underflows'


def parse(text):
    lines = [line.strip() for line in text.splitlines()
             if line.startswith('SV,') and not line.startswith('SV,mode,')]
    rows = list(csv.DictReader(io.StringIO(HEADER+'\n'+'\n'.join(lines))))
    keys = set()
    for row in rows:
        for key in ('seed', 'draws', 'parameter', 'underflows'):
            row[key] = int(row[key])
        for key in ('mean', 'error', 'mcse', 'ess', 'maxWeight', 'seconds'):
            row[key] = float(row[key])
            if not math.isfinite(row[key]):
                raise ValueError('Non-finite '+key)
        for key in ('covered95', 'withinPointOnePosteriorSd'):
            if row[key] not in ('true', 'false'):
                raise ValueError('Invalid boolean')
            row[key] = row[key] == 'true'
        family, i, n = row['family'], row['parameter'], row['draws']
        if family not in REFERENCES or not 0 <= i < len(REFERENCES[family]):
            raise ValueError('Unknown parameter')
        if not (1-1e-10 <= row['ess'] <= n+1e-8 and 1/n-1e-10 <= row['maxWeight'] <= 1+1e-10
                and row['mcse'] >= 0 and row['seconds'] >= 0 and 0 <= row['underflows'] <= n):
            raise ValueError('Invalid diagnostics')
        if abs(row['error'] - (row['mean']-REFERENCES[family][i])) > 1e-12:
            raise ValueError('Reference error mismatch')
        if row['covered95'] != (abs(row['error']) <= 1.959963984540054*row['mcse']):
            raise ValueError('Coverage mismatch')
        if row['withinPointOnePosteriorSd'] != (abs(row['error']) <= .1*SD[family][i]):
            raise ValueError('Accuracy mismatch')
        key = tuple(row[k] for k in ('mode', 'family', 'rng', 'seed', 'draws', 'parameter'))
        if key in keys:
            raise ValueError('Duplicate experiment')
        keys.add(key)
    modes = ('backends',) if {r['mode'] for r in rows} == {'backends'} else ('kernel', 'graph')
    expected = {(mode, family, rng, seed, n, i)
                for mode in modes for family in REFERENCES
                for rng in (BACKENDS if mode == 'backends' else RNGS if mode == 'kernel' else ['Random'])
                for seed in (SEEDS if mode != 'graph' else SEEDS[:3])
                for n in (BUDGETS if mode != 'graph' else [2000])
                for i in range(len(REFERENCES[family]))}
    if keys != expected:
        raise ValueError(f'Incomplete/unexpected study: missing={len(expected-keys)}, extra={len(keys-expected)}')
    return rows, lines


def summarize(rows):
    print('mode,family,rng,draws,medianESS,medianMaxWeight,RMSEperParameter,coverage95perParameter,accuracyPassesPerParameter')
    for mode in (('backends',) if {r['mode'] for r in rows} == {'backends'} else ('kernel', 'graph')):
        for family in REFERENCES:
            for rng in (BACKENDS if mode == 'backends' else RNGS if mode == 'kernel' else ['Random']):
                for n in (BUDGETS if mode != 'graph' else [2000]):
                    selected = [r for r in rows if (r['mode'], r['family'], r['rng'], r['draws']) == (mode, family, rng, n)]
                    by_parameter = [[r for r in selected if r['parameter'] == i] for i in range(len(REFERENCES[family]))]
                    rmse = [math.sqrt(mean(r['error']**2 for r in group)) for group in by_parameter]
                    coverage = [sum(r['covered95'] for r in group) for group in by_parameter]
                    accuracy = [sum(r['withinPointOnePosteriorSd'] for r in group) for group in by_parameter]
                    print(mode, family, rng, n, round(median(r['ess'] for r in selected), 3),
                          round(median(r['maxWeight'] for r in selected), 4),
                          [round(v, 5) for v in rmse], coverage, accuracy, sep=',')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('input', type=Path)
    parser.add_argument('--export', type=Path)
    args = parser.parse_args()
    rows, lines = parse(args.input.read_text(encoding='utf-8-sig'))
    if args.export:
        args.export.write_text(HEADER+'\n'+'\n'.join(lines)+'\n', encoding='utf-8')
    print(f'Validated {len(rows)} parameter rows; every predeclared experiment retained.')
    summarize(rows)
