"""Independent numerical/inventory audit; does not tune the frozen candidate."""
import argparse
from collections import Counter
import json
from pathlib import Path
import numpy as np
import gvm_dimension_controls as c
from gvm_dimension_audit import reference_log
from gvm_gradient_study import inventory
from gvm_study_io import save,sha


def audit(results,protocol,output):
    result=json.loads(results.read_text());config=json.loads(protocol.read_text())
    assert result['protocolSha256']==sha(protocol)
    for path,digest in config['sourceSha256'].items():assert sha(path)==digest,path
    for path,digest in result['inputSha256'].items():assert sha(results.parent/path)==digest,path
    rows=result['records'];inventory(rows,config['seeds'])
    for bad in (rows[:-1],rows+[rows[0]]):
        try:inventory(bad,config['seeds'])
        except ValueError:pass
        else:raise AssertionError('Invalid inventory accepted')
    maximum=0.;train_error=0.;points=0;missing=0;datasets={};current_gradient=[]
    for row in rows:
        key=f"d{row['d']}-{row['family']}-{row['seed']}"
        if key not in datasets:
            with np.load(results.parent/(key+'.npz')) as data:
                datasets={key:(c.transform(data['train'],data['center'],data['lower']),
                               c.transform(data['test'],data['center'],data['lower']),
                               float(np.log(np.diag(data['lower'])).sum()))}
                np.testing.assert_allclose(np.loadtxt(results.parent/(key+'.txt')),datasets[key][0],rtol=0,atol=0)
        training,test,jacobian=datasets[key];model=row['fit'];method=row['method']
        if not model['components']:
            assert row['logScore'] is None;missing+=1;continue
        weights=[a['weight'] for a in model['components']]
        assert all(w>0 for w in weights) and abs(sum(weights)-1)<1e-12
        for component in model['components']:
            eigen=np.linalg.eigvalsh(component['cov'])
            assert min(eigen)>=.01-1e-9 and max(eigen)<=25+1e-9
            if method!='wrapped':
                assert 0<=component['kappa']<=50 and max(map(abs,component['coeff']))<=4+1e-12
        evaluate=c.gaussian_log if method=='wrapped' else c.gvm_log
        for index in (17,len(test)-1):
            actual=float(evaluate(test[index:index+1],model)[0])
            expected=reference_log(test[index],model,'wrapped' if method=='wrapped' else 'gvm')
            error=abs(actual-expected);assert error<1e-8,(key,method,error)
            maximum=max(maximum,error);points+=1
        actual=float(evaluate(training,model).sum());error=abs(actual-model['trainScore'])
        assert error<1e-8*len(training);train_error=max(train_error,error)
        assert abs(float(evaluate(test,model).mean()-jacobian)-row['logScore'])<1e-10
        for start in model['starts']:
            assert np.all(np.diff(start['trace'])>=-1e-8*len(training))
        if method!='wrapped':assert model['phasePoints']<=row['cap']
        if method=='analytic':
            candidates=[s for s in model['starts'] if s['trace']]
            chosen=max(candidates,key=lambda s:s['trace'][-1])
            if model['status']=='Converged':
                assert chosen['status']=='Converged'
                assert all(a['qualified'] and a['solverSuccess'] and a['projectedGradient']<=1e-6
                           for a in chosen['angular'][-1])
            # Separate diagnostic at the final responsibilities, not the previous
            # M-step's weights: outer likelihood stabilization need not be KKT.
            logs=np.array([c.gvm_log(training,dict(components=[dict(a,weight=1.)]))+np.log(a['weight']) for a in model['components']])
            from scipy.special import logsumexp
            r=np.exp(logs-logsumexp(logs,axis=0));features=c.phase_features(training[:,:-1]);pg=[]
            for component,weight in zip(model['components'],r):
                beta=np.array(component['coeff']);gradient=c.resultant_objective(beta,features,training[:,-1],weight)[1]
                pg.append(float(np.max(np.abs(beta-np.clip(beta-gradient,-4,4)))))
            current_gradient.append(dict(d=row['d'],family=row['family'],seed=row['seed'],k=row['k'],
                section=row['section'],status=model['status'],maximumFinalResponsibilityGradient=max(pg)))
    report=dict(status='passed',fits=len(rows),inputHashesVerified=len(result['inputSha256']),
        highPrecisionPoints=points,missingModels=missing,maximumLogDensityError=maximum,
        maximumTrainingScoreError=train_error,negativeInventoryControls=2,
        finalResponsibilityGradient=current_gradient,resultsSha256=sha(results),
        scope='numerical correctness and reporting audit; not global-optimum or utility certification')
    save(output,report)
    print(json.dumps({k:v for k,v in report.items() if k!='finalResponsibilityGradient'},indent=2))


if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('results',type=Path);parser.add_argument('protocol',type=Path)
    parser.add_argument('output',type=Path);args=parser.parse_args();audit(args.results,args.protocol,args.output)
