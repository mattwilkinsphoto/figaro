"""Independent densities, objective differences, inventory and reporting audit."""
import argparse, copy, json
from pathlib import Path
import numpy as np
from scipy.stats import t
import gvm_dimension_controls as c
from gvm_dimension_audit import reference_log
import gvm_wholefit_math as m
from gvm_wholefit_study import truth
from gvm_study_io import save, sha


def inventory(rows,cfg):
    expected={(d,f,s,k,method) for d in cfg['dimensions'] for f in cfg['families'] for s in cfg['seeds']
              for k,method in [(k,m) for k in cfg['components'] for m in ('em','squarem')]+[(4,'wrapped')]}
    keys=[(r['d'],r['family'],r['seed'],r['k'],r['method']) for r in rows]
    if len(set(keys))!=len(keys) or set(keys)!=expected: raise ValueError('Incomplete/duplicate inventory')


def difference_check(data,model):
    # Independent central differences of the marginal objective, all physical
    # coordinates. Symmetric covariance perturbations change both off-diagonals.
    _,_,gradients=m.derivatives(data,model); worst=0.; h=2e-5
    for j,a in enumerate(model['components']):
        for key in ('weight','mean','cov','alpha','coeff','kappa'):
            value=np.asarray(a[key])
            for index in np.ndindex(value.shape):
                if key=='cov' and index[0]>index[1]: continue
                step=min(h,float(a['weight'])/4) if key=='weight' else h
                if key=='cov': step=min(h,float(np.linalg.eigvalsh(a['cov']).min())*1e-4)
                plus,minus=copy.deepcopy(model),copy.deepcopy(model)
                p,q=value.copy(),value.copy();p[index]+=step;q[index]-=step;factor=1
                if key=='cov' and index[0]!=index[1]:p[index[::-1]]+=step;q[index[::-1]]-=step;factor=2
                plus['components'][j][key]=p.tolist();minus['components'][j][key]=q.tolist()
                if key=='kappa' and float(value)<step:
                    twice=copy.deepcopy(model);twice['components'][j][key]=float(value)+2*step
                    numeric=(3*c.gvm_log(data,model).mean()-4*c.gvm_log(data,plus).mean()+c.gvm_log(data,twice).mean())/(2*step)
                else:
                    numeric=(-c.gvm_log(data,plus).mean()+c.gvm_log(data,minus).mean())/(2*step)
                error=abs(numeric-factor*np.asarray(gradients[j][key])[index]);worst=max(worst,float(error))
    return worst


def audit(root,protocol,output):
    cfg=json.loads(protocol.read_text());result=json.loads((root/'results.json').read_text());rows=result['records']
    assert result['protocolSha256']==sha(protocol)
    for path,digest in cfg['sourceSha256'].items():assert sha(path)==digest,path
    for path,digest in result['inputSha256'].items():assert sha(root/path)==digest,path
    inventory(rows,cfg)
    for bad in (rows[:-1],rows+[rows[0]]):
        try:inventory(bad,cfg)
        except ValueError:pass
        else:raise AssertionError('Bad inventory accepted')
    max_density=max_gradient=max_score=0.;points=gradient_models=missing=0;cache={}
    for row in rows:
        key=f"d{row['d']}-{row['family']}-{row['seed']}"
        if key not in cache:
            with np.load(root/(key+'.npz')) as data:
                cache={key:tuple(c.transform(data[name],data['center'],data['lower']) for name in ('train','validation','test'))}
                kind,law=truth(row['d'],row['family']);evaluate=c.gvm_log if kind=='gvm' else c.gaussian_log
                for index in (17,len(data['test'])-1):
                    point=data['test'][index]
                    error=abs(float(evaluate(point[None,:],law)[0])-reference_log(point,law,'gvm' if kind=='gvm' else 'wrapped'))
                    assert error<1e-8;max_density=max(max_density,error);points+=1
        train,validation,test=cache[key];fit=row['fit'];wrapped=row['method']=='wrapped'
        if not fit['components']:
            missing+=1;assert row['testScore'] is None and row['validationScore'] is None;continue
        weights=[a['weight'] for a in fit['components']]
        assert min(weights)>0 and abs(sum(weights)-1)<1e-12
        for a in fit['components']:
            eigen=np.linalg.eigvalsh(a['cov']);assert eigen.min()>=.01-1e-9 and eigen.max()<=25+1e-9
            if not wrapped:assert 0<=a['kappa']<=50 and max(map(abs,a['coeff']))<=4+1e-12
        evaluate=c.gaussian_log if wrapped else c.gvm_log
        for index in (17,len(test)-1):
            point=test[index];error=abs(float(evaluate(point[None,:],fit)[0])-reference_log(point,fit,'wrapped' if wrapped else 'gvm'))
            assert error<1e-8;max_density=max(max_density,error);points+=1
        for name,data in [('testScore',test),('validationScore',validation)]:
            error=abs(float(evaluate(data,fit).mean())-row[name]);assert error<1e-9;max_score=max(max_score,error)
        error=abs(float(evaluate(train,fit).sum())-fit['trainScore']);assert error<1e-8*len(train);max_score=max(max_score,error)
        for record in fit['starts']:
            assert np.all(np.diff(record['trace'])>=-(1e-8 if wrapped else 1e-10)*len(train)-1e-9)
            if not wrapped:assert record['maps']<=cfg['maxMapsPerStart']
        if not wrapped:
            diag=m.stationarity(train,fit)
            assert abs(diag['maximum']-fit['diagnostic']['maximum'])<1e-12
            assert fit['totalWork']==sum(fit['work'].values())<=cfg['maxWork']
            if fit['status']=='Stationary':
                assert diag['maximum']<=cfg['stationarity'] and min(diag['minimumMass'],diag['minimumEss'])>=max(30,5*row['d'])
            if row['seed']==cfg['seeds'][0]:
                error=difference_check(train,fit);assert error<2e-6,(key,row['k'],row['method'],error)
                max_gradient=max(max_gradient,error);gradient_models+=1
    cells=[];ni=[];selection=[]
    for d in cfg['dimensions']:
        for family in cfg['families']:
            for k in cfg['components']:
                cell=dict(d=d,family=family,k=k)
                paired={method:sorted([r for r in rows if (r['d'],r['family'],r['k'],r['method'])==(d,family,k,method)],key=lambda r:r['seed']) for method in ('em','squarem')}
                for method,data in paired.items():
                    counts={status:sum(r['fit']['status']==status for r in data) for status in sorted({r['fit']['status'] for r in data})}
                    cell[method]=dict(statuses=counts,qualified=counts.get('Stationary',0),
                        medianWork=float(np.median([r['fit']['totalWork'] for r in data])),
                        medianSeconds=float(np.median([r['fit']['seconds'] for r in data])),
                        medianGradient=float(np.median([r['fit']['diagnostic']['maximum'] for r in data if r['fit']['diagnostic'] is not None])))
                cells.append(cell)
                differences=[a['testScore']-b['testScore'] for a,b in zip(paired['squarem'],paired['em']) if a['testScore'] is not None and b['testScore'] is not None]
                lower=None if len(differences)!=len(cfg['seeds']) else float(np.mean(differences)-t.ppf(1-cfg['alpha']/cfg['comparisons'],len(differences)-1)*np.std(differences,ddof=1)/np.sqrt(len(differences)))
                ni.append(dict(d=d,family=family,k=k,pairs=len(differences),mean=float(np.mean(differences)) if differences else None,lower=lower,passed=lower is not None and lower>=-cfg['noninferiority']))
            for seed in cfg['seeds']:
                for method in ('em','squarem'):
                    eligible=[r for r in rows if (r['d'],r['family'],r['seed'],r['method'])==(d,family,seed,method) and r['fit']['status']=='Stationary']
                    winner=min(eligible,key=lambda r:(-r['validationScore'],r['k'])) if eligible else None
                    all_fits=[r for r in rows if (r['d'],r['family'],r['seed'],r['method'])==(d,family,seed,method)]
                    selection.append(dict(d=d,family=family,seed=seed,method=method,k=winner['k'] if winner else None,
                        testScore=winner['testScore'] if winner else None,totalWork=sum(r['fit']['totalWork'] for r in all_fits)))
    primary=[r for r in rows if r['method']!='wrapped'];multi=[r for r in primary if r['k']>1]
    rates={method:sum(r['fit']['status']=='Stationary' for r in multi if r['method']==method)/sum(r['method']==method for r in multi) for method in ('em','squarem')}
    gates=dict(numerical=True,perCellReliability=all(a['squarem']['qualified']/len(cfg['seeds'])>=cfg['qualifiedRate'] for a in cells),
               completionGain=rates['squarem']-rates['em']>=cfg['completionGain'],scoreNoninferiority=all(a['passed'] for a in ni))
    report=dict(protocolSha256=sha(protocol),resultsSha256=sha(root/'results.json'),fits=len(rows),
        audit=dict(status='passed',inputHashes=len(result['inputSha256']),highPrecisionPoints=points,
                   maximumDensityError=max_density,gradientModels=gradient_models,maximumGradientDifference=max_gradient,
                   maximumScoreDifference=max_score,missingModels=missing,negativeInventoryControls=2),
        cells=cells,noninferiority=ni,selection=selection,multicomponentQualifiedRates=rates,gates=gates,
        decision='eligible-for-separate-review' if all(gates.values()) else 'no-promotion',
        elapsedSeconds=result['elapsedSeconds'],
        gaussianStatuses={s:sum(r['method']=='wrapped' and r['fit']['status']==s for r in rows) for s in sorted({r['fit']['status'] for r in rows if r['method']=='wrapped'})})
    save(output,report);print(json.dumps({k:v for k,v in report.items() if k not in ('cells','noninferiority','selection')},indent=2))


if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('root',type=Path);p.add_argument('protocol',type=Path);p.add_argument('output',type=Path)
    args=p.parse_args();audit(args.root,args.protocol,args.output)
