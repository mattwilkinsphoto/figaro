"""Validate Figaro's four JVM artifacts without extracting or executing archive contents."""
import argparse
import hashlib
import json
from pathlib import Path
from zipfile import ZipFile

LEGAL = {'META-INF/LICENSE': 'LICENSE', 'META-INF/FigaroAttributions.txt': 'FigaroAttributions.txt'}
FAT_LICENSE = 'META-INF/LICENSE_figaro-6.0.0-modern.10-SNAPSHOT'
REQUIRED = ('com/cra/figaro/language/Universe.class',
            'com/cra/figaro/util/SamplingRandom$.class',
            'com/cra/figaro/algorithm/sampling/VectorSliceSampler$.class',
            'com/cra/figaro/algorithm/sampling/parallel/MultiChainMetropolisHastings$.class',
            'com/cra/figaro/algorithm/sampling/parallel/MultiChainVectorSliceSampler$.class',
            'com/cra/figaro/algorithm/sampling/parallel/McmcDiagnostics$.class',
            'com/cra/figaro/library/atomic/continuous/GaussVonMisesScalarBhattacharyya$.class',
            'com/cra/figaro/library/atomic/continuous/GaussVonMisesMutualInformation$.class',
            'com/cra/figaro/library/atomic/continuous/StudentTDistribution.class',
            'com/cra/figaro/library/atomic/continuous/CauchyDistribution.class',
            'com/cra/figaro/library/atomic/continuous/LaplaceDistribution.class',
            'com/cra/figaro/library/atomic/continuous/LogNormalDistribution.class',
            'com/cra/figaro/library/atomic/continuous/WeibullDistribution.class',
            'com/cra/figaro/library/atomic/continuous/TriangularDistribution.class',
            'com/cra/figaro/library/atomic/continuous/KumaraswamyDistribution.class',
            'com/cra/figaro/library/atomic/discrete/NegativeBinomialDistribution.class',
            'com/cra/figaro/library/atomic/discrete/HypergeometricDistribution.class',
            'com/cra/figaro/library/atomic/continuous/ScalarDivergence$.class',
            'com/cra/figaro/library/atomic/discrete/CountDivergence$.class',
            'com/cra/figaro/library/atomic/DiscreteInformation$.class')


def validate(archive, kind, legal):
    names = archive.namelist()
    if len(names) != len(set(names)):
        raise ValueError('Duplicate archive entries')
    for name, expected in legal.items():
        # Assembly renames each dependency's license to avoid collisions. Only
        # Figaro's exact entry is an alternative, never another library's license.
        candidates = [name]
        if kind == 'fat' and name == 'META-INF/LICENSE':
            candidates.append(FAT_LICENSE)
        present = [entry for entry in candidates if entry in names]
        if not present:
            raise ValueError('Missing Figaro legal entry: ' + name)
        for entry in present:
            if archive.read(entry).replace(b'\r\n', b'\n') != expected.replace(b'\r\n', b'\n'):
                raise ValueError('Legal content differs beyond line endings')
    if kind in ('thin', 'fat'):
        if any(name not in names for name in REQUIRED):
            raise ValueError('Missing supported runtime class')
        if any(name.startswith(('org/scalatest/', 'org/scalactic/', 'scoverage/',
                                'com/cra/figaro/test/', 'com/cra/figaro/example/')) for name in names):
            raise ValueError('Test, coverage or example runtime included')
        # The fat JAR legitimately contains scala-parallel-collections. Its
        # scala/ namespace is not evidence of bundling the core Scala runtime.
        if (kind == 'thin' and any(name.startswith('scala/') for name in names)) or any(
                name.startswith(('scala/runtime/', 'scala/deriving/')) or
                name in ('scala/Predef.class', 'scala/Predef$.class', 'scala/Option.class')
                for name in names):
            raise ValueError('Scala runtime unexpectedly bundled')
        for name in names:
            if name.endswith('.class'):
                data = archive.read(name)
                if len(data)<8 or data[:4]!=b'\xca\xfe\xba\xbe' or not 45<=int.from_bytes(data[6:8],'big')<=61:
                    raise ValueError('Invalid or newer-than-Java-17 class file')
                if b'scoverage/Invoker' in data:
                    raise ValueError('Coverage instrumentation reference remains')
    elif kind == 'sources':
        if not any(name.endswith('VectorSliceSampler.scala') for name in names):
            raise ValueError('Missing source artifact contents')
    elif kind == 'javadoc':
        if 'index.html' not in names:
            raise ValueError('Missing API site entry point')
    else:
        raise ValueError('Unknown artifact kind')
    return len(names)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('directory', type=Path)
    parser.add_argument('--legal-root', type=Path, default=Path(__file__).resolve().parents[1])
    args = parser.parse_args()
    legal = {key: (args.legal_root / value).read_bytes() for key, value in LEGAL.items()}
    for kind, suffix in (('thin',''),('fat','-fat'),('sources','-sources'),('javadoc','-javadoc')):
        path = args.directory / f'figaro_3-6.0.0-modern.10-SNAPSHOT{suffix}.jar'
        with ZipFile(path) as archive:
            entries = validate(archive,kind,legal)
        print(json.dumps({'artifact':path.name,'entries':entries,
                          'sha256':hashlib.sha256(path.read_bytes()).hexdigest()},sort_keys=True))


if __name__ == '__main__': main()
