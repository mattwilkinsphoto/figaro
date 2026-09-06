"""Validate benchmark-specific JFR attribution against the original exact aggregate totals."""
import argparse
from collections import Counter
import csv
import io
from pathlib import Path
import re
import subprocess
import summarize_vector_profile as profile
from summarize_interleaved_performance import create_text

FIELDS = 'recordingSha256 kind legacyGroup group detail site caller truncated count value'.split()
GROUPS = {'diagnostics': 'diagnostics', 'callbackObserved': 'sampling', 'callbackBoundaryUnresolved': 'sampling',
          'samplerObserved': 'sampling', 'callbackUnanchored': 'other', 'other': 'other', 'unknown': 'unknown'}


def load(text, previous):
    reader = csv.DictReader(io.StringIO(text))
    if reader.fieldnames != FIELDS: raise ValueError('Wrong attribution schema')
    rows = list(reader); keys = set(); hashes = set(); counts = Counter(); values = Counter()
    for row in rows:
        if None in row or any(v is None for v in row.values()): raise ValueError('Wrong field count')
        hashes.add(row['recordingSha256'])
        if not re.fullmatch('[0-9a-f]{64}', row['recordingSha256']): raise ValueError('Invalid recording identity')
        if GROUPS.get(row['group']) != row['legacyGroup'] or row['kind'] not in ('allocation', 'execution'):
            raise ValueError('Invalid attribution group/kind')
        if row['truncated'] not in ('true', 'false'): raise ValueError('Missing truncation status')
        if any(not re.fullmatch(r'[A-Za-z0-9_.$;\[\]<>?:-]+', row[f]) for f in ('detail', 'site', 'caller')):
            raise ValueError('Unsanitized identifier')
        count, value = int(row['count']), int(row['value'])
        if count <= 0 or value <= 0 or (row['kind'] == 'execution' and count != value): raise ValueError('Invalid count/weight')
        key = tuple(row[f] for f in FIELDS[:-2])
        if key in keys: raise ValueError('Duplicate attribution row')
        keys.add(key)
        old_key = row['kind'], row['legacyGroup'], row['detail'], row['site']
        counts[old_key] += count; values[old_key] += value
    if len(hashes) != 1: raise ValueError('Expected one recording')
    expected = {k: r for k, r in previous.items() if r['kind'] != 'metric'}
    if counts.keys() != expected.keys() or any(counts[k] != int(r['count']) or values[k] != int(r['value']) for k, r in expected.items()):
        raise ValueError('Attribution does not reconcile exactly with original profile')
    return rows


def summarize(rows):
    print('Verified exact per-kind/category/class-or-leaf/site counts and weights against original recording aggregates.')
    print('Recording SHA-256:', rows[0]['recordingSha256'])
    for kind in ('allocation', 'execution'):
        selected = [r for r in rows if r['kind'] == kind]
        total = sum(int(r['value']) for r in selected)
        print(f'\n{kind}: {total} total weight/count')
        print('| Attribution | Samples | Weight/count | Share % | Truncated samples |')
        print('| --- | --- | --- | --- | --- |')
        for group in sorted(GROUPS):
            subset = [r for r in selected if r['group'] == group]
            value = sum(int(r['value']) for r in subset)
            print(f"| {group} | {sum(int(r['count']) for r in subset)} | {value} | {100*value/total:.2f} | {sum(int(r['count']) for r in subset if r['truncated']=='true')} |")
    callers = Counter()
    for row in rows:
        if row['kind'] == 'execution' and '.interrupted:' in row['site']:
            callers[row['caller']] += int(row['count'])
    print('\nDiagnostic interruption: observed next diagnostic frame (not exclusive causal CPU cost)')
    for caller, count in callers.most_common(): print(caller, count)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--profile', type=Path, required=True)
    source = parser.add_mutually_exclusive_group(required=True)
    source.add_argument('--csv', type=Path); source.add_argument('--jfr', type=Path)
    parser.add_argument('--java', type=Path); parser.add_argument('--output', type=Path)
    parser.add_argument('--acl-script')
    args = parser.parse_args()
    previous = profile.load(args.profile.read_text(encoding='utf-8'))
    if args.jfr:
        if not args.java: raise ValueError('--java required to read JFR')
        text = subprocess.run([str(args.java.resolve()), '-XX:-UsePerfData', str(Path(__file__).with_name('VectorProfileAttribution.java').resolve()), str(args.jfr.resolve())],
                              text=True, encoding='utf-8', capture_output=True, check=True).stdout
    else: text = args.csv.read_text(encoding='utf-8')
    rows = load(text, previous)
    if args.output: create_text(args.output, text, args.acl_script)
    summarize(rows)


if __name__ == '__main__': main()
