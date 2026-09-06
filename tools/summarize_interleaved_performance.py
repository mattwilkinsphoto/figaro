"""Isolated JVM snapshots, balanced vector benchmark runs, and exact-work audit (stdlib only)."""
import argparse
import csv
import hashlib
import io
import json
import os
from pathlib import Path
import re
import shutil
import statistics as stats
import subprocess

import summarize_vector_performance as vector

PREFIX = ['invocation', 'pair', 'position', 'variant', 'revision', 'runtimeHash']
FIELDS = PREFIX + vector.FIELDS
TIMING = {'wallSeconds', 'constructionSeconds', 'samplingSeconds', 'diagnosticsSeconds', 'cpuSeconds', 'gcSeconds'}


def grant(path, script, recursive=False):
    if script:
        command = [shutil.which('pwsh') or 'powershell', '-NoProfile', '-File', str(Path(script).resolve()), '-Paths', str(path.resolve())]
        if recursive:
            command += ['-Recurse']
        subprocess.run(command, check=True)


def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def encode(rows, fields):
    stream = io.StringIO()
    writer = csv.DictWriter(stream, fields, quoting=csv.QUOTE_ALL, lineterminator='\n')
    writer.writeheader(); writer.writerows(rows)
    return stream.getvalue()


def create_text(path, text, script):
    with path.open('x', encoding='utf-8', newline='') as stream:
        grant(path, script)
        stream.write(text)


def schedule(pairs):
    if pairs < 2 or pairs % 2:
        raise ValueError('Use an even number of at least two pairs for balanced order')
    return [(p, pos, variant) for p in range(pairs) for pos, variant in
            enumerate(('baseline', 'current') if p % 2 == 0 else ('current', 'baseline'))]


def snapshot(args):
    if not re.fullmatch(r'[0-9a-f]{40}', args.revision):
        raise ValueError('Full Git revision required')
    lines = [line for line in args.log.read_text(encoding='utf-8-sig').splitlines() if line.startswith('List(${OUT}/')]
    if len(lines) != 1 or not lines[0].endswith(')'):
        raise ValueError('Expected one sbt 2 exported runtime classpath')
    entries = []
    for reference in lines[0][5:-1].split(', '):
        match = re.fullmatch(r'(\$\{OUT\}|\$\{CSR_CACHE\})/(.+?)(?:>sha256-([0-9a-f]{64})/(\d+))?', reference)
        if not match:
            raise ValueError('Unexpected classpath reference')
        root = (args.out_root if match[1] == '${OUT}' else args.cache_root).resolve()
        path = (root / match[2]).resolve()
        if not path.is_relative_to(root) or path.suffix != '.jar' or not path.is_file():
            raise ValueError('Classpath entry is not a jar within its declared root')
        sha = digest(path)
        if match[3] and (sha != match[3] or path.stat().st_size != int(match[4])):
            raise ValueError('Exported jar identity mismatch')
        entries.append((path, sha))
    args.output.mkdir()  # Exclusive: never overwrite/reuse a snapshot.
    grant(args.output, args.acl_script)
    manifest = {'revision': args.revision, 'files': []}
    for index, (source, sha) in enumerate(entries):
        name = f'{index:02d}-{source.name}'
        target = args.output / name
        shutil.copyfile(source, target); grant(target, args.acl_script)
        if digest(target) != sha:
            raise ValueError('Snapshot copy mismatch')
        manifest['files'].append({'name': name, 'sha256': sha})
    create_text(args.output / 'runtime.json', json.dumps(manifest, indent=2) + '\n', args.acl_script)
    print('Prepared snapshot', args.revision, digest(args.output / 'runtime.json'))


def runtime(directory):
    manifest_path = directory / 'runtime.json'
    manifest = json.loads(manifest_path.read_text(encoding='utf-8'))
    if not re.fullmatch(r'[0-9a-f]{40}', manifest['revision']) or not manifest['files']:
        raise ValueError('Invalid snapshot identity')
    paths = []
    for item in manifest['files']:
        if Path(item['name']).name != item['name'] or not item['name'].endswith('.jar'):
            raise ValueError('Invalid snapshot jar name')
        path = (directory / item['name']).resolve()
        if digest(path) != item['sha256']:
            raise ValueError('Snapshot jar changed')
        paths.append(str(path))
    return manifest, digest(manifest_path), os.pathsep.join(paths)


def same_work(left, right):
    if left.keys() != right.keys() or any(any(left[k][f] != right[k][f] for f in vector.FIELDS if f not in TIMING) for k in left):
        raise ValueError('Changed work, traces, diagnostics, warnings or statuses')


def load(text, pairs, repetitions, draws, warm_up, baseline=None):
    reader = csv.DictReader(io.StringIO(text))
    if reader.fieldnames != FIELDS:
        raise ValueError('Wrong interleaved schema')
    grouped = {i: [] for i in range(2 * pairs)}
    identities = {}
    plan = schedule(pairs)
    for row in reader:
        if None in row or any(value is None for value in row.values()):
            raise ValueError('Wrong field count')
        invocation = int(row['invocation'])
        if invocation not in grouped:
            raise ValueError('Unexpected invocation')
        p, position, variant = plan[invocation]
        if (row['pair'], row['position'], row['variant']) != (str(p), str(position), variant):
            raise ValueError('Incorrect balanced order')
        if not re.fullmatch('[0-9a-f]{40}', row['revision']) or not re.fullmatch('[0-9a-f]{64}', row['runtimeHash']):
            raise ValueError('Invalid revision/runtime identity')
        identity = row['revision'], row['runtimeHash']
        if identities.setdefault(variant, identity) != identity:
            raise ValueError('Runtime changed within variant')
        grouped[invocation].append({f: row[f] for f in vector.FIELDS})
    if len(identities) != 2 or identities['baseline'] == identities['current']:
        raise ValueError('Two distinct runtime identities required')
    runs = {i: vector.load([encode(rows, vector.FIELDS)], repetitions, draws, warm_up) for i, rows in grouped.items()}
    reference = baseline if baseline is not None else runs[0]
    for records in runs.values():
        same_work(reference, records)
    return runs


def summary(runs, pairs, repetitions):
    print(f'Validated {sum(len(r) for r in runs.values())} exact-work records in {2*pairs} fresh JVM runs.')
    print('| Fixture / method (4 workers) | Median pair total gain | Pair range | Pairs faster | Median pair diagnostic gain |')
    print('| --- | --- | --- | --- | --- |')
    for fixture in vector.FIXTURES:
        for method in vector.METHODS:
            gains, diagnostics = [], []
            complete = True
            for pair in range(pairs):
                b, c = (2*pair, 2*pair+1) if pair % 2 == 0 else (2*pair+1, 2*pair)
                old = [runs[b][fixture, method, 4, r] for r in range(repetitions)]
                new = [runs[c][fixture, method, 4, r] for r in range(repetitions)]
                if any(x['status'] != 'Complete' for x in old + new) or any(float(x['diagnosticsSeconds']) <= 0 for x in old + new):
                    complete = False; break
                gains.append(stats.median(float(x['wallSeconds']) / float(y['wallSeconds']) for x, y in zip(old, new)))
                diagnostics.append(stats.median(float(x['diagnosticsSeconds']) / float(y['diagnosticsSeconds']) for x, y in zip(old, new)))
            cells = f'{stats.median(gains):.3f} | {min(gains):.3f}-{max(gains):.3f} | {sum(g>1 for g in gains)}/{pairs} | {stats.median(diagnostics):.3f}' if complete else 'N/A | N/A | N/A | N/A'
            print(f'| {fixture} / {method} | {cells} |')
    print('Pairs/JVMs, not seed rows, are the repetition units. Ranges are descriptive, not confidence intervals. All warm-ups retained.')


def run(args):
    plan = schedule(args.pairs)
    runtimes = {name: runtime(getattr(args, name)) for name in ('baseline', 'current')}
    # The benchmark and dependencies must be byte-identical; only the Figaro jar differs.
    files = [data[0]['files'] for data in runtimes.values()]
    if [x['name'] for x in files[0]] != [x['name'] for x in files[1]]:
        raise ValueError('Classpath ordering/names differ')
    differing = [x['name'] for x, y in zip(*files) if x['sha256'] != y['sha256']]
    if len(differing) != 1 or not re.fullmatch(r'\d+-figaro_3-.+\.jar', differing[0]):
        raise ValueError('Only the Figaro library jar may differ')
    reference = vector.load([args.baseline_csv.read_text(encoding='utf-8')], args.repetitions, args.draws, args.warm_up)
    args.output.mkdir(); grant(args.output, args.acl_script)
    combined = args.output / 'interleaved-results.csv'
    with combined.open('x', encoding='utf-8', newline='') as out:
        grant(combined, args.acl_script)
        writer = csv.DictWriter(out, FIELDS, quoting=csv.QUOTE_ALL, lineterminator='\n'); writer.writeheader()
        for invocation, (pair, position, variant) in enumerate(plan):
            manifest, sha, classpath = runtimes[variant]
            # Recheck snapshot integrity before every invocation; no builds during measurement.
            if runtime(getattr(args, variant))[1] != sha:
                raise ValueError('Runtime manifest changed')
            temp = args.output / f'tmp-{invocation}'
            temp.mkdir(); grant(temp, args.acl_script)
            log = args.output / f'run-{invocation}-{variant}.log'
            print(f'Starting invocation {invocation+1}/{len(plan)}: pair {pair}, {variant}', flush=True)
            with log.open('x', encoding='utf-8') as stream:
                grant(log, args.acl_script)
                command = [str(args.java.resolve()), '-Xms1G', '-Xmx6G', '-Xss6M', '-Dfile.encoding=UTF-8',
                           f'-Djava.io.tmpdir={temp.resolve()}', f'-Duser.home={temp.resolve()}', '-cp', classpath,
                           'com.cra.figaro.example.VectorSamplingPerformance', str(args.repetitions), str(args.draws), str(args.warm_up)]
                try:
                    subprocess.run(command, stdout=stream, stderr=subprocess.STDOUT, check=True, cwd=args.output)
                finally:
                    grant(temp, args.acl_script, recursive=True)
            records = vector.load([log.read_text(encoding='utf-8')], args.repetitions, args.draws, args.warm_up)
            meta = dict(zip(PREFIX, map(str, (invocation, pair, position, variant, manifest['revision'], sha))))
            writer.writerows(dict(meta, **records[k]) for k in sorted(records)); out.flush()
            same_work(reference, records)
            print(f'Completed invocation {invocation+1}: {len(records)} unchanged-work records', flush=True)
    summary(load(combined.read_text(encoding='utf-8'), args.pairs, args.repetitions, args.draws, args.warm_up, reference), args.pairs, args.repetitions)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    modes = parser.add_subparsers(dest='mode', required=True)
    prep = modes.add_parser('snapshot')
    for name in ('log', 'out-root', 'cache-root', 'output'):
        prep.add_argument('--'+name, type=Path, required=True)
    prep.add_argument('--revision', required=True); prep.add_argument('--acl-script')
    running = modes.add_parser('run')
    for name in ('java', 'baseline', 'current', 'output'):
        running.add_argument('--'+name, type=Path, required=True)
    running.add_argument('--acl-script')
    checking = modes.add_parser('check'); checking.add_argument('csv', type=Path)
    for mode in (running, checking):
        mode.add_argument('--pairs', type=int, default=4)
        mode.add_argument('--repetitions', type=int, default=5)
        mode.add_argument('--draws', type=int, default=4000)
        mode.add_argument('--warm-up', type=int, default=500)
        mode.add_argument('--baseline-csv', type=Path, required=True)
    args = parser.parse_args()
    if args.mode == 'snapshot': snapshot(args)
    elif args.mode == 'run': run(args)
    else:
        baseline = vector.load([args.baseline_csv.read_text(encoding='utf-8')], args.repetitions, args.draws, args.warm_up)
        summary(load(args.csv.read_text(encoding='utf-8'), args.pairs, args.repetitions, args.draws, args.warm_up, baseline), args.pairs, args.repetitions)


if __name__ == '__main__':
    main()
