"""Independent 80-digit checks of captured adviser output; no invented Scala results.

Usage: python -B tools/budget_reference.py STUDY_LOG
Small stopping trees independently validate the work identity in Scala tests;
this script independently checks its numerical evaluation with mpmath, and checks
pilot limits using beta-density integration (not Commons Math's inverse routine).
"""
import hashlib
import json
import platform
import sys
from pathlib import Path
import mpmath as m


def beta_cdf(x, a, b):
    if x <= 0:
        return m.mpf(0)
    if x >= 1:
        return m.mpf(1)
    if a == 1:
        return -m.expm1(b * m.log1p(-x))
    if b == 1:
        return x ** a
    if min(a, b) < 10000:
        return m.betainc(a, b, 0, x, regularized=True)
    # Integrate the normalized density in standard-deviation coordinates. Splits
    # resolve a sharply concentrated density without a normal approximation.
    mean = m.mpf(a) / (a+b)
    sd = m.sqrt(m.mpf(a)*b / ((a+b)**2 * (a+b+1)))
    low, high = -mean/sd, (x-mean)/sd
    log_norm = m.loggamma(a+b)-m.loggamma(a)-m.loggamma(b)+m.log(sd)
    def density(z):
        t = mean+sd*z
        return m.exp(log_norm+(a-1)*m.log(t)+(b-1)*m.log1p(-t)) if 0 < t < 1 else m.mpf(0)
    splits = [low] + [m.mpf(z) for z in (-40,-20,-10,-5,0,5,10,20,40) if low < z < high] + [high]
    return m.quad(density, splits)


def miss(n, r, p):
    if r > n or p == 0:
        return m.mpf(1)
    if p == 1:
        return m.mpf(0)
    # Sum just the r lower terms, using a high-precision recurrence; no beta backend.
    term = m.exp(n*m.log1p(-p))
    total = term
    for k in range(1, r):
        term *= m.mpf(n-k+1)/k * p/(1-p)
        total += term
    return total


def work(lo, hi, r, p):
    def capped(n):
        return r/p*(1-miss(n+1,r+1,p))+n*miss(n,r,p)
    return capped(hi)+lo-capped(lo)


def compare(path):
    raw = Path(path).read_bytes()
    rows = []
    intervals = {}
    with m.workdps(80):
        for line in raw.decode('utf-8-sig').splitlines():
            start = line.find('BUDGET_')
            if start < 0:
                continue
            kind, *v = line[start:].split()
            if kind == 'BUDGET_INTERVAL':
                n,k = map(int,v[:2]); c,lo,hi = map(m.mpf,v[2:]); alpha=(1-c)/2
                intervals[(n,k,c)] = (lo,hi)
                # Outward margin deliberately moves tails below alpha. Absolute
                # probability residual accommodates that declared 2e-14 p margin.
                errors=[]
                for observed,a,b,endpoint in ((lo,k,n-k+1,k==0),(1-hi,n-k,k+1,k==n)):
                    if endpoint:
                        errors.append(abs(observed))
                    else:
                        errors.append(abs(beta_cdf(observed,a,b)-alpha))
                error=max(errors); limit=m.mpf('3e-8')
            elif kind == 'BUDGET_PLAN':
                n,k = map(int,v[:2]); c=m.mpf(v[2]); lo,hi,r=map(int,v[3:6]); actual=m.mpf(v[6])
                lower = intervals[(n,k,c)][0]
                assert miss(hi,r,lower) <= m.mpf('.05') + m.mpf('1e-12'), 'Cap misses design aim'
                assert miss(hi-1,r,lower) > m.mpf('.05') - m.mpf('1e-12'), 'Cap is not minimal'
                expected=work(lo,hi,r,m.mpf(k)/n)
                error=abs(actual-expected)/max(1,abs(expected)); limit=m.mpf('2e-9')
            elif kind == 'BUDGET_WORK':
                lo,hi,r=map(int,v[:3]); p,actual=map(m.mpf,v[3:])
                expected=work(lo,hi,r,p)
                error=abs(actual-expected)/max(1,abs(expected)); limit=m.mpf('2e-9')
            else:
                raise AssertionError('Unknown study row')
            rows.append(dict(kind=kind,inputs=v,error=str(error),limit=str(limit),passed=bool(error<=limit)))
    assert len([r for r in rows if r['kind']=='BUDGET_INTERVAL']) == 8, 'Missing interval outputs'
    assert len([r for r in rows if r['kind']=='BUDGET_WORK']) == 6, 'Missing work outputs'
    assert len([r for r in rows if r['kind']=='BUDGET_PLAN']) == 6, 'Missing plan outputs'
    result=dict(input=Path(path).name,sha256=hashlib.sha256(raw).hexdigest(),python=platform.python_version(),
                mpmath=m.__version__,precision_digits=80,rows=rows,passed=all(r['passed'] for r in rows))
    print(json.dumps(result,indent=2))
    assert result['passed'], 'Independent numerical gate failed'


if __name__ == '__main__':
    compare(sys.argv[1])
