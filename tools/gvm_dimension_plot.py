"""Plots from measured GVM-05 outcomes; no omitted failed fits in status panel."""
import argparse
import json
from pathlib import Path
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
import numpy as np
from gvm_study_io import grant


def plot(summary_path, destination):
    summary=json.loads(summary_path.read_text())
    fig,axes=plt.subplots(1,2,figsize=(13,5.5),layout='constrained')
    selected=[r for r in summary['comparisons'] if r['budget']==1 and r['control']=='wrapped']
    y=np.arange(len(selected));means=np.array([r['meanGvmMinusControl'] for r in selected])
    lower=np.array([r['simultaneousLower'] for r in selected]);upper=np.array([r['simultaneousUpper'] for r in selected])
    axes[0].errorbar(means,y,xerr=[means-lower,upper-means],fmt='o',color='#17669b',capsize=3)
    axes[0].axvline(0,color='black',linewidth=.8)
    axes[0].set_yticks(y,[f"d={r['d']} {r['family']}" for r in selected]);axes[0].invert_yaxis()
    axes[0].set_xlabel('GVM minus wrapped-GMM log score (nats / point)')
    axes[0].set_title('Larger parameter ceiling; finite models\nSimultaneous intervals across 32 comparisons')
    statuses=sorted({status for m in summary['statusCounts'].values() for status in m})
    bottom=np.zeros(3);methods=['gvm','chart','wrapped']
    for status in statuses:
        counts=np.array([summary['statusCounts'][m].get(status,0) for m in methods])
        axes[1].bar(methods,counts,bottom=bottom,label=status);bottom+=counts
    axes[1].set_ylabel('Fit outcomes, all 256 per method');axes[1].set_title('All outcomes retained; Converged is required')
    axes[1].legend(loc='upper center',bbox_to_anchor=(.5,-.12),fontsize=8)
    fig.suptitle('Higher-dimensional GVM fitting: frozen synthetic comparison',fontsize=14)
    with destination.open('xb') as stream:
        grant(destination);fig.savefig(stream,format='png',dpi=150)
    plt.close(fig)


if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('summary',type=Path);parser.add_argument('output',type=Path)
    args=parser.parse_args();plot(args.summary,args.output)
