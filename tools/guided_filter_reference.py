"""Independent control for the guided study; reuses rational joint conditioning, not Kalman."""
import hashlib
import json
import math
from pathlib import Path
import platform
import sys

from weighted_filter_reference import reference


def check(path):
    raw = Path(path).read_bytes()
    ref = reference()
    expected = dict(evidence=1.0, firstMoment=ref[1], secondMoment=ref[2])
    modes = ('fixed-bootstrap', 'fixed-guided', 'calibrated-bootstrap', 'calibrated-guided')
    rows, oracles = [], []
    for line in raw.decode('utf-8-sig').splitlines():
        if 'GUIDED_ORACLE ' in line:
            values = list(map(float, line.split('GUIDED_ORACLE ', 1)[1].split()))
            assert len(values) == 3 and all(abs(a-b) <= 1e-12 for a, b in zip(values, ref))
            oracles.append(values)
        elif 'GUIDED ' in line:
            mode, metric, e, mean, se, runs, cost, pilots, fallbacks = line.split('GUIDED ', 1)[1].split()
            e, mean, se = map(float, (e, mean, se))
            runs, cost, pilots, fallbacks = map(int, (runs, cost, pilots, fallbacks))
            assert mode in modes and metric in expected and runs == 512
            assert 0 <= pilots < cost <= runs*5*768 and 0 <= fallbacks <= runs*5
            assert all(map(math.isfinite, (e, mean, se))) and se >= 0
            assert abs(e-expected[metric]) <= 1e-12
            if mode.startswith('fixed'):
                assert cost == runs*5*768 and pilots == 0 and fallbacks == 0
            else:
                assert pilots == runs*5*(32+128)
            passed = abs(mean-expected[metric]) <= 6*se+1e-12
            rows.append(dict(mode=mode, metric=metric, expected=expected[metric], mean=mean,
                             standard_error=se, runs=runs, candidates=cost, pilot_candidates=pilots,
                             fallbacks=fallbacks, passed=passed,
                             variance_per_run=se*se*runs,
                             variance_times_mean_work=se*se*cost))
    assert len(oracles) == 1
    assert len(rows) == 12 and len({(r['mode'], r['metric']) for r in rows}) == 12
    result = dict(input=Path(path).name, sha256=hashlib.sha256(raw).hexdigest(),
                  python=platform.python_version(), reference=ref,
                  method='rational joint Gaussian conditioning; double log determinant',
                  comparison='equal maximum allowance, not equal realized work or wall time',
                  rows=rows, passed=all(r['passed'] for r in rows))
    print(json.dumps(result, indent=2))
    assert result['passed']


if __name__ == '__main__':
    check(sys.argv[1])
