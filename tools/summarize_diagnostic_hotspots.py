"""Validate complete isolated diagnostic studies; report JVM-level candidate gains (stdlib only)."""
import argparse
import csv
import io
import math
from pathlib import Path
import re
import statistics as stats
from summarize_interleaved_performance import create_text, encode

BASE = 'diagnosticHotspot shape values round stage iterations seconds allocatedBytes fingerprint'.split()
FIELDS = ['jvm'] + BASE
SHAPES = ('continuous', 'ties', 'ordered', 'reverse', 'constant')
SIZES = (1024, 16000, 64000)
STAGES = ('mergeSort', 'radixSort', 'scoresAndScatter', 'mergeRank', 'radixRank', 'summary')


def load(text, jvms=3, rounds=7, work=64000):
    if not 1 <= jvms <= 10 or not 1 <= rounds <= 20 or not 64000 <= work <= 1024000:
        raise ValueError('Invalid study size')
    reader = csv.DictReader(io.StringIO(text))
    if reader.fieldnames != FIELDS: raise ValueError('Wrong hotspot schema')
    rows = {}; hashes = {}
    for row in reader:
        if None in row or any(v is None for v in row.values()): raise ValueError('Wrong field count')
        jvm, n, r = (int(row[f]) for f in ('jvm', 'values', 'round'))
        shape, stage = row['shape'], row['stage']
        key = jvm, n, shape, r, stage
        if row['diagnosticHotspot'] != 'row' or jvm not in range(jvms) or n not in SIZES or shape not in SHAPES or stage not in STAGES or r not in range(-5, rounds):
            raise ValueError('Unexpected grid record')
        if key in rows or int(row['iterations']) != max(1, work // n): raise ValueError('Duplicate or wrong work')
        seconds = float(row['seconds'])
        if not math.isfinite(seconds) or seconds <= 0: raise ValueError('Invalid duration')
        if row['allocatedBytes'] != 'NaN' and int(row['allocatedBytes']) < 0: raise ValueError('Invalid allocation count')
        h = row['fingerprint']
        if not re.fullmatch('[0-9a-f]{64}', h): raise ValueError('Invalid fingerprint')
        equivalence = 'sort' if stage in ('mergeSort', 'radixSort') else 'rank' if stage != 'summary' else stage
        if hashes.setdefault((n, shape, equivalence), h) != h: raise ValueError('Changed output across candidates/rounds/JVMs')
        rows[key] = row
    if len(rows) != jvms * len(SIZES) * len(SHAPES) * (rounds + 5) * len(STAGES):
        raise ValueError('Incomplete grid, including warm-ups')
    return rows


def summarize(rows, jvms, rounds):
    print(f'Validated {len(rows)} records in {jvms} JVMs; warm-ups retained, excluded from medians.')
    print('| Values / shape | Sort gain median [JVM range] | Full-rank gain median [JVM range] | Sort allocated bytes/op: merge -> radix |')
    print('| --- | --- | --- | --- |')
    for n in SIZES:
        for shape in SHAPES:
            cells = []
            for before, after in (('mergeSort', 'radixSort'), ('mergeRank', 'radixRank')):
                gains = [stats.median(float(rows[j,n,shape,r,before]['seconds']) / float(rows[j,n,shape,r,after]['seconds'])
                                     for r in range(rounds)) for j in range(jvms)]
                cells.append(f'{stats.median(gains):.3f} [{min(gains):.3f}-{max(gains):.3f}]')
            alloc = []
            for stage in ('mergeSort', 'radixSort'):
                values = [rows[j,n,shape,r,stage] for j in range(jvms) for r in range(rounds)]
                alloc.append('N/A' if any(x['allocatedBytes']=='NaN' for x in values) else
                             f"{stats.median(int(x['allocatedBytes']) / int(x['iterations']) for x in values):.0f}")
            print(f'| {n} / {shape} | {cells[0]} | {cells[1]} | {alloc[0]} -> {alloc[1]} |')
    print('Gains compare rotated stage measurements within JVMs, not independent inner calls. No end-to-end speedup claim.')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    source = parser.add_mutually_exclusive_group(required=True)
    source.add_argument('--logs', type=Path, nargs='+')
    source.add_argument('--csv', type=Path)
    parser.add_argument('--jvms', type=int, default=3)
    parser.add_argument('--rounds', type=int, default=7)
    parser.add_argument('--work', type=int, default=64000)
    parser.add_argument('--output', type=Path)
    parser.add_argument('--acl-script')
    args = parser.parse_args()
    if args.logs:
        if len(args.logs) != args.jvms: raise ValueError('One complete log per JVM required')
        records = []
        for jvm, path in enumerate(args.logs):
            text = '\n'.join(line for line in path.read_text(encoding='utf-8-sig').splitlines()
                             if line.startswith('"diagnosticHotspot",') or line.startswith('"row",'))
            reader = csv.DictReader(io.StringIO(text))
            if reader.fieldnames != BASE: raise ValueError('Wrong raw study schema')
            records.extend(dict(row, jvm=str(jvm)) for row in reader)
        text = encode(records, FIELDS)
    else: text = args.csv.read_text(encoding='utf-8')
    rows = load(text, args.jvms, args.rounds, args.work)
    if args.output: create_text(args.output, text, args.acl_script)
    summarize(rows, args.jvms, args.rounds)


if __name__ == '__main__': main()
