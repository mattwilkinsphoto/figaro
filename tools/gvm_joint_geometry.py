"""Development-only coupling/conditioning diagnostics; no identifiability claim."""
import argparse,json
from pathlib import Path
import numpy as np
import gvm_dimension_controls as c
import gvm_wholefit_math as m
from gvm_joint_fit import Coordinates
from gvm_joint_audit import geometry
from gvm_study_io import save,sha

def assess(old,output):
    rows=json.loads((old/'results.json').read_text())['records'];reports=[];inputs={}
    for row in rows:
        if row['method']!='baseline' or row['seed']!=1150001 or row['k']==1:continue
        name=f'd{row["d"]}-{row["family"]}-1150001.npz';inputs[name]=sha(old/name)
        with np.load(old/name) as raw:train=c.transform(raw['train'],raw['center'],raw['lower'])
        model=row['fit'];coords=Coordinates(row['d'],row['k']);x=coords.pack(model)
        def gradient(v):
            _,_,grad=m.derivatives(train,coords.unpack(v));return coords.pullback(v,grad)
        columns=[]
        for i in range(len(x)):
            step=1e-5*max(1,abs(x[i]));p=x.copy();q=x.copy();p[i]+=step;q[i]-=step
            columns.append((gradient(p)-gradient(q))/(2*step))
        raw_h=np.asarray(columns).T;h=(raw_h+raw_h.T)/2
        # Constrain weight directions to sum zero; all other coordinates unchanged.
        basis=[]
        for i in range(len(x)):
            if i%coords.width!=0:
                v=np.zeros(len(x));v[i]=1;basis.append(v)
        for j in range(1,row['k']):
            v=np.zeros(len(x));v[0]=-1;v[j*coords.width]=1;basis.append(v)
        q,_=np.linalg.qr(np.asarray(basis).T);eig=np.linalg.eigvalsh(q.T@h@q)
        scale=max(float(np.max(abs(eig))),1e-30)
        off=h-np.diag(np.diag(h))
        reports.append(dict(d=row['d'],family=row['family'],k=row['k'],**geometry(train,model),
            hessianSymmetryError=float(np.max(abs(raw_h-raw_h.T))),
            offDiagonalFrobeniusFraction=float(np.linalg.norm(off)/max(np.linalg.norm(h),1e-30)),
            minimumTangentCurvature=float(eig.min()),maximumTangentCurvature=float(eig.max()),
            nearFlatDirections=int(np.sum(abs(eig)<1e-6*scale)),negativeDirections=int(np.sum(eig< -1e-6*scale))))
    assert len(reports)==20
    save(output,dict(developmentOnly=True,previousResultsSha256=sha(old/'results.json'),inputSha256=inputs,
        interpretation='Coordinate-dependent finite-difference indicators, not identifiability or constrained-optimum certificates',cases=reports))
    print(json.dumps(dict(cases=len(reports),withNearFlatDirections=sum(r['nearFlatDirections']>0 for r in reports),
        withNegativeDirections=sum(r['negativeDirections']>0 for r in reports)),indent=2))

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('old',type=Path);p.add_argument('output',type=Path);a=p.parse_args();assess(a.old,a.output)
