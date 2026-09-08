"""Validate frozen-control versus audited-totals timings; no timing pass/fail thresholds."""
import argparse
import math
import re
import statistics
from pathlib import Path

CASES = {'ordinary': 712, 'opposed': 764, 'curved': 25098}


def parse(text):
    """Require three independent complete JVMs with identical-work accuracy checks."""
    runs, pids = [], set()
    current = None
    for line in text.splitlines():
        line = line.removeprefix('[info] ')
        if not line.startswith('GVM_AUDIT_'):
            continue
        tag = line.split()[0]
        pairs = re.findall(r'(\w+)=(\S+)', line)
        f = dict(pairs)
        if len(f) != len(pairs):
            raise ValueError('duplicate fields')
        if tag == 'GVM_AUDIT_ENV':
            if current is not None or f['pid'] in pids or f['rounds'] != '7' or float(f['tolerance']) != 1e-8:
                raise ValueError('invalid run')
            pids.add(f['pid'])
            current = dict(checks=set(), timings={})
        elif current is None:
            raise ValueError('record outside run')
        elif tag == 'GVM_AUDIT_CHECK':
            name, error = f['case'], float(f['error'])
            if (name not in CASES or name in current['checks'] or f['identical'] != 'true' or
                    int(f['evaluations']) != CASES[name] or not math.isfinite(error) or not 0 <= error <= 1e-8):
                raise ValueError('invalid numerical check')
            current['checks'].add(name)
        elif tag == 'GVM_AUDIT_TIMING':
            key = f['case'], f['method'], int(f['round'])
            batch, value = int(f['batch']), float(f['nsPerCall'])
            if (key[0] not in current['checks'] or key[1] not in ('full', 'audited') or
                    not 0 <= key[2] < 7 or key in current['timings'] or not 1 <= batch <= 4096 or
                    batch & (batch-1) or not math.isfinite(value) or value <= 0):
                raise ValueError('invalid timing')
            current['timings'][key] = value
        elif tag == 'GVM_AUDIT_COMPLETE':
            expected = {(c, m, r) for c in CASES for m in ('full', 'audited') for r in range(7)}
            if (f['cases'] != '3' or f['methods'] != '6' or f['rounds'] != '7' or
                    f['sinkFinite'] != 'true' or current['checks'] != set(CASES) or
                    set(current['timings']) != expected):
                raise ValueError('incomplete run')
            runs.append(current)
            current = None
        else:
            raise ValueError('unknown record')
    if current is not None or len(runs) != 3:
        raise ValueError('incomplete run set')
    return runs


def summarize(runs):
    """Return per-JVM-median microseconds and before/after ratios for validated records."""
    lines = []
    for case in CASES:
        medians = {}
        for method in ('full', 'audited'):
            values = [statistics.median(run['timings'][case, method, r] for r in range(7))/1000 for run in runs]
            medians[method] = statistics.median(values)
            lines.append(f'{case} {method}: median={medians[method]:.3f} us JVM-range=[{min(values):.3f},{max(values):.3f}]')
        lines.append(f'{case}: speedup={medians["full"]/medians["audited"]:.3f}x')
    return '\n'.join(lines)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('log', type=Path)
    args = parser.parse_args()
    print(summarize(parse(args.log.read_text(encoding='utf-8-sig'))))


if __name__ == '__main__':
    main()
