#!/usr/bin/env python3
"""Package verified Figaro 6.1 artifacts as a deterministic, offline Maven layout.

Dependencies are not vendored: Maven Central/network or a populated dependency
cache is still required. The output directory must be new. Use --candidate only
for local validation; never publish a candidate bundle as a final release.
"""
import argparse
import hashlib
import io
import json
from pathlib import Path
import re
import shutil
import subprocess
import xml.etree.ElementTree as ET
from zipfile import ZipFile, ZipInfo, ZIP_DEFLATED

from check_acceptance_artifacts import LEGAL, validate

VERSION = '6.1.0'
PREFIX = f'maven/io/github/mattwilkinsphoto/figaro_3/{VERSION}/'


def check_pom(data):
    root = ET.fromstring(data)
    ns = {'p': 'http://maven.apache.org/POM/4.0.0'}
    coordinates = tuple(root.findtext('p:'+key, namespaces=ns) for key in ('groupId', 'artifactId', 'version'))
    if coordinates != ('io.github.mattwilkinsphoto', 'figaro_3', VERSION):
        raise ValueError('Unexpected POM coordinates')
    dependencies = root.findall('p:dependencies/p:dependency', ns)
    if not dependencies or any('SNAPSHOT' in d.findtext('p:version', default='', namespaces=ns) for d in dependencies):
        raise ValueError('Missing or snapshot dependencies')


def checksums(payload):
    return ''.join(f'{hashlib.sha256(data).hexdigest()}  {name}\n' for name, data in sorted(payload.items())).encode()


def archive_bytes(payload):
    stream = io.BytesIO()
    with ZipFile(stream, 'w', compression=ZIP_DEFLATED, compresslevel=9) as archive:
        for name, data in sorted(payload.items()):
            if name.startswith('/') or '..' in name.split('/') or '\\' in name:
                raise ValueError('Unsafe archive entry')
            info = ZipInfo(name, date_time=(2026, 1, 1, 0, 0, 0))
            info.create_system = 3
            info.external_attr = 0o100644 << 16
            info.compress_type = ZIP_DEFLATED
            archive.writestr(info, data, compresslevel=9)
    return stream.getvalue()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('artifacts', type=Path)
    parser.add_argument('output', type=Path)
    parser.add_argument('--candidate', action='store_true')
    parser.add_argument('--acl-script', type=Path)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    def git(*arguments):
        return subprocess.check_output(['git', '-c', 'safe.directory='+root.as_posix(), '-C', str(root), *arguments], text=True).strip()
    revision = git('rev-parse', 'HEAD')
    dirty = bool(git('status', '--porcelain'))
    if not re.fullmatch('[0-9a-f]{40}', revision) or (dirty and not args.candidate):
        raise ValueError('Final bundle requires a clean, committed source tree')
    payload = {}
    legal = {name: (root / path).read_bytes() for name, path in LEGAL.items()}
    for kind, suffix in (('thin', ''), ('fat', '-fat'), ('sources', '-sources'), ('javadoc', '-javadoc')):
        name = f'figaro_3-{VERSION}{suffix}.jar'
        path = args.artifacts / name
        with ZipFile(path) as archive:
            validate(archive, kind, legal)
        payload[('supplemental/' if kind == 'fat' else PREFIX)+name] = path.read_bytes()
    pom_name = f'figaro_3-{VERSION}.pom'
    pom = (args.artifacts / pom_name).read_bytes()
    check_pom(pom)
    payload[PREFIX+pom_name] = pom
    for name, data in list(payload.items()):
        if name.startswith(PREFIX):
            payload[name+'.sha256'] = (hashlib.sha256(data).hexdigest()+'\n').encode()
    for name in ('LICENSE', 'FigaroAttributions.txt', 'ArviZ-LICENSE.txt'):
        payload[name] = (root / name).read_bytes()
    payload['release.json'] = (json.dumps({'version': VERSION, 'sourceRevision': revision,
        'candidate': args.candidate, 'dirtySource': dirty, 'javaMinimum': 17, 'scalaVersion': '3.9.0',
        'sbtVersion': '2.0.8', 'mavenCentralPublication': False,
        'artifactSha256': {name: hashlib.sha256(data).hexdigest() for name, data in sorted(payload.items()) if name.endswith(('.jar', '.pom'))}}, indent=2, sort_keys=True)+'\n').encode()
    payload['README.md'] = f'''# Figaro {VERSION} compiled library

Verify the downloaded ZIP against its external `.sha256` file, then extract it.
`SHA256SUMS` inside covers every payload file except itself.

1. Use Java 17+ and Scala 3.9.0. The library was built with sbt 2.0.8.
2. Point your build at the extracted `maven` directory (replace the example path):

```scala
resolvers += "figaro-release" at file("/absolute/path/to/extracted/maven").toURI.toString
libraryDependencies += "io.github.mattwilkinsphoto" %% "figaro" % "{VERSION}"
```

3. Run your usual compile and tests. No local Figaro source checkout is needed.

These coordinates are not published to Maven Central. The bundled POM resolves
Scala and other runtime dependencies from normal repositories; network access or
a populated dependency cache is required. Do not also add the supplemental fat
JAR: it excludes the Scala runtime, is not executable, and can duplicate dependencies.
Sources and Scaladoc classifiers are alongside the thin JAR in the Maven layout.

`release.json` identifies the source revision and hashes. A candidate/dirtySource
bundle is for local testing only. Final releases use an immutable source tag.

Migration and scope: https://github.com/mattwilkinsphoto/figaro/blob/v{VERSION}/docs/RELEASE_6_1.md
'''.encode()
    payload['SHA256SUMS'] = checksums(payload)
    output = args.output.resolve()
    if output.exists() or not output.parent.is_dir():
        raise ValueError('Output must be a new directory with an existing parent')
    def grant(path):
        if args.acl_script:
            shell = shutil.which('pwsh') or shutil.which('powershell.exe')
            if not shell:
                raise ValueError('PowerShell is required for --acl-script')
            subprocess.run([shell, '-NoProfile', '-File', str(args.acl_script.resolve()), '-Paths', str(path)], check=True)
    output.mkdir(); grant(output)
    for name, data in sorted(payload.items()):
        path = output / name
        missing = []
        parent = path.parent
        while not parent.exists():
            missing.append(parent); parent = parent.parent
        for parent in reversed(missing):
            parent.mkdir(); grant(parent)
        path.write_bytes(data); grant(path)
    zip_name = f'figaro-{VERSION}-maven.zip'
    packed = archive_bytes(payload)
    (output / zip_name).write_bytes(packed); grant(output / zip_name)
    (output / (zip_name+'.sha256')).write_bytes(checksums({zip_name: packed})); grant(output / (zip_name+'.sha256'))
    print(f'Created {zip_name}; revision={revision}; candidate={args.candidate}; dirty={dirty}')


if __name__ == '__main__':
    main()
