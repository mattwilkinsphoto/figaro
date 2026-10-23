"""Run the existing repair gates on Linux in fresh, explicit boot/cache directories."""
import argparse
import json
import os
from pathlib import Path
import platform
import re
import shutil
import subprocess
import sys
from urllib.parse import urlparse
from urllib.request import url2pathname

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from validation_support import command, directory, download, sha, write

ROOT = Path(__file__).resolve().parents[2]
REL = Path('scala-3.8.4/org.scala-sbt/sbt/2.0.8/actions_3-2.0.8.jar')
LAUNCHER_URL = 'https://repo.maven.apache.org/maven2/org/scala-sbt/sbt-launch/2.0.8/sbt-launch-2.0.8.jar'
LAUNCHER_SHA = '5b4c25e234cfc43cc5103f1d4626cbdd713384bfe9c6402812b9ed3070021026'
SUITES = ['com.cra.figaro.test.util.SelectableSetTest'] + [
    'com.cra.figaro.test.modernization.' + name for name in [
        'GaussianBlockProposalRegressionTest', 'GuidedFixedBudgetTest',
        'FixedBudgetParticleFilterTest', 'FixedBudgetNumericalTest',
        'DecisionStratifiedProductionTest', 'WeightedBoundedParticleFilterTest',
        'CalibratedGuidedFilterTest', 'AuxiliaryParticleFilterTest']]


def check_figaro_log(text, expected_jar):
    """A successful exit is insufficient: verify the loaded binary and full outcomes."""
    text = re.sub(r'\x1b\[[0-9;]*m', '', text)
    urls = re.findall(r'ans: java.net.URL = (\S+)', text)
    if len(urls) != 1 or urlparse(urls[0]).scheme != 'file':
        raise ValueError('Missing or ambiguous loaded React code source')
    if Path(url2pathname(urlparse(urls[0]).path)).resolve() != Path(expected_jar).resolve():
        raise ValueError('React was not loaded from the isolated repaired JAR')
    required = ['Total number of tests run: 102', 'Suites: completed 9, aborted 0',
                'Tests: succeeded 102, failed 0, canceled 0, ignored 0, pending 0',
                'All tests passed.']
    if any(marker not in text for marker in required):
        raise ValueError('Incomplete focused Figaro outcomes')
    if 'Promise already completed' in text or '*** FAILED ***' in text:
        raise ValueError('Worker completion or test failure in focused run')


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--output', type=Path, required=True, help='New directory with an existing parent')
    a = p.parse_args()
    if platform.system() != 'Linux':
        raise RuntimeError('This entry point records actual Linux validation only')
    out = directory(a.output.resolve())
    home = directory(out/'home')
    temp = directory(out/'tmp')
    global_base = directory(out/'global')
    stock = directory(out/'stock-boot')
    coursier = directory(out/'coursier')
    os.environ['COURSIER_CACHE'] = str(coursier)
    os.environ['XDG_RUNTIME_DIR'] = str(temp)
    source_dir = directory(out/'sources')
    shutil.copytree(Path(__file__).parent, source_dir/'sbt-worker-repair')
    shutil.copyfile(ROOT/'tools/validation_support.py', source_dir/'validation_support.py')
    write(out/'protocol.json', dict(
        platform=platform.platform(), python=sys.version,
        commit=subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip(),
        runnerImage=os.environ.get('ImageOS'), runnerImageVersion=os.environ.get('ImageVersion'),
        sourceHashes={str(f.relative_to(source_dir)): sha(f) for f in source_dir.rglob('*') if f.is_file()},
        gates=dict(matrix='8 orderings x 20 for each of stock and repaired; existing expected outcomes',
                   fixtures='100 pass / 100 pass + 1 assertion failure / worker exit 17',
                   figaroSuites=SUITES, figaroTests=102),
        scope='Linux validation only; no promotion, default changes or upstream submission'))
    command(['java', '-version'], out, 'java-version')
    command(['javac', '-version'], out, 'javac-version')
    launcher = out/'sbt-launch-2.0.8.jar'
    download(LAUNCHER_URL, launcher, LAUNCHER_SHA)
    bootstrap = directory(out/'bootstrap')
    directory(bootstrap/'project')
    write(bootstrap/'project/build.properties', 'sbt.version=2.0.8\n')
    write(bootstrap/'build.sbt', 'scalaVersion := "3.9.0"\n')

    def sbt(boot):
        return ['java', '-Xms512m', '-Xmx6G', '-Xss6M', '-Dfile.encoding=UTF-8',
                '-Dsbt.supershell=false', '-Dsbt.log.noformat=true', '-Dsbt.server.autostart=false',
                f'-Dsbt.boot.directory={boot}', f'-Dsbt.global.base={global_base}',
                f'-Duser.home={home}', f'-Djava.io.tmpdir={temp}', '-jar', str(launcher)]

    command(sbt(stock) + ['show sbtVersion'], out, 'bootstrap-stock', cwd=bootstrap)
    original_hashes = {str(f.relative_to(stock)): sha(f) for f in stock.rglob('*.jar')}
    write(out/'stock-inputs.json', original_hashes)
    shutil.copyfile(stock/REL, out/'actions.stock.jar')
    scripts = Path(__file__).parent
    command([sys.executable, '-B', scripts/'prepare.py', '--boot', stock,
             '--output', out/'prepared'], out, 'prepare', cwd=ROOT)
    repaired = out/'prepared/boot'
    command([sys.executable, '-B', scripts/'verify.py', '--stock-boot', stock,
             '--repaired-boot', repaired, '--output', out/'matrix'], out, 'matrix', cwd=ROOT)
    command([sys.executable, '-B', scripts/'integration.py', '--boot', repaired,
             '--launcher', launcher, '--user-home', home, '--output', out/'integration'],
            out, 'integration', cwd=ROOT)
    task = 'figaro / Test / testOnly ' + ' '.join(SUITES) + ' -- -l com.cra.figaro.test.Performance'
    command(sbt(repaired) + ['eval Class.forName("sbt.React").getProtectionDomain.getCodeSource.getLocation', task],
            out, 'figaro-regressions', cwd=ROOT)
    check_figaro_log((out/'figaro-regressions.log').read_text(), repaired/REL)
    if original_hashes != {str(f.relative_to(stock)): sha(f) for f in stock.rglob('*.jar')}:
        raise ValueError('Stock boot JARs changed')
    manifest = json.loads((out/'prepared/manifest.json').read_text())
    if sha(repaired/REL) != manifest['repairedJarSha256']:
        raise ValueError('Repaired JAR changed during validation')
    command(['git', 'diff', '--exit-code'], out, 'source-integrity', cwd=ROOT)
    write(out/'result.json', dict(status='all bounded Linux gates passed',
          platform=platform.platform(), stockJarsUnchanged=True, repairedJarLoaded=True,
          repairedJarSha256=sha(repaired/REL), matrixCases=320, figaroTests=102,
          fixtures=json.loads((out/'integration/results.json').read_text()),
          limits='First completion unchanged; forkError-specific race not separately forced'))
    print((out/'result.json').read_text())


if __name__ == '__main__':
    main()
