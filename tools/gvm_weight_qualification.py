"""Stage 1: fixed, balanced development reuse of GVM-07 data, never confirmation."""
import argparse, copy, json, shutil, time
from pathlib import Path
import numpy as np
import gvm_dimension_controls as c
import gvm_wholefit_math as m
import gvm_weight_solver as w
import gvm_weight_reference as reference
from gvm_study_io import grant,save,sha


def run(previous,output,summary):
    begun=time.time(); source=previous/'results.json'; old=json.loads(source.read_text())
    rows=[r for r in old['records'] if r['method']=='squarem' and r['k'] in (2,3)
          and r['seed'] in (1050001,1050002)]
    assert len(rows)==40
    reports=[]; inputs={}
    for row in sorted(rows,key=lambda r:(r['d'],r['family'],r['seed'],r['k'])):
        key=f"d{row['d']}-{row['family']}-{row['seed']}"; name=key+'.npz'
        assert sha(previous/name)==old['inputSha256'][name]
        if name not in inputs:
            shutil.copy2(previous/name,output/name);grant(output/name);inputs[name]=sha(output/name)
        with np.load(previous/name) as data:train=c.transform(data['train'],data['center'],data['lower'])
        original=copy.deepcopy(row['fit']);model=dict(components=copy.deepcopy(original['components']))
        logs=np.array([c.gvm_log(train,dict(components=[dict(a,weight=1.)])) for a in model['components']]).T
        before=m.stationarity(train,model);start=[a['weight'] for a in model['components']]
        weights,report=w.solve(logs,start)
        for a,weight in zip(model['components'],weights):a['weight']=float(weight)
        after=m.stationarity(train,model); checked=reference.evaluate(logs,weights)
        numeric=dict(objectiveError=abs(checked['objective']+float(c.gvm_log(train,model).mean())))
        if 'gradient' in report:
            numeric.update(gradientError=float(max(abs(np.array(checked['gradient'])-report['gradient']))),
                           gapError=abs(checked['gap']-report['gap']))
            assert numeric['gradientError']<2e-6
            assert numeric['gapError']<=report['allowance']
        assert numeric['objectiveError']<1e-8
        support=min(after['minimumMass'],after['minimumEss'])>=max(30,5*row['d'])
        improvement=before['objective']-checked['objective']
        relevant=bool(report['qualified'] and support and improvement>1e-8 and after['blocks']['weight']<before['blocks']['weight'])
        record=dict(d=row['d'],family=row['family'],seed=row['seed'],k=row['k'],input=name,
            originalModel=dict(components=original['components']),model=model,before=before,after=after,
            polish=report,reference=checked,numerical=numeric,support=support,improvement=improvement,relevant=relevant)
        reports.append(record);save(output/f'{key}-k{row["k"]}.json',record)
        print(json.dumps(dict(case=key,k=row['k'],status=report['status'],support=support,
                              improvement=improvement,relevant=relevant)),flush=True)
    result=dict(stage='development relevance only',previousResultsSha256=sha(source),inputSha256=inputs,
        cases=len(reports),qualified=sum(r['polish']['qualified'] for r in reports),
        supportValid=sum(r['support'] for r in reports),relevant=sum(r['relevant'] for r in reports),
        maximumObjectiveError=max(r['numerical']['objectiveError'] for r in reports),
        maximumGradientError=max(r['numerical'].get('gradientError',0) for r in reports),
        elapsedSeconds=time.time()-begun,records=reports)
    result['proceed']=result['relevant']>0
    save(output/'results.json',result)
    public={key:value for key,value in result.items() if key not in ('records','inputSha256')}
    public['resultsSha256']=sha(output/'results.json');save(summary,public);print(json.dumps(public,indent=2))


if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('previous',type=Path);p.add_argument('output',type=Path);p.add_argument('summary',type=Path)
    a=p.parse_args();run(a.previous,a.output,a.summary)
