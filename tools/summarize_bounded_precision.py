"""Validate complete paired stopped-work evidence; timings never gate correctness."""
import argparse
import csv
import io
import math
from pathlib import Path
import statistics

FIELDS = 'jvm seed model method draws nanos covered precision error'.split()


def parse_log(text, jvm):
    rows = []
    for line in text.splitlines():
        if not line.startswith('BOUNDED_STUDY,') or line.startswith('BOUNDED_STUDY,seed,'):
            continue
        parts = next(csv.reader([line]))[1:]
        if len(parts) != 8:
            raise ValueError('Malformed benchmark row')
        rows.append(dict(zip(FIELDS, [str(jvm)] + parts)))
    return rows


def validate(rows, seeds=20, jvms=3):
    expected = {(j,s,m,a) for j in range(jvms) for s in range(seeds) for m in range(4) for a in range(2)}
    seen = set()
    for row in rows:
        key = tuple(int(row[f]) for f in FIELDS[:4])
        if key not in expected or key in seen:
            raise ValueError('Unexpected or duplicate evidence key')
        seen.add(key)
        if not 1 <= int(row['draws']) <= 100000 or int(row['nanos']) <= 0:
            raise ValueError('Invalid work/timing')
        if row['covered'] not in ('true','false') or row['precision'] not in ('true','false'):
            raise ValueError('Invalid outcome')
        error = float(row['error'])
        if not math.isfinite(error) or error < 0 or (row['precision']=='true' and error>.02):
            raise ValueError('Invalid precision result')
    if seen != expected:
        raise ValueError('Incomplete evidence grid')
    return rows


def summarize(rows):
    lookup = {tuple(int(r[f]) for f in FIELDS[:4]): r for r in rows}
    for model in range(4):
        pairs = [(r,lookup[(j,s,m,1)]) for (j,s,m,a),r in lookup.items() if m==model and a==0]
        draws = statistics.median(int(b['draws'])/int(a['draws']) for b,a in pairs)
        times = statistics.median(int(b['nanos'])/int(a['nanos']) for b,a in pairs)
        misses = sum(r['covered']=='false' for pair in pairs for r in pair)
        refusals = sum(r['precision']=='false' for pair in pairs for r in pair)
        print(f'model={model} pairs={len(pairs)} median_sample_ratio={draws:.3f} median_time_ratio={times:.3f} misses={misses} budget_exhausted={refusals}')


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('paths',nargs='+',type=Path)
    parser.add_argument('--output',type=Path)
    parser.add_argument('--seeds',type=int,default=20)
    parser.add_argument('--jvms',type=int,default=3)
    args = parser.parse_args()
    if len(args.paths)==1 and args.paths[0].suffix=='.csv':
        rows=list(csv.DictReader(io.StringIO(args.paths[0].read_text(encoding='utf-8'))))
    else:
        rows=[row for j,path in enumerate(args.paths) for row in parse_log(path.read_text(encoding='utf-8'),j)]
    validate(rows,args.seeds,args.jvms)
    if args.output:
        with args.output.open('w',encoding='utf-8',newline='') as out:
            writer=csv.DictWriter(out,fieldnames=FIELDS); writer.writeheader(); writer.writerows(rows)
    summarize(rows)
