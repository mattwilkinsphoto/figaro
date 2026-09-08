"""Independent direct-density information integrals/sums, mpmath==1.3.0."""
import mpmath as mp
from common_distribution_reference import law,fmt


def pairs():
    return [('StudentT',[5.,0.,1.],[8.,.4,1.2]),('StudentT',[1.,0.,1.],[2.,1.,2.]),
            ('Cauchy',[0.,1.],[1.,2.]),('Laplace',[0.,1.],[.7,2.]),('Laplace',[0.,1.],[1.,1.]),
            ('LogNormal',[.3,.8],[-.2,1.3]),('Weibull',[1.7,2.3],[2.4,1.4]),
            ('Weibull',[2.,1.],[2.,3.]),('Triangular',[0.,.3,1.],[0.,.7,1.]),
            ('Triangular',[0.,.3,1.],[-.5,.4,1.5]),
            ('Kumaraswamy',[1.4,2.3],[2.2,1.3]),('Kumaraswamy',[1.4,2.3],[1.4,3.1])]


def scalar_reference(name,p,q,precision=50):
    with mp.workdps(precision):
        fp,_,sp,kp=law(name,p); fq,_,sq,kq=law(name,q)
        def split(support):
            lo,hi=support
            return [lo]+sorted(set(x for x in kp+kq if lo<x<hi))+[hi]
        def kl(x):
            a=fp(x); b=fq(x)
            return a*(mp.log(a)-mp.log(b)) if a else mp.mpf(0)
        divergence=mp.inf if sp[0]<sq[0] or sp[1]>sq[1] else mp.quad(kl,split(sp))
        intersection=(max(sp[0],sq[0]),min(sp[1],sq[1]))
        affinity=mp.quad(lambda x: mp.sqrt(fp(x)*fq(x)),split(intersection))
        return divergence,-mp.log(affinity)


def count_reference(name,p,q):
    with mp.workdps(50):
        if name == 'NegativeBinomial':
            def masses(params):
                r,z=map(mp.mpf,params); out=[z**r]
                for k in range(1,1200): out.append(out[-1]*(k-1+r)/k*(1-z))
                return out
            a,b=masses(p),masses(q)
        else:
            def masses(params):
                n,s,d=params
                return [mp.binomial(s,k)*mp.binomial(n-s,d-k)/mp.binomial(n,d) for k in range(min(p[2],q[2])+1)]
            a,b=masses(p),masses(q)
        return sum(x*mp.log(x/y) for x,y in zip(a,b) if x),-mp.log(sum(mp.sqrt(x*y) for x,y in zip(a,b)))


def count_pairs(): return [('NegativeBinomial',[2.,.4],[2.,.6]),('NegativeBinomial',[2.5,.4],[3.2,.6]),('Hypergeometric',[20,7,5],[20,9,5])]


def scala_source():
    lines=['package com.cra.figaro.test.modernization','','import com.cra.figaro.library.atomic.continuous.*',
           'import com.cra.figaro.library.atomic.discrete.*','',
           '// Independent 50-digit density integrals and count sums; tools/common_metrics_reference.py.',
           'private[modernization] object CommonMetricFixtures {',
           '  val scalar: Vector[(ScalarDistribution,ScalarDistribution,Double,Double)] = Vector(']
    rows=[]
    for name,p,q in pairs():
        kl,bd=scalar_reference(name,p,q)
        kernel=lambda params: name+'Distribution('+','.join(map(fmt,params))+')'
        rows.append('    ('+kernel(p)+','+kernel(q)+','+fmt(kl)+','+fmt(bd)+')')
    lines.extend([',\n'.join(rows),'  )','  val count: Vector[(CountDistribution,CountDistribution,Double,Double)] = Vector('])
    rows=[]
    for name,p,q in count_pairs():
        kl,bd=count_reference(name,p,q)
        kernel=lambda params: name+'Distribution('+','.join(map(str if name == 'Hypergeometric' else fmt,params))+')'
        rows.append('    ('+kernel(p)+','+kernel(q)+','+fmt(kl)+','+fmt(bd)+')')
    return '\n'.join(lines+[',\n'.join(rows),'  )','}'])+'\n'


if __name__ == '__main__': print(scala_source(),end='')
