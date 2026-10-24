"""INF-19: strict all-point analysis of the model-only relevance screen."""
import argparse
import hashlib
import json
import statistics
from pathlib import Path


def read(path):
    return [json.loads(line) for line in Path(path).read_text().splitlines()]


def validate(rows):
    points = [r for r in rows if r['type'] == 'point']
    times = [r for r in rows if r['type'] == 'timing']
    expected_points = {(t, i) for t in (2, 6) for i in range(321)}
    keys = [(r['T'], round((r['x'] + 8) / .05)) for r in points]
    assert len(keys) == len(set(keys)) and set(keys) == expected_points, 'Point inventory'
    for r in points:
        i = round((r['x'] + 8) / .05)
        assert abs(r['x'] - (-8 + .05*i)) < 1e-12
    expected_times = {(t, tangent, gbs, b) for t in (2, 6)
                      for tangent in (False, True) for gbs in (False, True) for b in range(7)}
    keys = [(r['T'], r['tangent'], r['gbs'], r['batch']) for r in times]
    assert len(keys) == len(set(keys)) and set(keys) == expected_times, 'Timing inventory'
    assert all(r['calls'] == 321 and r['ns'] > 0 and r['rhs'] > 0 for r in times)
    assert [r for r in rows if r['type'] == 'checks'] == [dict(type='checks', checks=7704, failures=0)]
    assert rows[-1] == dict(type='complete')
    assert sum(r['type'] == 'environment' for r in rows) == 1
    assert len(rows) == 701, 'Unexpected records'
    return points, times


def summarize(paths):
    assert len(paths) == 3, 'Three JVM logs required'
    runs = [validate(read(p)) for p in paths]
    assert all(p == runs[0][0] for p, _ in runs), 'Scientific replay differs'
    result = dict(scope='model-only relevance, not filter utility', checksPerJvm=7704,
                  independentReplications=0, scientificReplay=True,
                  inputHashes={Path(p).name: hashlib.sha256(Path(p).read_bytes()).hexdigest() for p in paths},
                  horizons={})
    for t in (2, 6):
        points = sorted((p for p in runs[0][0] if p['T'] == t), key=lambda p: p['x'])
        crossings = {str(y): [[a['x'], b['x']] for a, b in zip(points, points[1:])
                              if (a['h']-y)*(b['h']-y) < 0] for y in (-.5, 0., .5)}
        turns = [[a['x'], b['x']] for a, b in zip(points, points[1:])
                 if a['derivative']*b['derivative'] < 0]
        timing = {}
        for gbs in (False, True):
            for tangent in (False, True):
                label = ('gbs' if gbs else 'dp853') + ('-tangent' if tangent else '-endpoint')
                timing[label] = [dict(medianMicrosecondsPerCall=statistics.median(
                    r['ns']/r['calls']/1000 for r in ts if r['T']==t and r['gbs']==gbs and r['tangent']==tangent),
                    minBatchMicrosecondsPerCall=min(r['ns']/321/1000 for r in ts
                        if r['T']==t and r['gbs']==gbs and r['tangent']==tangent),
                    maxBatchMicrosecondsPerCall=max(r['ns']/321/1000 for r in ts
                        if r['T']==t and r['gbs']==gbs and r['tangent']==tangent)) for _, ts in runs]
        result['horizons'][str(t)] = dict(rootBrackets=crossings, turningBrackets=turns, timing=timing,
            maxEndpointComparisonError=max(max(p['refinementError'],p['crossError'],p['augmentedError']) for p in points),
            maxDerivativeComparisonError=max(max(p['derivativeError'],p['otherDerivativeError']) for p in points),
            rhsRange=[min(p['rhs'] for p in points), max(p['rhs'] for p in points)])
    return result


def plot(points):
    # Plain SVG from measured endpoints; no plotting package needed.
    out = ['<svg xmlns="http://www.w3.org/2000/svg" width="920" height="440" viewBox="0 0 920 440">',
           '<rect width="920" height="440" fill="white"/>',
           '<text x="30" y="28" font-size="18">Measured nonlinear endpoint response (central input region)</text>']
    for j, t in enumerate((2, 6)):
        left = 55 + j*450
        out += [f'<text x="{left}" y="55" font-size="16">T = {t}</text>',
                f'<path d="M {left} 80 V 370 H {left+370}" fill="none" stroke="black"/>']
        selected = [p for p in points if p['T']==t and -3<=p['x']<=3]
        ymax = max(abs(p['h']) for p in selected)
        coords = ' '.join(f'{left+(p["x"]+3)/6*370:.3f},{225-p["h"]/ymax*140:.3f}' for p in selected)
        out += [f'<polyline points="{coords}" fill="none" stroke="#1261a0" stroke-width="2"/>',
                f'<path d="M {left} 225 H {left+370}" stroke="#aaaaaa"/>',
                f'<text x="{left}" y="395">x: -3 to 3; response scale: +/-{ymax:.2f}</text>']
    out += ['<text x="55" y="425">Plot zoom only; all numerical checks retain x in [-8,8]. Crossings do not certify mode completeness.</text>', '</svg>']
    return '\n'.join(out)


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('logs', nargs=3)
    parser.add_argument('--plot', action='store_true')
    args = parser.parse_args()
    summary = summarize(args.logs)
    print(plot(validate(read(args.logs[0]))[0]) if args.plot else json.dumps(summary, indent=2, allow_nan=False))
