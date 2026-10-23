"""Prepare an explicit, isolated sbt 2.0.8 boot copy. Never patch shared caches."""
import argparse
import difflib
import hashlib
import json
import os
from pathlib import Path
import shutil
import sys
import zipfile
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from validation_support import access, command, directory, download, sha, write

SOURCE_URL = 'https://raw.githubusercontent.com/sbt/sbt/v2.0.8/main-actions/src/main/scala/sbt/ForkTests.scala'
SOURCE_SHA = 'c75f1219c0d2918022f3ecd3cc5b2d0636af9cc82fdfefb8c0c6bd1cecc7599f'
ACTIONS_SHA = '95498714672cffa9f62d843d8a7d07e212704d5ecfc52c8b71164dedb20126a1'
REL = Path('scala-3.8.4/org.scala-sbt/sbt/2.0.8/actions_3-2.0.8.jar')


def patch(source):
    if hashlib.sha256(source.encode()).hexdigest() != SOURCE_SHA:
        raise ValueError('Unreviewed sbt source; refusing to patch')
    if source.count('promise.success(') != 2 or source.count('promise.failure(') != 3:
        raise ValueError('Unexpected completion sites')
    return source.replace('promise.success(', 'promise.trySuccess(').replace('promise.failure(', 'promise.tryFailure(')


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--boot',type=Path,required=True,help='Existing stock sbt boot root (read only)')
    p.add_argument('--output',type=Path,required=True,help='New, nonexistent output directory')
    p.add_argument('--java',default='java')
    a=p.parse_args()
    boot=a.boot.resolve(strict=True); output=a.output.resolve()
    if output == boot or boot in output.parents or output in boot.parents:
        raise ValueError('Output and stock boot must be disjoint')
    original=boot/REL
    if sha(original) != ACTIONS_SHA:
        raise ValueError('Unreviewed actions JAR; refusing to patch')
    directory(output)
    source=output/'ForkTests.original.scala'
    download(SOURCE_URL,source,SOURCE_SHA)
    text=source.read_text(encoding='utf-8'); revised=patch(text)
    write(output/'upstream.patch',''.join(difflib.unified_diff(text.splitlines(True),revised.splitlines(True),
          fromfile='a/main-actions/src/main/scala/sbt/ForkTests.scala',tofile='b/main-actions/src/main/scala/sbt/ForkTests.scala')))
    # Compile only the private React class, retaining upstream imports/license.
    react=text[:text.index('/**')] + revised[revised.index('private class React('):]
    write(output/'React.scala',react)
    classes=directory(output/'classes')
    libs=sorted((boot/'scala-3.8.4/lib').glob('*.jar'))+sorted(original.parent.glob('*.jar'))
    cp=os.pathsep.join(map(str,libs))
    args=['-cp',cp,'dotty.tools.dotc.Main','-classpath',cp,'-d',str(classes),str(output/'React.scala')]
    # Argument file avoids Windows command-length and wildcard expansion problems.
    write(output/'compile.args','\n'.join('"'+v.replace('\\','/').replace('"','\\"')+'"' for v in args)+'\n')
    try:
        command([a.java,'@'+str(output/'compile.args')],output,'compile')
    finally:
        access(classes,recursive=True)
    replacements={str(f.relative_to(classes)).replace('\\','/'):f.read_bytes() for f in classes.rglob('*') if f.is_file()}
    if set(replacements) != {'sbt/React.class','sbt/React.tasty'}:
        raise ValueError(f'Unexpected generated classes: {sorted(replacements)}')
    target=directory(output/'boot')
    shutil.copytree(boot/'scala-3.8.4',target/'scala-3.8.4')
    access(target,recursive=True)
    destination=target/REL
    replacement=output/'actions.repaired.jar'
    with zipfile.ZipFile(original) as src, zipfile.ZipFile(replacement,'w') as dst:
        for entry in src.infolist():
            dst.writestr(entry,replacements.get(entry.filename,src.read(entry.filename)))
    access(replacement)
    with zipfile.ZipFile(original) as src, zipfile.ZipFile(replacement) as dst:
        if src.namelist()!=dst.namelist(): raise ValueError('JAR entry drift')
        changed=[n for n in src.namelist() if src.read(n)!=dst.read(n)]
        if set(changed)!=set(replacements): raise ValueError(f'Unexpected byte changes: {changed}')
    shutil.copyfile(replacement,destination);access(destination)
    if sha(original)!=ACTIONS_SHA: raise ValueError('Stock JAR was changed')
    write(output/'manifest.json',dict(sbt='2.0.8',scala='3.8.4',sourceUrl=SOURCE_URL,sourceSha256=SOURCE_SHA,
          stockJarSha256=ACTIONS_SHA,repairedJarSha256=sha(destination),changedEntries=changed,
          boot=str(target),compilerInputs=[dict(path=str(f),sha256=sha(f)) for f in libs],
          scope='atomic completion only; first-completion semantics and worker event protocol unchanged'))
    print(target)


if __name__=='__main__': main()
