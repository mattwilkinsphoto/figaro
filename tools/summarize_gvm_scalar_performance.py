"""Validate complete scalar study evidence; never rank refusals as successful speedups."""
import argparse
import math
import re
import statistics
from pathlib import Path

ORACLES = {
    'gaussian': .5,
    'constant-opposed': 47.1275755018718045,
    'linear-moderate': .06387735648997029028485409595318461855683,
    'curved-unequal': .355509912840583167741149,
    'linear-concentrated': 1.29442047240411212017621814538838746359,
    'opposed-weak': 47.1275754862468045235184686645218055091,
    'opposed-moderate': 45.0461096112953972045121001065173035263,
    'curved-concentrated': 1.3162805891364137825114985731082026827,
}
METHODS = {(case, method) for case in ORACLES for method in ('fourier', 'positive')}
REFUSALS = {('opposed-weak', 'fourier'), ('opposed-moderate', 'fourier')}


def fields(line):
    """Read unique key/value fields; reject ambiguous duplicate keys."""
    pairs = re.findall(r'(\w+)=(\S+)', line)
    result = dict(pairs)
    if len(pairs) != len(result):
        raise ValueError('duplicate field')
    return result


def parse(text, expected_runs=3):
    """Return validated per-run records, requiring every method and round exactly once."""
    if expected_runs < 1:
        raise ValueError('positive run count required')
    runs, pids = [], set()
    current = None
    for line in text.splitlines():
        line = line.removeprefix('[info] ')
        if not line.startswith('GVM_SCALAR_'):
            continue
        tag, row = line.split()[0], fields(line)
        if tag == 'GVM_SCALAR_ENV':
            if current is not None:
                raise ValueError('missing completion')
            pid, rounds = int(row['pid']), int(row['rounds'])
            if pid <= 0 or pid in pids or row['timed'] != 'true' or not 3 <= rounds <= 31:
                raise ValueError('not distinct measured JVM runs')
            if float(row['tolerance']) != 1e-8:
                raise ValueError('wrong accuracy target')
            if row['warmupMs'] != '500' or row['calibrationTargetMs'] != '20' or row['maxBatch'] != '65536':
                raise ValueError('wrong measurement protocol')
            if int(row['processors']) < 1 or int(row['maxHeap']) < 1:
                raise ValueError('invalid environment')
            pids.add(pid)
            current = dict(env=row, rounds=rounds, methods={}, timings={})
            continue
        if current is None:
            raise ValueError('record outside run')
        if tag == 'GVM_SCALAR_METHOD':
            pair = row['case'], row['method']
            if pair not in METHODS or pair in current['methods']:
                raise ValueError('unknown or duplicate method')
            oracle = float(row['oracle'])
            if oracle != ORACLES[pair[0]]:
                raise ValueError('wrong oracle')
            if pair in REFUSALS:
                if row['status'] != 'NumericallyUnresolved' or row['distance'] != 'none' or row['error'] != 'none':
                    raise ValueError('refusal mislabeled as success')
            else:
                expected = 'Resolved' if pair[1] == 'fourier' else 'Estimated'
                distance, error = float(row['distance']), float(row['error'])
                if (row['status'] != expected or not math.isfinite(distance) or distance < 0 or
                        not math.isfinite(error) or not 0 <= error <= 1e-8 or
                        abs(distance-oracle) > 1e-8 or error != abs(distance-oracle)):
                    raise ValueError('unmatched accuracy')
            cap, unit = (256, 'harmonics') if pair[1] == 'fourier' else (50000, 'evaluations')
            if not 0 <= int(row['work']) <= cap or row['unit'] != unit:
                raise ValueError('wrong work budget/units')
            expected_path = {'gaussian': 'gaussian', 'constant-opposed': 'constant-angular'}.get(pair[0])
            if expected_path is not None:
                if row['path'] != expected_path or int(row['work']) != 0:
                    raise ValueError('analytic shortcut mismatch')
            elif row['path'] != ('fourier' if pair[1] == 'fourier' else 'positive-integration'):
                raise ValueError('wrong algorithm path')
            current['methods'][pair] = row
        elif tag == 'GVM_SCALAR_TIMING':
            pair = row['case'], row['method']
            key = pair + (int(row['round']),)
            count, ns = int(row['batch']), float(row['nsPerCall'])
            if (pair not in current['methods'] or key in current['timings'] or
                    not 0 <= key[2] < current['rounds'] or not 1 <= count <= 65536 or
                    count & (count-1) or not math.isfinite(ns) or ns <= 0):
                raise ValueError('invalid timing')
            current['timings'][key] = ns
        elif tag == 'GVM_SCALAR_COMPLETE':
            expected = {(c, m, r) for c, m in METHODS for r in range(current['rounds'])}
            if (row['fixtures'] != '8' or row['methods'] != '16' or row['accepted'] != '14' or
                    row['refused'] != '2' or int(row['rounds']) != current['rounds'] or
                    row['sinkFinite'] != 'true' or set(current['methods']) != METHODS or
                    set(current['timings']) != expected):
                raise ValueError('incomplete study population')
            runs.append(current)
            current = None
        else:
            raise ValueError('unknown study record')
    if current is not None or len(runs) != expected_runs:
        raise ValueError('incomplete run set')
    # Numerical results must be reproducible across the fresh JVMs on this fixed run set.
    if any(run['methods'] != runs[0]['methods'] for run in runs[1:]):
        raise ValueError('numerical records differ across runs')
    return runs


def summarize(runs):
    """Format median-of-JVM-medians timings and ranges, with refusals explicitly labeled."""
    lines = [f'Validated {len(runs)} fresh JVMs; 14 accepted and 2 refusals per run. Times in microseconds.']
    medians = {}
    for case, method in sorted(METHODS):
        values = [statistics.median(run['timings'][case, method, r] for r in range(run['rounds']))/1000
                  for run in runs]
        median = statistics.median(values)
        medians[case, method] = median
        status = 'REFUSAL-COST' if (case, method) in REFUSALS else 'accepted'
        lines.append(f'{case} {method} {status}: median={median:.3f} JVM-range=[{min(values):.3f},{max(values):.3f}]')
    for case in ORACLES:
        if (case, 'fourier') in REFUSALS:
            lines.append(f'{case}: no matched-success speed ratio (Fourier refused)')
        else:
            lines.append(f'{case}: positive/fourier time ratio={medians[case,"positive"]/medians[case,"fourier"]:.3f}')
    return '\n'.join(lines)


def main():
    """Validate a log path, optionally overriding the expected number of independent runs."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('log', type=Path)
    parser.add_argument('--runs', type=int, default=3)
    args = parser.parse_args()
    print(summarize(parse(args.log.read_text(encoding='utf-8-sig'), args.runs)))


if __name__ == '__main__':
    main()
