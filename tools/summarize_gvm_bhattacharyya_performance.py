"""Validate complete fresh-JVM study output and summarize per-JVM medians; stdlib only."""
import argparse
import math
import re
import statistics
from pathlib import Path

CASES = ('linear-1d','linear-2d','curved-2d','linear-6d')
GENERIC = ('guarded','tensor-build-evaluate','tensor-reuse')
REDUCED = ('reduced-build-evaluate','reduced-reuse')
METHODS = {(c,m) for c in CASES for m in GENERIC+(REDUCED if c != 'curved-2d' else ())}
ACCURACY = set(CASES) | {c+'-reduced' for c in CASES if c != 'curved-2d'}


def parse(text, expected_runs=3):
    """Reject incomplete, duplicated, nonfinite or unmatched study records."""
    runs = []
    current = None
    pids = set()
    for line in text.splitlines():
        line = line.removeprefix('[info] ')
        if not line.startswith('GVM_'): continue
        tag = line.split()[0]
        fields = dict(re.findall(r'(\w+)=(\S+)',line))
        if tag == 'GVM_ENV':
            if current is not None: raise ValueError('missing completion')
            pid = int(fields['pid'])
            rounds = int(fields['rounds'])
            if pid in pids or fields['timed'] != 'true' or not 3 <= rounds <= 31:
                raise ValueError('not distinct measured JVM runs')
            if float(fields['tolerance']) != 1e-6: raise ValueError('wrong accuracy target')
            pids.add(pid)
            current = dict(rounds=rounds, timings={}, methods=set(), series=set(), accepted=set())
            continue
        if current is None: raise ValueError('record outside run')
        if tag in ('GVM_ACCURACY','GVM_SERIES'):
            error = float(fields['error'])
            if not math.isfinite(error) or error < 0: raise ValueError('invalid error')
            if tag == 'GVM_SERIES':
                if error > 1e-6 or fields['case'] in current['series']: raise ValueError('invalid series')
                current['series'].add(fields['case'])
            else:
                if fields['accepted'] not in ('true','false') or (fields['accepted']=='true') != (error <= 1e-6):
                    raise ValueError('unmatched quadrature accuracy')
                if fields['accepted']=='true':
                    if fields['case'] in current['accepted']: raise ValueError('duplicate selected rule')
                    current['accepted'].add(fields['case'])
        elif tag == 'GVM_METHOD':
            pair = fields['case'],fields['method']
            if pair not in METHODS or pair in current['methods']: raise ValueError('invalid method')
            current['methods'].add(pair)
        elif tag == 'GVM_TIMING':
            key = fields['case'],fields['method'],int(fields['round'])
            ns = float(fields['nsPerCall'])
            count = int(fields['batch'])
            if key in current['timings'] or not math.isfinite(ns) or ns <= 0 or not 1 <= count <= 4096:
                raise ValueError('invalid timing')
            current['timings'][key] = ns
        elif tag == 'GVM_COMPLETE':
            expected = {(c,m,r) for c,m in METHODS for r in range(current['rounds'])}
            if (int(fields['fixtures']) != 4 or int(fields['methods']) != 18 or
                int(fields['rounds']) != current['rounds'] or fields['sinkFinite'] != 'true' or
                current['methods'] != METHODS or current['series'] != set(CASES) or
                current['accepted'] != ACCURACY or set(current['timings']) != expected):
                raise ValueError('incomplete study matrix')
            runs.append(current)
            current = None
        else: raise ValueError('unknown study record')
    if current is not None or len(runs) != expected_runs: raise ValueError('incomplete run set')
    return runs


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('log',type=Path)
    parser.add_argument('--runs',type=int,default=3)
    args = parser.parse_args()
    if args.runs < 1: parser.error('--runs must be positive')
    runs = parse(args.log.read_text(encoding='utf-8-sig'),args.runs)
    print(f'Validated {len(runs)} runs; 18 methods per run. Microseconds per call:')
    for c,m in sorted(METHODS):
        values = [statistics.median(run['timings'][c,m,r] for r in range(run['rounds']))/1000 for run in runs]
        print(f'{c} {m}: median={statistics.median(values):.3f} JVM-range=[{min(values):.3f},{max(values):.3f}]')


if __name__ == '__main__': main()
