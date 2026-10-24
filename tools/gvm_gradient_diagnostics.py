"""Descriptive diagnostics for the predeclared work/iteration curves (not gates)."""
import argparse
from collections import Counter
import json
from pathlib import Path
import numpy as np
from gvm_study_io import save,sha


def diagnose(results,audit,output):
    data=json.loads(results.read_text());rows=data['records'];report=json.loads(audit.read_text())
    groups=[]
    for section,k,cap,iterations,method in sorted(set((r['section'],r['k'],r['cap'],r['iterations'],r['method']) for r in rows)):
        selected=[r for r in rows if (r['section'],r['k'],r['cap'],r['iterations'],r['method'])==(section,k,cap,iterations,method)]
        groups.append(dict(section=section,k=k,cap=cap,iterations=iterations,method=method,fits=len(selected),
            status=dict(Counter(r['fit']['status'] for r in selected)),
            medianSeconds=float(np.median([r['fit']['seconds'] for r in selected])),
            medianPhasePoints=float(np.median([r['fit'].get('phasePoints',0) for r in selected])),
            medianDensityPoints=float(np.median([r['fit'].get('densityPoints',0) for r in selected]))))
    curves=[]
    for row in rows:
        if row['section']!='diagnostic':continue
        base=next(r for r in rows if (r['d'],r['family'],r['seed'],r['k'],r['method'],r['section'])==
                  (row['d'],row['family'],row['seed'],row['k'],row['method'],'gaussian' if row['method']=='wrapped' else 'primary'))
        curves.append(dict(d=row['d'],family=row['family'],seed=row['seed'],method=row['method'],cap=row['cap'],
            iterations=row['iterations'],baselineStatus=base['fit']['status'],status=row['fit']['status'],
            logScoreChange=(row['logScore']-base['logScore']) if row['logScore'] is not None and base['logScore'] is not None else None))
    residuals=[r['maximumFinalResponsibilityGradient'] for r in report['finalResponsibilityGradient']
               if r['section']=='primary' and r['status']=='Converged']
    out=dict(resultsSha256=sha(results),auditSha256=sha(audit),groups=groups,pairedDiagnosticChanges=curves,
             primaryConvergedFinalResponsibilityGradient=dict(count=len(residuals),
                 median=float(np.median(residuals)),maximum=max(residuals),above1eMinus6=sum(x>1e-6 for x in residuals)),
             scope='descriptive predeclared diagnostics; no substitution into primary gates')
    save(output,out);print(json.dumps(out,indent=2))


if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('results',type=Path);parser.add_argument('audit',type=Path)
    parser.add_argument('output',type=Path);args=parser.parse_args();diagnose(args.results,args.audit,args.output)
