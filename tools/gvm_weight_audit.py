"""Independent GVM-08 numerical, complete-inventory and comparative validation."""
import argparse,json,math
from pathlib import Path
import numpy as np
from scipy.stats import t
import gvm_dimension_controls as c
from gvm_dimension_audit import reference_log
from gvm_wholefit_audit import difference_check
import gvm_wholefit_math as m
import gvm_weight_reference as reference
from gvm_weight_study import ARMS
from gvm_study_io import save,sha


def inventory(rows,cfg,development=False):
    expected={(d,f,s,k,a) for d in cfg['dimensions'] for f in cfg['families']
        for s in ([cfg['developmentSeed']] if development else cfg['seeds'])
        for k,a in ([(k,a) for k in ([3] if development else cfg['components']) for a in ARMS]
                    +([] if development else [(4,'wrapped')]))}
    keys=[(r['d'],r['family'],r['seed'],r['k'],r['method']) for r in rows]
    if len(set(keys))!=len(keys) or set(keys)!=expected:raise ValueError('Incomplete/duplicate inventory')


def ledger(fit,cap):
    if fit['totalWork']!=sum(fit['work'].values()) or fit['totalWork']>cap:raise ValueError('Incorrect total work')
    if 'workByStart' not in fit:return
    if sum(fit['workByStart'])!=fit['totalWork']:raise ValueError('Incorrect start work')
    for event in fit['schedule']:
        if not 0<=event['before']<=event['after']<=fit['totalWork']:raise ValueError('Invalid event work')
    # Independent replay of least-work choices, from accepted initialization work.
    if fit.get('allocation')=='balanced' and all(s['trace'] for s in fit['starts']):
        charged=[s['checkpoints'][0]['work'] for s in fit['starts']];state=['Active']*len(charged)
        for event in fit['schedule']:
            active=[i for i,x in enumerate(state) if x=='Active']
            expected=sorted(active,key=lambda i:(charged[i],i))[0]
            if event['start']!=expected:raise ValueError('Incorrect allocation')
            if sum(charged)!=event['before']:raise ValueError('Missing charged work')
            charged[expected]+=event['after']-event['before'];state[expected]=event['status']
        if charged!=fit['workByStart']:raise ValueError('Discarded charged work')


def audit(root,cfg,output,protocol=None):
    result=json.loads((root/'results.json').read_text());rows=result['records'];dev=result['development']
    if protocol:
        assert result['protocolSha256']==sha(protocol)
        for path,digest in cfg['sourceSha256'].items():assert sha(path)==digest,path
    for path,digest in result['inputSha256'].items():assert sha(root/path)==digest,path
    inventory(rows,cfg,dev)
    for bad in (rows[:-1],rows+[rows[0]]):
        try:inventory(bad,cfg,dev)
        except ValueError:pass
        else:raise AssertionError('Bad inventory accepted')
    density_error=gradient_error=score_error=weight_error=gap_error=0.;points=gradient_models=0;missing=0;cache={}
    for row in rows:
        key=f"d{row['d']}-{row['family']}-{row['seed']}"
        if key not in cache:
            with np.load(root/(key+'.npz')) as data:
                cache={key:tuple(c.transform(data[name],data['center'],data['lower']) for name in ('train','validation','test'))}
        train,val,test=cache[key];fit=row['fit'];wrapped=row['method']=='wrapped'
        if not wrapped:
            allocation='balanced' if row['method'] in ('balanced','combined') else 'sequential'
            if 'allocation' in fit:assert fit['allocation']==allocation
            ledger(dict(fit,allocation=allocation),cfg['maxWork'])
            assert all(s['maps']<=cfg['maxMapsPerStart'] for s in fit['starts'])
        if not fit['components']:
            missing+=1;assert row['testScore'] is None and row['validationScore'] is None;continue
        weights=np.array([a['weight'] for a in fit['components']]);assert min(weights)>0 and abs(sum(weights)-1)<1e-12
        for a in fit['components']:
            eigen=np.linalg.eigvalsh(a['cov']);assert min(eigen)>=.01-1e-9 and max(eigen)<=25+1e-9
            if not wrapped:assert 0<=a['kappa']<=50 and max(map(abs,a['coeff']))<=4+1e-12
        evaluator=c.gaussian_log if wrapped else c.gvm_log
        for index in (17,len(test)-1):
            error=abs(float(evaluator(test[index:index+1],fit)[0])-reference_log(test[index],fit,'wrapped' if wrapped else 'gvm'))
            assert error<1e-8;density_error=max(density_error,error);points+=1
        for data,score in ((train,fit['trainScore']/len(train)),(val,row['validationScore']),(test,row['testScore'])):
            error=abs(float(evaluator(data,fit).mean())-score);assert error<1e-8;score_error=max(score_error,error)
        for record in fit['starts']:
            assert np.all(np.diff(record['trace'])>=-(1e-8 if wrapped else 1e-10)*len(train)-1e-9)
            for report in record.get('polishes',[]):
                if report.get('qualified'):
                    assert report['upper']<=cfg['weightGap'] and report['upper']==report['gap']+report['allowance']
                assert report.get('work',0)<=cfg['weightLocalWork'] and report.get('calls',0)<=cfg['weightCalls']
        if wrapped:continue
        diag=m.stationarity(train,fit);assert abs(diag['maximum']-fit['diagnostic']['maximum'])<1e-12
        assert min(diag['minimumMass'],diag['minimumEss'])>=max(30,5*row['d'])
        if fit['status']=='Stationary':assert diag['maximum']<=cfg['stationarity']
        if 'retainedQualified' in fit:assert fit['retainedQualified']==(diag['maximum']<=cfg['stationarity'])
        best=max((s for s in fit['starts'] if s['trace']),key=lambda s:s['trace'][-1])
        assert best['start']==fit['selectedStart'] and abs(best['trace'][-1]-fit['trainScore'])<1e-9
        if dev or row['seed']==cfg['seeds'][0]:
            error=difference_check(train,fit);assert error<2e-6,(key,row['method'],error)
            gradient_error=max(gradient_error,error);gradient_models+=1
            logs=np.array([c.gvm_log(train,dict(components=[dict(a,weight=1.)])) for a in fit['components']]).T
            independent=reference.evaluate(logs,weights)
            _,_,grad=m.derivatives(train,fit)
            error=max(abs(np.array(independent['gradient'])-[x['weight'] for x in grad]));assert error<2e-6
            weight_error=max(weight_error,float(error));assert abs(independent['objective']-diag['objective'])<1e-8
            # Exact high-precision gap may be positive: full fit need not be weight-optimal.
            assert independent['gap']>=-1e-12
            raw=np.array([x['weight'] for x in grad]);numeric=float(raw@weights-1e-8*sum(raw)-(1-len(raw)*1e-8)*min(raw))
            error=abs(numeric-independent['gap']);assert error<1e-10;gap_error=max(gap_error,error)
    summary=dict(fits=len(rows),development=dev,resultsSha256=sha(root/'results.json'),
        audit=dict(status='passed',inputHashes=len(result['inputSha256']),highPrecisionPoints=points,
        gradientModels=gradient_models,maximumDensityError=density_error,maximumGradientDifference=gradient_error,
        maximumWeightGradientError=weight_error,maximumGapError=gap_error,maximumScoreError=score_error,
        missingModels=missing,negativeInventoryControls=2),elapsedSeconds=result['elapsedSeconds'])
    if not dev:
        cells=[];ni=[]
        for d in cfg['dimensions']:
            for family in cfg['families']:
                for k in cfg['components']:
                    cell=dict(d=d,family=family,k=k);paired={}
                    for arm in ARMS:
                        subset=sorted([r for r in rows if (r['d'],r['family'],r['k'],r['method'])==(d,family,k,arm)],key=lambda r:r['seed']);paired[arm]=subset
                        cell[arm]=dict(qualified=sum(r['fit']['status']=='Stationary' for r in subset),
                            retainedStationary=sum(bool(r['fit'].get('diagnostic') and r['fit']['diagnostic']['maximum']<=cfg['stationarity']) for r in subset),
                            statuses={s:sum(r['fit']['status']==s for r in subset) for s in sorted({r['fit']['status'] for r in subset})},
                            medianWork=float(np.median([r['fit']['totalWork'] for r in subset])),
                            medianSeconds=float(np.median([r['fit']['seconds'] for r in subset])))
                    cells.append(cell)
                    delta=[a['testScore']-b['testScore'] for a,b in zip(paired['combined'],paired['baseline']) if a['testScore'] is not None and b['testScore'] is not None]
                    lower=None if len(delta)!=len(cfg['seeds']) else float(np.mean(delta)-t.ppf(1-cfg['alpha']/cfg['comparisons'],len(delta)-1)*np.std(delta,ddof=1)/np.sqrt(len(delta)))
                    ni.append(dict(d=d,family=family,k=k,pairs=len(delta),lower=lower,passed=lower is not None and lower>=-cfg['noninferiority']))
        multi={arm:[r for r in rows if r['method']==arm and r['k']>1] for arm in ARMS}
        counts={arm:sum(r['fit']['status']=='Stationary' for r in subset) for arm,subset in multi.items()}
        gates=dict(numerical=True,perCellReliability=all(x['combined']['qualified']/len(cfg['seeds'])>=cfg['qualifiedRate'] for x in cells),
            completionGain=(counts['combined']-counts['baseline'])/len(multi['baseline'])>=cfg['completionGain'],
            scoreNoninferiority=all(x['passed'] for x in ni))
        summary.update(protocolSha256=sha(protocol),cells=cells,noninferiority=ni,multicomponentQualified=counts,
            gates=gates,decision='eligible-for-separate-review' if all(gates.values()) else 'no-promotion')
    save(output,summary);print(json.dumps({k:v for k,v in summary.items() if k not in ('cells','noninferiority')},indent=2))


if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('root',type=Path);p.add_argument('output',type=Path)
    p.add_argument('--protocol',type=Path)
    a=p.parse_args()
    if a.protocol:cfg=json.loads(a.protocol.read_text())
    else:
        from gvm_weight_study import config
        cfg=config()
    audit(a.root,cfg,a.output,a.protocol)
