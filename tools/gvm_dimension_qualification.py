"""Stage-two cross-language checks and development-only calibration, GVM-05."""
import argparse
import json
import math
import os
from pathlib import Path
import subprocess
import time
import numpy as np
import psutil
from scipy.optimize import minimize
from scipy.special import i0e, i1e
import gvm_dimension_controls as c
from gvm_study_io import grant, save, sha, text_file


def java_command(classes, jar):
    return ['java', '-Xms32m', '-Xmx512m', '-cp', str(classes)+os.pathsep+str(jar), 'GvmDimensionalFit']


def write_data(path, data):
    with Path(path).open('x', encoding='utf-8') as stream:
        grant(path)
        np.savetxt(stream, data, fmt='%.17g')


def invoke(command, input_path, output, k, seed, max_eval=400, max_iter=50, max_points=20000000, seconds=120):
    args = command + [str(input_path), str(k), str(seed), str(max_eval), str(max_iter), str(max_points), str(seconds)]
    begin = time.time()
    with Path(output).open('x', encoding='utf-8') as stream:
        grant(output)
        process_begin = time.perf_counter()
        process = subprocess.Popen(args, stdout=stream, stderr=subprocess.STDOUT)
        measured = psutil.Process(process.pid); sampled_rss = 0
        while process.poll() is None:
            try:
                sampled_rss = max(sampled_rss, measured.memory_info().rss)
            except psutil.NoSuchProcess:
                pass
            if time.time()-begin > seconds+30:
                process.kill(); process.wait(); raise TimeoutError('Candidate exceeded process safety timeout')
            try:
                process.wait(timeout=.02)
            except subprocess.TimeoutExpired:
                pass
        process_seconds = time.perf_counter()-process_begin
    save(str(output)+'.command.json', dict(args=args, startedUnix=begin, finishedUnix=time.time(),
         exitCode=process.returncode, inputSha256=sha(input_path), outputSha256=sha(output), sampledMaximumRss=sampled_rss))
    if process.returncode:
        raise RuntimeError(f'Candidate process failed: {output}')
    result = json.loads(Path(output).read_text())
    result['processSeconds'] = process_seconds
    result['evidenceCaptureSeconds'] = time.time()-begin-process_seconds
    result['sampledMaximumRss'] = sampled_rss or None
    return result


def qualify(output, command):
    records = []; begin = time.time()
    raw = subprocess.check_output(command+['bessel'], text=True)
    text_file(output/'bessel.log', raw)
    for k, log_i, ratio in map(json.loads, raw.splitlines()):
        assert abs(log_i-(math.log(float(i0e(k)))+k)) < 1e-12
        assert abs(ratio-float(i1e(k)/i0e(k))) < 1e-12
    for d in (1, 2, 3):
        rng = np.random.Generator(np.random.PCG64DXSM(150000+d))
        _, law = c.truth(d, 'curved')
        data = c.sample_gvm(rng, law, 500)
        center, lower = c.whitening(data); data = c.transform(data, center, lower)
        path = output/f'control-d{d}.txt'; write_data(path, data)
        fit = invoke(command, path, output/f'control-d{d}.log', 1, 101+d)
        assert fit['components'], fit
        independent_score = float(c.gvm_log(data, fit).sum())
        assert abs(independent_score-fit['trainScore']) < 1e-8*len(data)
        assert fit['phasePoints'] <= 20000000
        for start in fit['starts']:
            assert np.all(np.diff(start['trace']) >= -1e-8*len(data))
        # Independently optimize the conditional phase. Comparisons use a
        # reference initialized at zero, never the candidate's coefficients.
        f = c.phase_features(data[:, :-1]); weights = np.ones(len(data))
        reference = minimize(lambda b: c.resultant_objective(b, f, data[:, -1], weights),
                             np.zeros(f.shape[1]), jac=True, method='L-BFGS-B',
                             bounds=[(-4,4)]*f.shape[1], options={'maxiter': 400, 'ftol': 1e-14, 'gtol': 1e-8})
        candidate_value = c.resultant_objective(np.array(fit['components'][0]['coeff']), f, data[:, -1], weights)[0]
        assert abs(candidate_value-reference.fun) < 1e-5, (d, candidate_value, reference.fun)
        replay = invoke(command, path, output/f'replay-d{d}.log', 1, 101+d)
        assert fit['components'] == replay['components'] and fit['starts'] == replay['starts']
        records.append(dict(d=d, status=fit['status'], independentScoreError=abs(independent_score-fit['trainScore']),
                            independentObjectiveError=abs(candidate_value-reference.fun), replay=True))
    sample = output/'control-d2.txt'
    for name, options, expected in [
        ('phase-budget', {'max_points': 1}, 'PhaseBudgetExhausted'),
        ('time-budget', {'seconds': .000001}, 'TimeBudgetExhausted'),
        ('iteration-budget', {'max_iter': 1}, 'IterationLimit')]:
        result = invoke(command, sample, output/(name+'.log'), 1, 22, **options)
        assert result['status'] == expected, result
        records.append(dict(control=name, status=result['status']))
    rank = output/'rank-deficient.txt'; write_data(rank, np.zeros((100,3)))
    result = invoke(command, rank, output/'rank-deficient.log', 1, 22)
    assert result['status'] == 'RankDeficient'
    records.append(dict(control='rank-deficient', status=result['status']))
    tiny = output/'insufficient-component.txt'; write_data(tiny, np.loadtxt(sample)[:60])
    result = invoke(command, tiny, output/'insufficient-component.log', 3, 22)
    assert result['status'] == 'InsufficientComponent' and not result['components']
    records.append(dict(control='insufficient-component', status=result['status']))
    limited = invoke(command, sample, output/'angular-budget.log', 1, 22, max_eval=12, max_iter=2)
    assert any(r['innerBudgetHits'] > 0 for r in limited['starts'])
    assert limited['status'] in ('AngularBudgetExhausted','IterationLimit')
    records.append(dict(control='angular-budget', status=limited['status']))
    invalid = output/'nonfinite.txt'; write_data(invalid, np.full((100,3), np.nan))
    args = command+[str(invalid), '1', '22']
    with (output/'nonfinite.log').open('x', encoding='utf-8') as stream:
        grant(output/'nonfinite.log'); process = subprocess.run(args, stdout=stream, stderr=subprocess.STDOUT)
    assert process.returncode != 0
    records.append(dict(control='nonfinite', exitCode=process.returncode))
    save(output/'qualification.json', dict(status='passed', seconds=time.time()-begin, records=records,
         besselCases=7, scope='independent densities/objectives, replay and failure controls; no utility claim'))
    print(json.dumps(records, indent=2))


def adverse(output, command):
    records=[]
    for d in (2,3):
        for j,label in enumerate(('weak','overlap','minority','misspecified')):
            rng=np.random.Generator(np.random.PCG64DXSM(180000+d*100+j))
            _,law=c.truth(d,'seam')
            if label=='weak':
                for component in law['components']:component['kappa']=.01
            if label=='overlap':
                for component in law['components']:component['mean']=[0.]*d
            if label=='minority':law['components'][0]['weight']=.97;law['components'][1]['weight']=.03
            data=c.sample_gvm(rng,law,500)
            if label=='misspecified':
                data[:,:-1]=rng.standard_t(4,size=(500,d))
                data[:,-1]=c.wrap(np.sin(data[:,0])+rng.vonmises(0,2,500))
            center,lower=c.whitening(data);data=c.transform(data,center,lower)
            name=f'adverse-{d}-{label}';path=output/(name+'.txt');write_data(path,data)
            for method in ('gvm','wrapped'):
                if method=='gvm':fit=invoke(command,path,output/(name+'.log'),2,19100+d)
                else:
                    fit=c.fit_gaussian(data,2,True,19100+d);save(output/(name+'-wrapped.json'),fit)
                if fit['components']:
                    values=(c.gvm_log if method=='gvm' else c.gaussian_log)(data,fit)
                    assert np.all(np.isfinite(values))
                records.append(dict(d=d,case=label,method=method,status=fit['status'],finiteModel=bool(fit['components'])))
    save(output/'adverse.json',dict(records=records,scope='diagnostic stress cases, not held-out utility gates'))
    print(json.dumps(records,indent=2))


def calibrate(output, command):
    records = []
    for d in (2,3):
        for family in ('curved','seam','local','wrapped'):
            kind, model = c.truth(d,family)
            rng = np.random.Generator(np.random.PCG64DXSM(170000+d*100+('curved','seam','local','wrapped').index(family)))
            data = (c.sample_gvm if kind == 'gvm' else c.sample_gaussian)(rng,model,2000)
            center, lower = c.whitening(data); data = c.transform(data,center,lower)
            label=f'dev-{d}-{family}'; path=output/(label+'.txt');write_data(path,data)
            for method in ('gvm','chart','wrapped'):
                k=1 if family in ('curved','local') else 2
                if method=='gvm':
                    fit=invoke(command,path,output/(label+'-gvm.log'),k,19000+d)
                else:
                    fit=c.fit_gaussian(data,k,method=='wrapped',19000+d)
                    save(output/(label+'-'+method+'.json'),fit)
                records.append(dict(d=d,family=family,method=method,status=fit['status'],seconds=fit['seconds'],
                    phasePoints=fit.get('phasePoints',0),score=float((c.gvm_log if method=='gvm' else c.gaussian_log)(data,fit).mean()) if fit['components'] else None))
                print(json.dumps(records[-1]),flush=True)
    save(output/'development.json',dict(records=records,heldOut=False))


if __name__ == '__main__':
    parser=argparse.ArgumentParser();parser.add_argument('mode',choices=['qualify','develop','adverse'])
    parser.add_argument('output',type=Path);parser.add_argument('--classes',type=Path,required=True)
    parser.add_argument('--jar',type=Path,required=True);args=parser.parse_args()
    {'qualify':qualify,'develop':calibrate,'adverse':adverse}[args.mode](args.output,java_command(args.classes,args.jar))
