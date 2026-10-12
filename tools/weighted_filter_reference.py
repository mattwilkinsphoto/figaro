"""Independent Gaussian conditioning oracle using rational joint covariance, not Kalman recursion."""
import hashlib
import json
import math
from fractions import Fraction as F
from pathlib import Path
import platform
import sys


def reference():
    n=5; a=F(4,5); q=F(3,10); r=F(1,2)
    # State covariance from independent initial/process innovations.
    cov=[[a**(i+j)+q*sum(a**(i-k)*a**(j-k) for k in range(1,min(i,j)+1))
          for j in range(n)] for i in range(n)]
    matrix=[[cov[i][j]+(r if i==j else 0) for j in range(n)] for i in range(n)]
    y=list(map(F,('0.2','-0.3','1.5','0.8','-0.2')))
    cross=cov[-1]
    aug=[matrix[i]+[y[i],cross[i]] for i in range(n)]
    determinant=F(1)
    for i in range(n):
        pivot=aug[i][i]; assert pivot>0
        determinant*=pivot
        aug[i]=[x/pivot for x in aug[i]]
        for j in range(n):
            if j!=i:
                factor=aug[j][i]
                aug[j]=[x-factor*z for x,z in zip(aug[j],aug[i])]
    mean=sum(cross[i]*aug[i][n] for i in range(n))
    variance=cov[-1][-1]-sum(cross[i]*aug[i][n+1] for i in range(n))
    logz=-.5*(n*math.log(2*math.pi)+math.log(float(determinant))+float(sum(y[i]*aug[i][n] for i in range(n))))
    return logz,float(mean),float(mean*mean+variance)


def check(path):
    raw=Path(path).read_bytes()
    ref=reference(); expected={'evidence':1.0,'firstMoment':ref[1],'secondMoment':ref[2]}
    rows=[]; oracle=[]
    for line in raw.decode('utf-8-sig').splitlines():
        if 'WEIGHTED_ORACLE ' in line:
            vals=list(map(float,line.split('WEIGHTED_ORACLE ',1)[1].split()))
            assert len(vals)==3 and all(abs(x-y)<=1e-12 for x,y in zip(vals,ref))
            oracle.append(vals)
        elif 'WEIGHTED ' in line:
            mode,metric,e,mean,se,runs,cost=line.split('WEIGHTED ',1)[1].split()
            e,mean,se=map(float,(e,mean,se))
            assert mode in ('fixed','adaptive') and metric in expected
            assert int(runs)==512 and int(cost)>0 and math.isfinite(se) and se>=0
            assert abs(e-expected[metric])<=1e-12
            passed=abs(mean-expected[metric])<=6*se+1e-12
            rows.append(dict(mode=mode,metric=metric,expected=expected[metric],mean=mean,
                             standard_error=se,runs=int(runs),candidates=int(cost),passed=passed))
    assert len(oracle)==1
    assert len(rows)==6 and len({(x['mode'],x['metric']) for x in rows})==6
    result=dict(input=Path(path).name,sha256=hashlib.sha256(raw).hexdigest(),python=platform.python_version(),
                method='rational joint Gaussian conditioning; double log determinant',reference=ref,
                rows=rows,passed=all(x['passed'] for x in rows))
    print(json.dumps(result,indent=2))
    assert result['passed']


if __name__=='__main__':
    check(sys.argv[1])
