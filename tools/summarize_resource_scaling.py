"""Fresh-JVM resource study runner and strict complete-grid validator (Python stdlib)."""
import argparse
import csv
import ctypes
import io
import math
import os
from pathlib import Path
import re
import statistics as stats
import subprocess
import time

from summarize_interleaved_performance import runtime, grant, digest

BASE = ('resourceStudy kind variant draws workers jobs round mode seed wallSeconds cpuSeconds '
        'constructionSeconds samplingSeconds diagnosticsSeconds gcMillis compilationMillis heapBeforeBytes '
        'heapAtReturnBytes sumHeapPoolPeaksBytes heapRetainedBytes gcBeforeObserved gcAfterObserved '
        'storedValues evaluations fingerprints callbackWeight vectorWeight diagnosticWeight graphWeight '
        'otherWeight unknownWeight allocationSamples truncatedSamples lostBytes compilationEvents deoptimizationEvents').split()
PREFIX = ('caseIndex revision runtimeHash osPeakSource osPeakBytes recordingHash warmup0Seconds warmup1Seconds').split()
FIELDS = PREFIX + BASE
KINDS = ('gaussian32', 'positive32', 'likelihood8')


def schedule(smoke=False):
    """Return the predeclared ordered cases; smoke is tooling evidence only."""
    if smoke:
        return [('gaussian32', 'reference', 100, 1, 'single', 0, 'plain'),
                ('gaussian32', 'loop', 100, 4, 'single', 0, 'plain'),
                ('gaussian32', 'reference', 100, 4, 'single', 0, 'profile'),
                ('graphWide', 'reference', 100, 4, 'single', 0, 'plain')]
    plan = []
    for r in range(3):
        workers = (1, 2, 4)[r:] + (1, 2, 4)[:r]
        for kind in KINDS:
            for draws in (4000, 16000):
                for w in workers:
                    plan.append((kind, 'reference', draws, w, 'single', r, 'plain'))
        for draws in (1000, 4000):
            for w in ((1, 4) if r % 2 == 0 else (4, 1)):
                plan.append(('graphWide', 'reference', draws, w, 'single', r, 'plain'))
        for kind, draws in (('gaussian32', 16000), ('likelihood8', 4000)):
            for jobs in (('serial', 'overlap') if r % 2 == 0 else ('overlap', 'serial')):
                plan.append((kind, 'reference', draws, 4, jobs, r, 'plain'))
        for kind in KINDS:
            plan.append((kind, 'loop', 4000, 4, 'single', r, 'plain'))
        for kind in KINDS:
            for variant in (('reference', 'loop') if r % 2 == 0 else ('loop', 'reference')):
                plan.append((kind, variant, 16000, 4, 'single', r, 'profile'))
        plan.append(('graphWide', 'reference', 4000, 4, 'single', r, 'profile'))
    return plan


def os_peak(pid):
    """Read a live child's lifetime resident-memory high-water mark, not heap/commit."""
    if os.name == 'nt':
        from ctypes import wintypes
        class Counters(ctypes.Structure):
            _fields_ = [('cb', wintypes.DWORD), ('faults', wintypes.DWORD)] + [
                (name, ctypes.c_size_t) for name in ('peak', 'working', 'qpp', 'qp', 'qnp', 'qn', 'page', 'peakPage')]
        kernel = ctypes.WinDLL('kernel32', use_last_error=True)
        psapi = ctypes.WinDLL('psapi', use_last_error=True)
        kernel.OpenProcess.argtypes = [wintypes.DWORD, wintypes.BOOL, wintypes.DWORD]
        kernel.OpenProcess.restype = wintypes.HANDLE
        kernel.CloseHandle.argtypes = [wintypes.HANDLE]
        kernel.CloseHandle.restype = wintypes.BOOL
        psapi.GetProcessMemoryInfo.argtypes = [wintypes.HANDLE, ctypes.POINTER(Counters), wintypes.DWORD]
        psapi.GetProcessMemoryInfo.restype = wintypes.BOOL
        handle = kernel.OpenProcess(0x1000, False, pid)
        if not handle:
            raise ctypes.WinError(ctypes.get_last_error())
        try:
            counters = Counters(); counters.cb = ctypes.sizeof(counters)
            if not psapi.GetProcessMemoryInfo(handle, ctypes.byref(counters), counters.cb):
                raise ctypes.WinError(ctypes.get_last_error())
            return 'WindowsPeakWorkingSet', counters.peak
        finally:
            kernel.CloseHandle(handle)
    status = Path(f'/proc/{pid}/status')
    if status.is_file():
        match = re.search(r'^VmHWM:\s+(\d+) kB$', status.read_text(), re.M)
        if match:
            return 'LinuxVmHWM', int(match[1]) * 1024
    return 'unavailable', -1


def parse_log(text):
    lines = text.splitlines()
    data = [line for line in lines if line.startswith(('"resourceStudy",', '"row",'))]
    reader = csv.DictReader(io.StringIO('\n'.join(data)))
    rows = list(reader)
    if reader.fieldnames != BASE or len(rows) != 1 or lines.count('RESOURCE_READY') != 1:
        raise ValueError('Incomplete case output')
    row = rows[0]
    warm = [line.split(',') for line in lines if line.startswith('resourceWarmup,')]
    if len(warm) != 2 or [w[1] for w in warm] != ['-2', '-1']:
        raise ValueError('Two discarded warm-ups required')
    if any(len(w) != 4 or w[3] != row['fingerprints'] for w in warm):
        raise ValueError('Warm-up changed the result')
    return row, [w[2] for w in warm]


def load(text, smoke=False):
    reader = csv.DictReader(io.StringIO(text))
    if reader.fieldnames != FIELDS:
        raise ValueError('Wrong resource schema')
    rows = list(reader); plan = schedule(smoke)
    if len(rows) != len(plan):
        raise ValueError('Incomplete or oversized study grid')
    identity = None; work = {}
    for i, (row, case) in enumerate(zip(rows, plan)):
        if None in row or any(v is None for v in row.values()):
            raise ValueError('Wrong field count')
        kind, variant, draws, workers, jobs, r, mode = case
        expected = dict(zip(('kind','variant','draws','workers','jobs','round','mode'), map(str, case)))
        expected.update(caseIndex=str(i), resourceStudy='row', seed=str(420013+7919*r))
        if any(row[k] != v for k, v in expected.items()):
            raise ValueError('Unexpected case identity/order')
        for field, size in (('revision',40), ('runtimeHash',64)):
            if not re.fullmatch('[0-9a-f]{'+str(size)+'}', row[field]):
                raise ValueError('Invalid runtime identity')
        current = row['revision'], row['runtimeHash'], row['osPeakSource']
        if identity is None: identity = current
        if identity != current: raise ValueError('Runtime or OS counter changed')
        if row['osPeakSource'] not in ('WindowsPeakWorkingSet', 'LinuxVmHWM', 'unavailable'):
            raise ValueError('Unknown OS counter')
        if (int(row['osPeakBytes']) != -1 if row['osPeakSource']=='unavailable' else int(row['osPeakBytes'])<=0):
            raise ValueError('Invalid OS peak')
        for field in ('wallSeconds','warmup0Seconds','warmup1Seconds'):
            if not math.isfinite(float(row[field])) or float(row[field])<=0: raise ValueError('Invalid timing')
        cpu = float(row['cpuSeconds'])
        if not (math.isnan(cpu) or math.isfinite(cpu) and cpu>=0): raise ValueError('Invalid CPU counter')
        for field in ('constructionSeconds','samplingSeconds','diagnosticsSeconds'):
            value = float(row[field])
            if (not math.isnan(value) if kind=='graphWide' else not math.isfinite(value) or value<0):
                raise ValueError('Invalid phase timing')
        for field in ('gcMillis','heapBeforeBytes','heapAtReturnBytes','sumHeapPoolPeaksBytes','heapRetainedBytes'):
            if int(row[field])<0: raise ValueError('Invalid memory/GC counter')
        if int(row['compilationMillis']) < -1: raise ValueError('Invalid compilation counter')
        if any(row[f] not in ('true','false') for f in ('gcBeforeObserved','gcAfterObserved')):
            raise ValueError('Missing GC observation flag')
        count = 1 if jobs=='single' else 2
        dimension = 1 if kind=='graphWide' else 8 if kind=='likelihood8' else 32
        if int(row['storedValues']) != count*4*draws*dimension: raise ValueError('Changed retained work')
        evaluations = int(row['evaluations'])
        if (evaluations != -1 if kind=='graphWide' else evaluations<=0): raise ValueError('Invalid evaluations')
        hashes = row['fingerprints'].split(';')
        if len(hashes)!=count or any(not re.fullmatch('[0-9a-f]{64}', h) for h in hashes):
            raise ValueError('Invalid result hash')
        for job, h in enumerate(hashes):
            key = kind, draws, r, job
            if work.setdefault(key,h)!=h: raise ValueError('Changed traces/diagnostics/work')
        # Compare evaluation totals for equal job cardinality; serial/overlap must match.
        key = kind, draws, r, count, 'evaluations'
        if work.setdefault(key,evaluations)!=evaluations: raise ValueError('Changed evaluations')
        allocations = [int(row[f]) for f in BASE[25:]]
        if mode=='plain':
            if row['recordingHash']!='none' or allocations!=[-1]*11: raise ValueError('Profile data in plain case')
        elif (not re.fullmatch('[0-9a-f]{64}',row['recordingHash']) or any(v<0 for v in allocations)
              or int(row['allocationSamples'])<=0 or int(row['lostBytes'])!=0
              or int(row['truncatedSamples'])>int(row['allocationSamples'])):
            raise ValueError('Incomplete/lossy profile')
    return rows


def summary(rows):
    def median(selected, field): return stats.median(float(x[field]) for x in selected)
    def select(kind, draws, workers, variant='reference', jobs='single', mode='plain'):
        return [x for x in rows if (x['kind'],x['draws'],x['workers'],x['variant'],x['jobs'],x['mode'])
                == (kind,str(draws),str(workers),variant,jobs,mode)]
    print(f'Validated {len(rows)} complete cases, two result-matched discarded warm-ups each.')
    for kind in (*KINDS, 'graphWide'):
        for draws in ((1000,4000) if kind=='graphWide' else (4000,16000)):
            serial, parallel = select(kind,draws,1), select(kind,draws,4)
            if not serial or not parallel: continue
            gains = [float(a['wallSeconds'])/float(b['wallSeconds']) for a,b in zip(serial,parallel)]
            print(f'{kind} draws={draws}: W1/W4 median={stats.median(gains):.3f} range={min(gains):.3f}-{max(gains):.3f}; '
                  f'W4 OS peak MiB={median(parallel,"osPeakBytes")/2**20:.1f}; '
                  f'retained heap MiB={median(parallel,"heapRetainedBytes")/2**20:.1f}')
    for kind in KINDS:
        a,b=select(kind,4000,4),select(kind,4000,4,'loop')
        if a and b: print(f'{kind} application callback total ratio={stats.median(float(x["wallSeconds"])/float(y["wallSeconds"]) for x,y in zip(a,b)):.3f}')
    print('JVM/seed rounds are repetition units; ranges are descriptive, not confidence intervals. Profile timings are not speed evidence.')


def run(args):
    manifest, sha, classpath = runtime(args.runtime)
    args.output = args.output.resolve(); args.output.mkdir(); grant(args.output,args.acl_script)
    combined = args.output/'resource-results.csv'
    with combined.open('x',encoding='utf-8',newline='') as out:
        grant(combined,args.acl_script)
        writer=csv.DictWriter(out,FIELDS,quoting=csv.QUOTE_ALL,lineterminator='\n'); writer.writeheader(); out.flush()
        plan=schedule(args.smoke)
        for i,case in enumerate(plan):
            if runtime(args.runtime)[1]!=sha: raise ValueError('Runtime changed')
            temp=args.output/f'tmp-{i}'; temp.mkdir(); grant(temp,args.acl_script)
            log=args.output/f'case-{i}.log'; profile=args.output/f'case-{i}.jfr'
            command=[str(args.java.resolve()),'-Xms1G','-Xmx6G','-Xss6M','-XX:-UsePerfData','-Dfile.encoding=UTF-8',
                     f'-Djava.io.tmpdir={temp}',f'-Duser.home={temp}','-cp',classpath,
                     'com.cra.figaro.example.ResourceScalingStudy',*map(str,case)]
            if case[-1]=='profile':
                command += [str(profile)]
                if args.acl_script: command += [str(Path(args.acl_script).resolve())]
            command += ['hold']
            print(f'Starting {i+1}/{len(plan)}: {case}',flush=True)
            with log.open('x',encoding='utf-8') as stream:
                grant(log,args.acl_script)
                child=subprocess.Popen(command,cwd=args.output,stdin=subprocess.PIPE,stdout=stream,stderr=subprocess.STDOUT)
                try:
                    deadline=time.monotonic()+300
                    while 'RESOURCE_READY' not in log.read_text(encoding='utf-8'):
                        if child.poll() is not None: raise RuntimeError(f'Case {i} failed; inspect its retained log')
                        if time.monotonic()>deadline: raise TimeoutError(f'Case {i} exceeded 300 seconds')
                        time.sleep(0.1)
                    source,peak=os_peak(child.pid)
                    child.communicate(b'\n',timeout=30)
                    if child.returncode: raise RuntimeError(f'Case {i} failed after measurement')
                finally:
                    if child.poll() is None:
                        child.kill(); child.communicate(timeout=30)
                    grant(temp,args.acl_script,recursive=True)
                    if profile.exists(): grant(profile,args.acl_script)
            row,warm=parse_log(log.read_text(encoding='utf-8'))
            row.update(dict(zip(PREFIX,map(str,(i,manifest['revision'],sha,source,peak,
                       digest(profile) if profile.exists() else 'none',*warm)))))
            writer.writerow(row); out.flush()
            print(f'Completed {i+1}/{len(plan)}: {row["wallSeconds"]} seconds',flush=True)
    summary(load(combined.read_text(encoding='utf-8'),args.smoke))


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    modes=parser.add_subparsers(dest='mode',required=True)
    running=modes.add_parser('run')
    for name in ('java','runtime','output'): running.add_argument('--'+name,type=Path,required=True)
    running.add_argument('--acl-script')
    checking=modes.add_parser('check'); checking.add_argument('csv',type=Path)
    for mode in (running,checking): mode.add_argument('--smoke',action='store_true')
    args=parser.parse_args()
    if args.mode=='run': run(args)
    else: summary(load(args.csv.read_text(encoding='utf-8'),args.smoke))


if __name__=='__main__': main()
