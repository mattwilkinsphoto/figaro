"""GVM-09 independent density/finite-difference audit and descriptive decision."""
import argparse,collections,json
from pathlib import Path
import numpy as np
import gvm_dimension_controls as c
import gvm_wholefit_math as m
from gvm_dimension_audit import reference_log
from gvm_wholefit_audit import difference_check
from gvm_study_io import save,sha


def inventory(rows,cfg):
    keys=[(r['d'],r['family'],r['k'],r['arm']) for r in rows]
    expected={(d,f,k,a) for d in cfg['dimensions'] for f in cfg['families'] for k in cfg['components'] for a in cfg['arms']}
    if len(keys)!=len(set(keys)) or set(keys)!=expected:raise ValueError('Invalid complete inventory')


def ledger(fit,n,k):
    assert sum(fit['work'].values())==fit['totalWork']<=20000000
    if 'ledger' not in fit:return
    assert sum(e['units'] for e in fit['ledger'])==fit['totalWork']
    for key,value in fit['work'].items():assert sum(e['units'] for e in fit['ledger'] if e['kind']==key)==value
    for i,value in enumerate(fit['workByStart']):assert sum(e['units'] for e in fit['ledger'] if e['start']==i)==value
    calls=sum(s.get('joint',{}).get('objectiveCalls',0) for s in fit['starts'])
    assert fit['work']['joint']==2*n*k*calls
    for s in fit['starts']:
        assert s['maps']<=10
        report=s.get('joint',{});assert report.get('objectiveCalls',0)<=2000
        assert report.get('iterations',0)<=200
        qualifications=report.get('qualificationCalls',0)
        charged=sum(e['units'] for e in fit['ledger'] if e['start']==s['start'] and e['kind']=='score')
        # Interrupted qualifications may charge score but not the following gradient.
        assert charged>=n*k*max(0,qualifications-1)


def geometry(data,model):
    _,r,_=m.derivatives(data,model);normalized=r/np.linalg.norm(r,axis=1)[:,None]
    similarity=normalized@normalized.T;np.fill_diagonal(similarity,0)
    features=np.c_[np.ones(len(data)),c.phase_features(data[:,:-1])]
    ratios=[]
    for weights in r:
        gram=(features.T*weights)@features/weights.sum();eigen=np.linalg.eigvalsh(gram)
        ratios.append(float(max(0,eigen[0])/eigen[-1]))
    diag=m.stationarity(data,model)
    return dict(maximumResponsibilityCosine=float(similarity.max()),
        minimumPhaseGramEigenRatio=min(ratios),minimumKappa=min(a['kappa'] for a in model['components']),
        minimumCovarianceEigenvalue=min(float(np.linalg.eigvalsh(a['cov']).min()) for a in model['components']),
        gradientBlocks=diag['blocks'],minimumMass=diag['minimumMass'],minimumEss=diag['minimumEss'])


def audit(root,protocol,output):
    cfg=json.loads(protocol.read_text());result=json.loads((root/'results.json').read_text())
    assert result['developmentOnly'] and result['protocolSha256']==sha(protocol)
    for p,h in cfg['sourceSha256'].items():assert sha(p)==h,p
    assert result['inputSha256']==cfg['inputSha256']
    for p,h in result['inputSha256'].items():assert sha(root/p)==h,p
    rows=result['records'];inventory(rows,cfg);density_error=gradient_error=score_error=0.;points=missing=0;geometry_rows=[]
    for row in rows:
        name=f'd{row["d"]}-{row["family"]}-{cfg["seed"]}.npz'
        with np.load(root/name) as raw:
            train,test=[c.transform(raw[n],raw['center'],raw['lower']) for n in ('train','test')]
        fit=row['fit'];ledger(fit,len(train),row['k'])
        if not fit['components']:missing+=1;assert row['testScore'] is None;continue
        weights=[a['weight'] for a in fit['components']]
        assert min(weights)>=1e-8-1e-12 and abs(sum(weights)-1)<1e-12
        for a in fit['components']:
            eig=np.linalg.eigvalsh(a['cov']);assert eig.min()>=.01-1e-9 and eig.max()<=25+1e-9
            assert 0<=a['kappa']<=50 and max(map(abs,a['coeff']))<=4+1e-12
        for point in test[[17,-1]]:
            error=abs(c.gvm_log(point[None,:],fit)[0]-reference_log(point,fit,'gvm'))
            assert error<1e-8;density_error=max(density_error,float(error));points+=1
        error=difference_check(train,fit);assert error<2e-6,(row['d'],row['family'],row['k'],row['arm'],error)
        gradient_error=max(gradient_error,error)
        for actual,reported in ((float(c.gvm_log(train,fit).mean()),fit['trainScore']/len(train)),
                                (float(c.gvm_log(test,fit).mean()),row['testScore'])):
            error=abs(actual-reported);assert error<1e-9;score_error=max(score_error,error)
        diag=m.stationarity(train,fit)
        assert abs(diag['maximum']-fit['diagnostic']['maximum'])<1e-10
        assert min(diag['minimumMass'],diag['minimumEss'])>=max(30,5*row['d'])-1e-9
        if fit['status']=='Stationary':
            assert diag['maximum']<=1e-4
            if row['arm']=='joint':assert fit['searchDisposition']=='AllStartsFinished' and len(fit['starts'])==3
        for s in fit['starts']:assert np.all(np.diff(s['trace'])>=-1e-10*len(train)-1e-9)
        if row['arm']=='joint':
            for s in fit['starts']:
                if s.get('prefixModel'):
                    geometry_rows.append(dict(d=row['d'],family=row['family'],k=row['k'],start=s['start'],
                        phase='prefix',**geometry(train,s['prefixModel'])))
        geometry_rows.append(dict(d=row['d'],family=row['family'],k=row['k'],arm=row['arm'],phase='retained',**geometry(train,fit)))
    pairs=[]
    for d in cfg['dimensions']:
        for family in cfg['families']:
            for k in cfg['components']:
                paired={a:next(r for r in rows if (r['d'],r['family'],r['k'],r['arm'])==(d,family,k,a)) for a in cfg['arms']}
                a,b=paired['joint'],paired['baseline']
                pairs.append(dict(d=d,family=family,k=k,baselineComplete=b['fit']['status']=='Stationary',
                    jointComplete=a['fit']['status']=='Stationary',baselineWork=b['fit']['totalWork'],jointWork=a['fit']['totalWork'],
                    scoreDifference=None if a['testScore'] is None or b['testScore'] is None else a['testScore']-b['testScore']))
    baseline=sum(p['baselineComplete'] for p in pairs);joint=sum(p['jointComplete'] for p in pairs)
    lost=sum(p['baselineComplete'] and not p['jointComplete'] for p in pairs)
    gates=dict(numerical=True,completionGain=joint-baseline>=cfg['minimumCompletionGain'],
        noLostCompletions=lost<=cfg['maximumLostCompletions'],
        descriptiveScoreMargin=all(p['scoreDifference'] is not None and p['scoreDifference']>=-cfg['scoreMargin'] for p in pairs))
    report=dict(developmentOnly=True,fits=len(rows),inputHashes=len(cfg['inputSha256']),resultsSha256=sha(root/'results.json'),
        protocolSha256=sha(protocol),audit=dict(status='passed',highPrecisionPoints=points,maximumDensityError=density_error,
            maximumPhysicalGradientError=gradient_error,maximumScoreError=score_error,missingModels=missing),
        completed=dict(baseline=baseline,joint=joint,lostBaselineCases=lost),gates=gates,
        decision='eligible-for-fresh-confirmation-plan' if all(gates.values()) else 'park-this-candidate',
        promotion='not-authorized-by-development-screen',pairs=pairs,geometry=geometry_rows,elapsedSeconds=result['elapsedSeconds'],
        statuses={a:dict(collections.Counter(r['fit']['status'] for r in rows if r['arm']==a)) for a in cfg['arms']})
    save(output,report);print(json.dumps({k:v for k,v in report.items() if k not in ('pairs','geometry')},indent=2))

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('root',type=Path);p.add_argument('output',type=Path)
    p.add_argument('--protocol',type=Path,default=Path('tools/gvm_joint_protocol_v1.json'));a=p.parse_args();audit(a.root,a.protocol,a.output)
