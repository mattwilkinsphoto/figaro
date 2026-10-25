"""Independent numerical and reporting audit; never imports the joint optimizer."""
import argparse,json,math,statistics
from pathlib import Path
import mpmath as mp
import numpy as np
from scipy.stats import t
import gvm_dimension_controls as c
import gvm_wholefit_math as m
from gvm_dimension_audit import reference_log
from gvm_wholefit_audit import difference_check
from gvm_wholefit_study import truth
from gvm_joint_audit import ledger,geometry
from gvm_joint_confirm_stats import report
from gvm_study_io import save,sha


def inventory(rows,cfg):
    keys=[tuple(r[x] for x in ('d','family','k','seed','repeat','arm')) for r in rows]
    expected={(d,f,k,s,r,a) for d in cfg['dimensions'] for f in cfg['families']
        for k in cfg['components'] for s in cfg['seeds'] for r in range(cfg['repetitions']) for a in cfg['arms']}
    if len(keys)!=len(set(keys)) or set(keys)!=expected:raise ValueError('Incomplete/duplicate inventory')
    # Independently reconstruct ordering, including within-pair arm position.
    for row in rows:
        index=cfg['seeds'].index(row['seed'])*len(cfg['dimensions'])+cfg['dimensions'].index(row['d'])
        index=index*len(cfg['families'])+cfg['families'].index(row['family'])
        index=index*len(cfg['components'])+cfg['components'].index(row['k'])
        expected_position=cfg['arms'].index(row['arm']) if (index+row['repeat'])%2==0 else 1-cfg['arms'].index(row['arm'])
        if row['position']!=expected_position:raise ValueError('Incorrect counterbalanced position')
        if not math.isfinite(row['wallSeconds']) or row['wallSeconds']<=0:raise ValueError('Invalid elapsed time')
        if not math.isfinite(row['cpuSeconds']) or row['cpuSeconds']<0:raise ValueError('Invalid CPU time')


def scientific(value):
    if isinstance(value,dict):return {k:scientific(v) for k,v in value.items() if k!='seconds'}
    if isinstance(value,list):return [scientific(v) for v in value]
    return value


def stationary(fit,diag):
    if fit['status']=='Stationary':
        assert diag['maximum']<=1e-4,'False stationarity'
        assert len(fit['starts'])==3,'Unfinished starts'
        if 'searchDisposition' in fit:assert fit['searchDisposition']=='AllStartsFinished'


def stats_check(rows,cfg,summary):
    # Independent high-precision CDF check of SciPy's quantile, not a second call
    # to the reporting routine. Positive-t survival is a regularized beta tail.
    mp.mp.dps=60;alpha=mp.mpf(str(cfg['alpha']))/cfg['comparisons'];df=len(cfg['seeds'])-1
    critical=float(t.ppf(1-float(alpha),df));q=mp.mpf(str(critical))
    error=abs(mp.betainc(mp.mpf(df)/2,mp.mpf('.5'),0,df/(df+q*q),regularized=True)/2-alpha)
    assert error<mp.mpf('1e-12')
    # Rebuild pairs by direct selection, not the reporter's grouped lookup.
    totals={a:0. for a in cfg['arms']};costs={s:{} for s in cfg['seeds']}
    for cell in summary['cells']:
        differences=[];completions={a:0 for a in cfg['arms']}
        for seed in cfg['seeds']:
            pair={}
            for arm in cfg['arms']:
                data=[r for r in rows if all(r[key]==cell[key] for key in ('d','family','k')) and r['seed']==seed and r['arm']==arm]
                median=sorted(r['wallSeconds'] for r in data)[len(data)//2]
                totals[arm]+=median;costs[seed][arm]=costs[seed].get(arm,0.)+median
                completions[arm]+=int(all(r['fit'] is not None and r['fit']['status']=='Stationary' for r in data))
                pair[arm]=next(r['testScore'] for r in data if r['repeat']==0)
            if None not in pair.values():differences.append(pair['joint']-pair['baseline'])
        for arm in cfg['arms']:assert completions[arm]==cell[arm+'Complete']
        if len(differences)==len(cfg['seeds']):
            average=mp.fsum(differences)/len(differences)
            sem=mp.sqrt(mp.fsum((mp.mpf(x)-average)**2 for x in differences)/(len(differences)*(len(differences)-1)))
            assert abs(float(average-q*sem)-cell['score']['lower'])<1e-12
        else:assert cell['score'] is None and not cell['scorePassed']
    for arm in cfg['arms']:
        assert abs(totals[arm]-summary['totals'][arm]['sumMedianWall'])<1e-8
        assert abs(math.fsum(r['wallSeconds'] for r in rows if r['arm']==arm)-summary['totals'][arm]['actualWall'])<1e-8
    logs=[mp.log(mp.mpf(costs[s]['joint'])/costs[s]['baseline']) for s in cfg['seeds']]
    mean=mp.fsum(logs)/len(logs);sem=mp.sqrt(mp.fsum((x-mean)**2 for x in logs)/(len(logs)*(len(logs)-1)))
    # Validate the primary log bound before exponentiation. An absolute error on
    # an enormous ratio (e.g. two-seed controls) is not scale-appropriate.
    assert abs(float(mean+q*sem)-summary['costBound']['upper'])<1e-12
    assert math.isclose(float(mp.exp(mean+q*sem)),summary['costBound']['ratioUpper'],rel_tol=1e-12,abs_tol=0)
    return float(error)


def audit(root,protocol,output):
    cfg=json.loads(protocol.read_text());result=json.loads((root/'results.json').read_text());rows=result['records']
    assert result['protocolSha256']==sha(protocol)
    for p,h in cfg['sourceSha256'].items():assert sha(p)==h,p
    for p,h in cfg['priorProtocolSha256'].items():assert sha(p)==h,p
    inventory(rows,cfg)
    expected={f'd{d}-{f}-{s}.npz' for d in cfg['dimensions'] for f in cfg['families'] for s in cfg['seeds']}
    assert set(result['inputSha256'])==expected
    for p,h in result['inputSha256'].items():assert sha(root/p)==h,p
    canonical={};mismatches=[];missing=[];exceptions=[];diagnostics=[]
    density_error=gradient_error=score_error=0.;points=gradient_models=0;data_cache={};checked=set()
    for row in rows:
        key=(row['d'],row['family'],row['seed']);fitkey=key+(row['k'],row['arm'])
        if key not in data_cache:
            name=f'd{key[0]}-{key[1]}-{key[2]}.npz'
            with np.load(root/name) as raw:
                center,lower=c.whitening(raw['train'])
                assert np.array_equal(center,raw['center']) and np.array_equal(lower,raw['lower'])
                assert len(raw['train'])==cfg['train'] and len(raw['test'])==cfg['test'] and len(raw['validation'])==cfg['validation']
                data_cache={key:tuple(c.transform(raw[n],center,lower) for n in ('train','test'))}
                if key not in checked:
                    kind,law=truth(key[0],key[1]);evaluate=c.gvm_log if kind=='gvm' else c.gaussian_log
                    for point in raw['test'][[17,-1]]:
                        e=abs(float(evaluate(point[None,:],law)[0])-reference_log(point,law,'gvm' if kind=='gvm' else 'wrapped'))
                        assert e<1e-8;density_error=max(density_error,e);points+=1
                    checked.add(key)
        train,test=data_cache[key];fit=row['fit']
        if row['repeat']==0:canonical[fitkey]=row
        else:
            original=canonical[fitkey]
            if scientific(fit)!=scientific(original['fit']) or row['testScore']!=original['testScore'] or row['error']!=original['error']:
                mismatches.append(dict(d=row['d'],family=row['family'],k=row['k'],seed=row['seed'],arm=row['arm'],repeat=row['repeat']))
        if row['error'] is not None:exceptions.append(dict(key=list(fitkey),repeat=row['repeat'],error=row['error']))
        if not fit or not fit['components']:
            missing.append(dict(key=list(fitkey),repeat=row['repeat']));assert row['testScore'] is None;continue
        ledger(fit,len(train),row['k'])
        weights=[a['weight'] for a in fit['components']]
        assert min(weights)>=1e-8-1e-12 and abs(sum(weights)-1)<1e-12
        for a in fit['components']:
            eig=np.linalg.eigvalsh(a['cov']);assert eig.min()>=.01-1e-9 and eig.max()<=25+1e-9
            assert 0<=a['kappa']<=50 and max(map(abs,a['coeff']))<=4+1e-12
        for actual,reported in ((float(c.gvm_log(train,fit).mean()),fit['trainScore']/len(train)),
                                (float(c.gvm_log(test,fit).mean()),row['testScore'])):
            e=abs(actual-reported);assert e<1e-9;score_error=max(score_error,e)
        diag=m.stationarity(train,fit)
        assert abs(diag['maximum']-fit['diagnostic']['maximum'])<1e-10
        assert min(diag['minimumMass'],diag['minimumEss'])>=max(30,5*row['d'])-1e-9
        stationary(fit,diag)
        for s in fit['starts']:assert np.all(np.diff(s['trace'])>=-1e-10*len(train)-1e-9)
        if row['repeat']==0:
            for point in test[[17,-1]]:
                e=abs(c.gvm_log(point[None,:],fit)[0]-reference_log(point,fit,'gvm'))
                assert e<1e-8;density_error=max(density_error,float(e));points+=1
            e=difference_check(train,fit);assert e<2e-6,(fitkey,e)
            gradient_error=max(gradient_error,e);gradient_models+=1
            diagnostics.append(dict(d=row['d'],family=row['family'],seed=row['seed'],k=row['k'],arm=row['arm'],
                status=fit['status'],selectedStart=fit.get('selectedStart'),maximum=diag['maximum'],**geometry(train,fit)))
        if len(checked)%10==0 and row['repeat']==0:print(json.dumps(dict(auditedCanonical=gradient_models)),flush=True)
    summary=report(rows,cfg);critical_error=stats_check(rows,cfg,summary)
    numerical=not mismatches and not missing and not exceptions
    summary['gates']['numerical']=numerical
    summary.update(protocolSha256=sha(protocol),resultsSha256=sha(root/'results.json'),fits=len(rows),
        audit=dict(status='passed' if numerical else 'failed',inputHashes=len(expected),
            highPrecisionPoints=points,gradientModels=gradient_models,maximumDensityError=density_error,
            maximumGradientError=gradient_error,maximumScoreError=score_error,criticalTailError=critical_error,
            repeatMismatches=mismatches,missingModels=missing,exceptions=exceptions),diagnostics=diagnostics,
        decision='eligible-for-separate-review' if all(summary['gates'].values()) else 'no-promotion',
        elapsedSeconds=result['elapsedSeconds'])
    save(output,summary)
    print(json.dumps({k:v for k,v in summary.items() if k not in ('pairs','cells','diagnostics','seedCost')},indent=2))


if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('root',type=Path);p.add_argument('output',type=Path)
    p.add_argument('--protocol',type=Path,default=Path('tools/gvm_joint_confirm_protocol_v1.json'));a=p.parse_args()
    audit(a.root,a.protocol,a.output)
