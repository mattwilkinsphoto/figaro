"""Validate three complete setup-inclusive JVM studies; never enforce timing thresholds."""
import argparse
import math
import re
import statistics
from pathlib import Path

CASES = ('ordinary', 'opposed', 'linear', 'unequal', 'curved')
METHODS = ('audited', 'candidate')


def parse(text):
    """Return three complete runs with stable accuracy/work checks; reject malformed evidence."""
    runs, pids, current = [], set(), None
    for line in text.splitlines():
        line = line.removeprefix('[info] ')
        if not line.startswith('GVM_TAIL_JVM_'):
            continue
        tag = line.split()[0]
        pairs = re.findall(r'(\w+)=(\S+)', line)
        f = dict(pairs)
        if len(f) != len(pairs):
            raise ValueError('duplicate fields')
        if tag == 'GVM_TAIL_JVM_ENV':
            if (current is not None or f['pid'] in pids or f['rounds'] != '7' or
                    float(f['tolerance']) != 1e-8 or int(f['processors']) <= 0 or int(f['maxHeap']) <= 0):
                raise ValueError('invalid environment')
            pids.add(f['pid'])
            current = dict(checks={}, timings={})
        elif current is None:
            raise ValueError('record outside run')
        elif tag == 'GVM_TAIL_JVM_CHECK':
            name = f['case']
            if name not in CASES or name in current['checks']:
                raise ValueError('invalid case')
            for method in ('before', 'after'):
                error, radius = float(f[method+'Error']), float(f[method+'Radius'])
                if (not math.isfinite(error) or not 0 <= error <= 1e-8 or
                        not math.isfinite(radius) or not 4 <= radius <= 16 or
                        not 5 <= int(f[method+'Work']) <= 50000):
                    raise ValueError('invalid numerical control')
            if float(f['afterRadius']) > float(f['beforeRadius']):
                raise ValueError('candidate radius grew')
            current['checks'][name] = f
        elif tag == 'GVM_TAIL_JVM_TIMING':
            key = f['case'], f['method'], int(f['round'])
            batch, value = int(f['batch']), float(f['nsPerCall'])
            if (key[0] not in current['checks'] or key[1] not in METHODS or not 0 <= key[2] < 7 or
                    key in current['timings'] or not 1 <= batch <= 4096 or batch & (batch-1) or
                    not math.isfinite(value) or value <= 0):
                raise ValueError('invalid timing')
            current['timings'][key] = value
        elif tag == 'GVM_TAIL_JVM_COMPLETE':
            expected = {(c, m, r) for c in CASES for m in METHODS for r in range(7)}
            if (f['cases'] != '5' or f['methods'] != '10' or f['rounds'] != '7' or
                    f['sinkFinite'] != 'true' or set(current['checks']) != set(CASES) or
                    set(current['timings']) != expected or
                    (runs and current['checks'] != runs[0]['checks'])):
                raise ValueError('incomplete or inconsistent run')
            runs.append(current)
            current = None
        else:
            raise ValueError('unknown record')
    if current is not None or len(runs) != 3:
        raise ValueError('incomplete run set')
    return runs


def summarize(runs):
    """Format median-of-JVM-medians microseconds, JVM ranges and baseline/candidate ratios."""
    lines = []
    for case in CASES:
        medians = {}
        for method in METHODS:
            values = [statistics.median(run['timings'][case, method, r] for r in range(7))/1000 for run in runs]
            medians[method] = statistics.median(values)
            lines.append(f'{case} {method}: median={medians[method]:.3f} us JVM-range=[{min(values):.3f},{max(values):.3f}]')
        lines.append(f'{case}: baseline/candidate={medians["audited"]/medians["candidate"]:.3f}x')
    return '\n'.join(lines)


def main():
    """Read the CLI log path and print its validated summary; no file writes."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('log', type=Path)
    args = parser.parse_args()
    print(summarize(parse(args.log.read_text(encoding='utf-8-sig'))))


if __name__ == '__main__':
    main()
