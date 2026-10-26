"""TJB-01 streaming independent trace audit and predeclared decision gates."""
import argparse,base64,gzip,json,math,platform,time
from pathlib import Path
import numpy as np
import scipy
from gvm_study_io import save,sha
from tied_quantile_audit import summary,RTOL,ATOL

ARMS=['default','jointPrior','scalar','candidate']
NAMES=['product2','product8','correlatedNormal','coupledSupport','curved','separated']

def unique(mapping,key,value):
    assert key not in mapping,('duplicate',key)
    mapping[key]=value

def check_trace(encoded,reported,draws):
    raw=gzip.decompress(base64.b64decode(encoded,validate=True))
    x=np.frombuffer(raw,dtype='>f8').astype(float)
    assert x.size==4*draws,'Wrong trace length'
    x=x.reshape(4,draws)
    values,cuts,constant=summary(x)
    np.testing.assert_allclose(reported,values,rtol=RTOL,atol=ATOL,equal_nan=True)
    return x,values,constant

def score(rows,labels):
    assert rows and all(r['seconds']>0 for r in rows)
    return max(np.mean([r['loss'].get(q,1.) for r in rows]) for q in labels)*np.mean([r['seconds'] for r in rows])

def decide(records,ref,count,complete,audit_ok):
    comparisons={};accuracy={};utility={}
    for f in NAMES:
        truth=ref['fixtures'][f];labels=set(truth['queries'])|set(truth['derived'])
        groups={a:[r for r in records if r['fixture']==f and r['arm']==a] for a in ARMS}
        scores={a:float(score(rows,labels)) if len(rows)==count else None for a,rows in groups.items()}
        controls=[(v,a) for a,v in scores.items() if a!='candidate' and v is not None]
        best=min(controls) if controls and min(v for v,a in controls)>0 else None
        ratio=scores['candidate']/best[0] if best and scores['candidate'] is not None else None
        required=count if count==8 else 30
        passed=sum(r['accurate'] for r in groups['candidate'])
        accuracy[f]=len(groups['candidate'])==count and passed>=required
        limit=.8 if f in ('correlatedNormal','coupledSupport') else 1.25
        utility[f]=ratio is not None and ratio<=limit
        comparisons[f]={'scores':scores,'bestCompleteControl':best[1] if best else None,
          'candidateRatio':ratio,'candidateAccurate':passed,'candidateDelivered':len(groups['candidate']),
          'requiredAccurate':required,'ordinary':truth['ordinary'],'utilityLimit':limit,
          'delivery':{a:len(v)/count for a,v in groups.items()}}
        # Paired leave-one-seed-out sensitivity, not a replacement acceptance criterion or CI.
        if all(len(groups[a])==count for a in ARMS):
            ratios=[]
            for i in range(count):
                s={a:score([r for r in v if r['seedIndex']!=i],labels) for a,v in groups.items()}
                denominator=min(s[a] for a in ARMS if a!='candidate')
                if denominator>0:ratios.append(float(s['candidate']/denominator))
            comparisons[f]['pairedLeaveOneOutRatioRange']=[min(ratios),max(ratios)] if ratios else None
    ordinary=NAMES[:4]
    gates={'complete':bool(complete),'independentAudit':bool(audit_ok),
      'ordinaryAccuracy':all(accuracy[f] for f in ordinary),'ordinaryUtility':all(utility[f] for f in ordinary)}
    return gates,comparisons

def analyze(root,phase):
    started=time.time();log=root/(phase+'.log.gz');receipt=json.loads((root/(phase+'-execution.json')).read_text())
    ref=json.loads((root/'joint-reference-v1.json').read_text())
    assert receipt['sha256']==sha(log),'Execution log changed'
    assert receipt['referenceSha256']==sha(root/'joint-reference-v1.json'),'Truth changed'
    count=8 if phase=='development' else 32
    seeds=ref[phase+'Seeds'];expected={(f,i,a) for f in NAMES for i in range(count) for a in ARMS}
    records=[];seen=set();active=None;reports={};traces={};chains={};finished=0;smokes=0
    failures=[];audit_errors=[];sequence=[]
    with gzip.open(log,'rt',encoding='utf-8',errors='strict') as stream:
      for line in stream:
        v=line.rstrip('\r\n').split('\t');tag=v[0]
        if tag=='BEGIN':
            assert active is None,'Unclosed attempt'
            f,i,a=v[1],int(v[2]),v[3];active=(f,i,a);smoke=v[5]=='true'
            if not smoke:
                assert active in expected and active not in seen,'Duplicate/unknown attempt'
                assert int(v[4])==seeds[i]+10000*NAMES.index(f),'Wrong seed'
                sequence.append(active)
            else:assert f==NAMES[i] and int(v[4])==7412202501+i and a in ARMS
        elif tag in ('SUMMARY','TRACE','CHAIN','END','SMOKE_END','FAIL'):
            assert active==(v[1],int(v[2]),v[3]),'Attempt mismatch'
            if tag=='SUMMARY':unique(reports,v[4],(list(map(float,v[5:12])),v[12]))
            elif tag=='TRACE':unique(traces,v[4],v[5])
            elif tag=='CHAIN':unique(chains,int(v[4]),dict(seed=int(v[5]),acceptance=float(v[6]),initializationAttempts=int(v[7]),samplingSeconds=float(v[8])))
            elif tag=='FAIL':
                failures.append(dict(key=active,error=v[4:6],seconds=float(v[6])))
                active=None;reports.clear();traces.clear();chains.clear()
            elif tag=='SMOKE_END':
                assert smoke and not reports and not traces and not chains
                smokes+=1;active=None
            else:
                assert not smoke
                f,i,a=active;truth=ref['fixtures'][f];qs=truth['queries']
                assert set(reports)==set(traces)==set(qs) and set(chains)==set(range(4)),'Missing query or chain'
                estimates={};loss={};details={};variances={}
                for q in qs:
                    reported,warnings=reports[q]
                    try:x,values,constant=check_trace(traces[q],reported,ref['drawsPerChain'])
                    except AssertionError as e:
                        audit_errors.append(dict(key=(*active,q),error=str(e)));continue
                    estimates[q]=float(x.mean());variances[q]=float(np.var(x,ddof=1))
                    loss[q]=float(((estimates[q]-qs[q]['mean'])/qs[q]['tolerance'])**2)
                    details[q]={'estimate':estimates[q],'normalizedError':math.sqrt(loss[q]),
                      'independentDiagnostics':values,'constantChain':constant,'warnings':warnings}
                for q,t in truth['derived'].items():
                    if t['source'] in variances:
                        estimate=variances[t['source']];loss[q]=float(((estimate-t['mean'])/t['tolerance'])**2)
                        details[q]={'estimate':estimate,'normalizedError':math.sqrt(loss[q])}
                accurate=len(loss)==len(qs)+len(truth['derived']) and all(v<=1 for v in loss.values())
                records.append(dict(fixture=f,seedIndex=i,seed=seeds[i]+10000*NAMES.index(f),arm=a,
                  seconds=float(v[4]),callbackEvaluations=int(v[5]),runnerSeconds=float(v[6]),
                  chains=chains.copy(),accurate=accurate,loss=loss,queries=details))
                seen.add(active);active=None;reports.clear();traces.clear();chains.clear()
                print('Audited',len(records),f,i,a,flush=True)
        elif tag=='COMPLETE':
            assert v[1:]==[phase,str(count*24)];finished+=1
    correct_order=[(f,i,ARMS[(j+i+fi)%4]) for i in range(count) for fi,f in enumerate(NAMES) for j in range(4)]
    assert sequence==correct_order[:len(sequence)],'Arm order changed'
    complete=(receipt['exitCode']==0 and receipt['limitExceeded'] is None and receipt['readerError'] is None and receipt['sourceUnchanged']
      and finished==1 and smokes==8 and active is None and not failures and seen==expected)
    gates,comparisons=decide(records,ref,count,complete,not audit_errors)
    def clean(value):
        if isinstance(value,(float,np.floating)) and not math.isfinite(value):return None
        if isinstance(value,dict):return {k:clean(v) for k,v in value.items()}
        if isinstance(value,(list,tuple)):return [clean(v) for v in value]
        return value
    result=dict(protocol='TJB-01-v1',phase=phase,complete=complete,gates=gates,promotion=False,
      confirmationEligible=phase=='development' and all(gates.values()),comparisons=comparisons,
      attempts=records,failures=failures,independentAuditErrors=audit_errors,missing=sorted(expected-seen),
      unfinishedAttempt=active,seconds=time.time()-started,startedUnix=started,
      python=platform.python_version(),numpy=np.__version__,scipy=scipy.__version__,
      sourceSha256=sha(__file__),diagnosticOracleSha256=sha(Path(__file__).with_name('tied_quantile_audit.py')),
      logSha256=sha(log),referenceSha256=sha(root/'joint-reference-v1.json'))
    save(root/(phase+'-analysis.json'),clean(result))
    print(json.dumps(clean({'gates':gates,'comparisons':comparisons,'failures':failures,'auditErrors':len(audit_errors)}),indent=2))

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('root',type=Path);p.add_argument('phase',choices=['development','confirmation'])
    a=p.parse_args();analyze(a.root,a.phase)
