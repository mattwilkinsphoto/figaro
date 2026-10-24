"""Independent high-precision point audit of every frozen confirmation fit."""
import argparse
import json
import math
from pathlib import Path
import mpmath as mp
import numpy as np
import gvm_dimension_controls as c
from gvm_study_io import save, sha


def reference_log(point, model, method):
    # Full inverse/determinant joint densities and explicit image sums, rather
    # than the candidate's Cholesky density or control's conditional evaluator.
    with mp.workdps(60):
        terms=[]; d=len(point)-1; p=mp.matrix(point.tolist())
        for component in model['components']:
            mean=mp.matrix(component['mean']); covariance=mp.matrix(component['cov'])
            inverse=covariance**-1; dim=len(mean)
            log_norm=-mp.mpf('.5')*(dim*mp.log(2*mp.pi)+mp.log(mp.det(covariance)))
            if method=='gvm':
                delta=mp.matrix(point[:-1].tolist())-mean
                phase=mp.mpf(component['alpha']);coeff=component['coeff']
                for i in range(d):phase+=mp.mpf(coeff[i])*p[i]
                index=d
                for i in range(d):
                    for j in range(i,d):
                        phase+=mp.mpf(coeff[index])*p[i]*p[j]*(mp.mpf('.5') if i==j else 1);index+=1
                k=mp.mpf(component['kappa'])
                log=log_norm-(delta.T*inverse*delta)[0]/2+k*mp.cos(p[d]-phase)-mp.log(2*mp.pi*mp.besseli(0,k))
                terms.append(mp.mpf(component['weight'])*mp.exp(log))
            else:
                linear=covariance[:d,:d];delta=mp.matrix(point[:-1].tolist())-mean[:d,0]
                regression=covariance[d,:d]*linear**-1
                angular_mean=mean[d]+(regression*delta)[0]
                center=int(mp.nint((angular_mean-p[d])/(2*mp.pi)))
                images=[]
                for winding in range(center-40,center+41):
                    z=p-mean;z[d]+=2*mp.pi*winding
                    images.append(mp.exp(log_norm-(z.T*inverse*z)[0]/2))
                terms.append(mp.mpf(component['weight'])*mp.fsum(images))
        return float(mp.log(mp.fsum(terms)))


def validate_inventory(rows, seeds):
    expected={(d,f,s,b,m) for d in (2,3) for f in ('curved','seam','local','wrapped')
              for s in seeds for b in (0,1) for m in ('gvm','chart','wrapped')}
    keys=[(r['d'],r['family'],r['seed'],r['budget'],r['method']) for r in rows]
    if len(keys)!=len(set(keys)) or set(keys)!=expected:
        raise ValueError('Missing, duplicate or unexpected confirmation case')


def audit(results, protocol, output):
    result=json.loads(results.read_text());config=json.loads(protocol.read_text())
    assert result['protocolSha256']==sha(protocol)
    for path,digest in config['sourceSha256'].items():assert sha(path)==digest,path
    for path,digest in result['inputSha256'].items():assert sha(results.parent/path)==digest,path
    rows=result['records'];validate_inventory(rows,config['confirmationSeeds'])
    maxima={'gvm':0.,'chart':0.,'wrapped':0.,'truth':0.};points=0;missing=0;train_error=0.;dataset_cache={};truth_points=0
    for row in rows:
        key=f"d{row['d']}-{row['family']}-{row['seed']}"
        if key not in dataset_cache:
            with np.load(results.parent/(key+'.npz')) as data:
                dataset_cache={key:(c.transform(data['train'],data['center'],data['lower']),
                                   c.transform(data['test'],data['center'],data['lower']))}
                kind,truth=c.truth(row['d'],row['family'])
                evaluator=c.gvm_log if kind=='gvm' else c.gaussian_log
                for index in (17,len(data['test'])-1):
                    point=data['test'][index]
                    error=abs(float(evaluator(point[None,:],truth)[0])-reference_log(point,truth,'gvm' if kind=='gvm' else 'wrapped'))
                    assert error<1e-8;maxima['truth']=max(maxima['truth'],error);truth_points+=1
        training,test=dataset_cache[key];model=row['fit'];method=row['method']
        if not model['components']:
            assert row['logScore'] is None;missing+=1;continue
        weights=[a['weight'] for a in model['components']]
        assert all(w>0 for w in weights) and abs(sum(weights)-1)<1e-12
        for component in model['components']:
            eigen=np.linalg.eigvalsh(component['cov']);assert min(eigen)>=.01-1e-9 and max(eigen)<=25+1e-9
            if method=='gvm':
                assert 0<=component['kappa']<=50 and max(map(abs,component['coeff']))<=4+1e-12
        evaluate=c.gvm_log if method=='gvm' else c.gaussian_log
        for index in (17,len(test)-1):
            actual=float(evaluate(test[index:index+1],model)[0]);expected=reference_log(test[index],model,method)
            error=abs(actual-expected);assert error<1e-8,(key,method,index,error)
            maxima[method]=max(maxima[method],error);points+=1
        if method=='gvm':
            error=abs(float(c.gvm_log(training,model).sum())-model['trainScore'])
            assert error<1e-8*len(training);train_error=max(train_error,error)
    # Negative inventory controls: incomplete and duplicated grids must refuse.
    for bad in (rows[:-1],rows+[rows[0]]):
        try:validate_inventory(bad,config['confirmationSeeds'])
        except ValueError:pass
        else:raise AssertionError('Broken inventory accepted')
    report=dict(status='passed',fits=len(rows),inputHashesVerified=len(result['inputSha256']),
        highPrecisionPoints=points,highPrecisionTruthPoints=truth_points,unavailableModels=missing,maxAbsoluteLogDensityError=maxima,
        maximumIndependentGvmTrainingScoreError=train_error,negativeInventoryControls=2,
        scope='numerical/inventory audit, not a new optimizer-utility decision',resultsSha256=sha(results))
    save(output,report);print(json.dumps(report,indent=2))


if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('results',type=Path)
    parser.add_argument('protocol',type=Path);parser.add_argument('output',type=Path);args=parser.parse_args()
    audit(args.results,args.protocol,args.output)
