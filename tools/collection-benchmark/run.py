"""Balanced standalone JMH comparison; execution integrity is a gate, speed is not."""
import argparse
import json
import math
import os
from pathlib import Path
import statistics
import sys
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from validation_support import access, command, directory, download, sha, write

MAVEN='https://repo.maven.apache.org/maven2/'
DEPS={
 'org/openjdk/jmh/jmh-core/1.37/jmh-core-1.37.jar':'dc0eaf2bbf0036a70b60798c785d6e03a9daf06b68b8edb0f1ba9eb3421baeb3',
 'org/openjdk/jmh/jmh-generator-annprocess/1.37/jmh-generator-annprocess-1.37.jar':'6a5604b5b804e0daca1145df1077609321687734a8b49387e49f10557c186c77',
 'net/sf/jopt-simple/jopt-simple/5.0.4/jopt-simple-5.0.4.jar':'df26cc58f235f477db07f753ba5a3ab243ebe5789d9f89ecf68dd62ea9a66c28',
 'org/apache/commons/commons-math3/3.6.1/commons-math3-3.6.1.jar':'1e56d7b058d28b65abd256b8458e3885b674c1d588fa43cd7d1cbb9c7ef2b308',
 'org/scala-lang/scala-library/3.9.0/scala-library-3.9.0.jar':'b844bf6fb3080c73df6b9c523b7a69b9ef4a980bb15e5c37c35443d1d0b0e0cd',
}
BASE='io/github/mattwilkinsphoto/figaro_3/7.3.0/figaro_3-7.3.0.jar'
BASE_SHA='6b82a0efc92ea4119a51cdfe42e38090b3680d129625d16b653e37cc821cee1f'


def validate(data,size,iterations,warmups):
    if len(data)!=1: raise ValueError('Missing or extra benchmark result')
    row=data[0]; metric=row['primaryMetric']; raw=metric['rawData']
    if (row['benchmark']!='figaro.benchmark.EnumerationBenchmark.enumerate' or
        row['params']!={'size':str(size)} or row['forks']!=1 or row['threads']!=1 or
        row['measurementIterations']!=iterations or row['warmupIterations']!=warmups or
        metric['scoreUnit']!='ns/op' or len(raw)!=1 or len(raw[0])!=iterations):
        raise ValueError('Benchmark protocol mismatch or incomplete measurements')
    if any(not math.isfinite(x) or x<=0 for x in raw[0]): raise ValueError('Invalid timing')
    allocation=row['secondaryMetrics']['gc.alloc.rate.norm']['score']
    if not math.isfinite(allocation) or allocation<=0: raise ValueError('Invalid allocation profile')
    return dict(nsPerOp=statistics.mean(raw[0]),measurements=raw[0],bytesPerOp=allocation,
                withinForkEndStartRatio=statistics.mean(raw[0][-2:])/statistics.mean(raw[0][:2]))


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--candidate',type=Path,required=True)
    p.add_argument('--output',type=Path,required=True)
    p.add_argument('--profile',choices=['full','smoke'],default='full')
    p.add_argument('--java',default='java');p.add_argument('--javac',default='javac')
    a=p.parse_args();candidate=a.candidate.resolve(strict=True);out=directory(a.output.resolve())
    deps=directory(out/'deps');classes=directory(out/'classes');generated=directory(out/'generated');temp=directory(out/'tmp')
    for rel,digest in DEPS.items():download(MAVEN+rel,deps/Path(rel).name,digest)
    baseline=out/'figaro_3-7.3.0.jar';download(MAVEN+BASE,baseline,BASE_SHA)
    depcp=os.pathsep.join(str(f) for f in sorted(deps.glob('*.jar')))
    try:
        command([a.javac,'-cp',str(baseline)+os.pathsep+depcp,'-processorpath',depcp,'-d',classes,'-s',generated,
                 Path(__file__).with_name('EnumerationBenchmark.java')],out,'compile')
    finally:access(classes,True);access(generated,True)
    full=a.profile=='full'; sizes=[1000,2000,3000,4000] if full else [1000,4000]
    forks=3 if full else 1; warmups=5 if full else 1; iterations=5 if full else 2;duration='1s' if full else '100ms'
    plan=[]
    for index,size in enumerate(sizes):
        for fork in range(forks):
            for label in (['baseline','candidate'] if (index+fork)%2==0 else ['candidate','baseline']):
                plan.append(dict(size=size,fork=fork,artifact=label))
    write(out/'protocol.json',dict(profile=a.profile,plan=plan,warmups=warmups,iterations=iterations,duration=duration,
          artifacts={'baseline':dict(path=str(baseline),sha256=sha(baseline)),
                     'candidate':dict(path=str(candidate),sha256=sha(candidate))},
          sources={f.name:sha(f) for f in [Path(__file__),Path(__file__).with_name('EnumerationBenchmark.java')]}))
    results=[]
    for step in plan:
        name=f"{step['size']}-{step['fork']}-{step['artifact']}";dest=out/(name+'-jmh.json')
        artifact=baseline if step['artifact']=='baseline' else candidate
        cp=os.pathsep.join([str(classes),str(artifact),depcp])
        write(dest,'')
        try:
            command([a.java,'-Djava.io.tmpdir='+str(temp),'-cp',cp,'org.openjdk.jmh.Main',
                'figaro.benchmark.EnumerationBenchmark','-p',f"size={step['size']}",'-f','1','-wi',str(warmups),
                '-i',str(iterations),'-w',duration,'-r',duration,'-t','1','-prof','gc','-foe','true',
                '-jvmArgs','-Xms512m -Xmx512m','-rf','json','-rff',dest],out,name)
        finally:access(temp,True)
        results.append(dict(**step,**validate(json.loads(dest.read_text()),step['size'],iterations,warmups)))
    if sha(candidate)!=json.loads((out/'protocol.json').read_text())['artifacts']['candidate']['sha256']:
        raise ValueError('Candidate changed during benchmark')
    comparisons=[]
    for size in sizes:
        medians={label:statistics.median(r['nsPerOp'] for r in results if r['size']==size and r['artifact']==label)
                 for label in ['baseline','candidate']}
        comparisons.append(dict(size=size,medianForkNs=medians,candidateBaselineRatio=medians['candidate']/medians['baseline']))
    write(out/'results.json',dict(execution='complete',profile=a.profile,rows=results,comparisons=comparisons,
          interpretation='No speed threshold; smoke is execution-only. Review variability/trends before performance claims.'))
    print(json.dumps(comparisons,indent=2))


if __name__=='__main__':main()
