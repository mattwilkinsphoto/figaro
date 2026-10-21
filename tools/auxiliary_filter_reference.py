"""Independent oracle: rational Gaussian joint conditioning and finite path enumeration.
Reads raw per-seed Scala output; no dropped failures, no fitted acceptance tolerances.
"""
from fractions import Fraction as F
from itertools import product
from pathlib import Path
import hashlib
import json
import math
import platform
import statistics
import sys
from weighted_filter_reference import reference


def finite_reference():
    emissions = [[F(v) for v in row] for row in
                 [('0.9','0.2','0.1','0.2','0.9'), ('0.1','0.8','0.2','0.8','0.1'), ('0.8','0.3','0.1','0.3','0.8')]]
    masses = [F(0)]*5
    # Independent full-path sum, not filtering or resampling recursion.
    for a,b,c in product(range(5), repeat=3):
        def transition(i,j):
            return F(1 + 8*(j == (i*i+1)%5) + 3*(j == i), 16)
        masses[c] += F(1,5)*emissions[0][a]*transition(a,b)*emissions[1][b]*transition(b,c)*emissions[2][c]
    z = sum(masses)
    return (math.log(float(z)), float(sum((i-2)*masses[i] for i in range(5))/z),
            float(sum((i-2)**2*masses[i] for i in range(5))/z), float(sum(masses[3:])/z))


def check(path):
    raw = Path(path).read_bytes()
    gaussian = reference()
    variance = gaussian[2]-gaussian[1]**2
    refs = {'gaussian': (*gaussian, .5*math.erfc(-gaussian[1]/math.sqrt(2*variance))),
            'nonlinear-finite': finite_reference()}
    modes = ('bootstrap','guided','auxiliary','calibrated-auxiliary','poor-auxiliary')
    groups = {(s,m): [] for s in refs for m in modes}
    for line in raw.decode('utf-8-sig').splitlines():
        if 'APF_RUN ' not in line:
            continue
        s,m,seed,z,mean,second,positive,candidates,pilots,lookahead,densities,nanos,ancestry,collapsed = line.split('APF_RUN ',1)[1].split()
        assert (s,m) in groups and collapsed in ('true','false')
        row = dict(seed=int(seed), log_evidence=float(z), mean=float(mean), second=float(second), positive=float(positive),
                   candidates=int(candidates), pilots=int(pilots), lookahead=int(lookahead), densities=int(densities),
                   nanos=int(nanos), ancestry=float(ancestry), collapsed=collapsed=='true')
        steps = 5 if s=='gaussian' else 3
        assert 0 < row['candidates'] <= steps*256 and row['nanos'] > 0
        assert all(math.isfinite(row[k]) for k in ('mean','second','positive','ancestry'))
        if not row['collapsed']:
            assert math.isfinite(row['log_evidence'])
            assert row['pilots'] == (48*steps if m=='calibrated-auxiliary' else 0)
            if m!='calibrated-auxiliary': assert row['candidates']==steps*256
        else:
            assert row['log_evidence']==-math.inf
        assert row['lookahead'] == 0 if m in ('bootstrap','guided') else row['lookahead'] > 0
        assert row['densities'] == (0 if m=='bootstrap' else 2*row['candidates'])
        groups[s,m].append(row)
    summaries=[]
    for (s,m),rows in groups.items():
        assert len(rows)==512 and sorted(r['seed'] for r in rows)==list(range(1,513))
        ref=refs[s]
        estimates=[math.exp(r['log_evidence']-ref[0]) for r in rows]
        gates=[]
        for name,expected,values in [('evidence',1.0,estimates)] + [
            (key,ref[i+1],[z*r[key] for z,r in zip(estimates,rows)]) for i,key in enumerate(('mean','second','positive'))]:
            average=statistics.mean(values); se=statistics.stdev(values)/math.sqrt(len(values))
            gates.append(dict(metric=name, mean=average, expected=expected, se=se,
                              passed=abs(average-expected)<=6*se+1e-12))
        valid=[r for r in rows if not r['collapsed']]
        # Posterior RMSE is descriptive and explicitly conditional when failures exist.
        rmse={key: math.sqrt(statistics.mean((r[key]-ref[i+1])**2 for r in valid)) if valid else None
              for i,key in enumerate(('mean','second','positive'))}
        total_work=sum(r['candidates']+r['lookahead'] for r in rows)
        variance=statistics.variance(estimates)
        summaries.append(dict(scenario=s, mode=m, runs=len(rows), collapses=len(rows)-len(valid), gates=gates,
                              posterior_rmse_on_valid=rmse, evidence_variance=variance,
                              total_candidates=sum(r['candidates'] for r in rows),
                              total_lookahead=sum(r['lookahead'] for r in rows),
                              total_density_calls=sum(r['densities'] for r in rows),
                              mean_distinct_parents_per_transition=statistics.mean(r['ancestry']/(4 if s=='gaussian' else 2) for r in rows),
                              total_seconds=sum(r['nanos'] for r in rows)/1e9,
                              evidence_variance_times_mean_work=variance*total_work/len(rows),
                              evidence_variance_times_mean_seconds=variance*sum(r['nanos'] for r in rows)/len(rows)/1e9))
    result=dict(input=Path(path).name, sha256=hashlib.sha256(raw).hexdigest(), python=platform.python_version(),
                reference=refs, summaries=summaries,
                caveat='Synthetic fixed ceilings, not equal realized work. Candidate and look-ahead calls are not equal FLOPs. JVM timings are descriptive.',
                passed=all(g['passed'] for s in summaries for g in s['gates']))
    print(json.dumps(result,indent=2,allow_nan=False))
    assert result['passed'], 'Predeclared mean gate failed; preserve all results and investigate'


if __name__=='__main__':
    check(sys.argv[1])
