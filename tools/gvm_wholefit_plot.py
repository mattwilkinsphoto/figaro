"""Render saved results, including all refusals and budget outcomes."""
import argparse,json
from pathlib import Path
import numpy as np
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
from gvm_study_io import grant


def plot(summary,diagnostics,output):
    stats=json.loads(summary.read_text());data=json.loads(diagnostics.read_text())
    fig,axes=plt.subplots(2,2,figsize=(12,8),layout='constrained')
    colors={'em':'#b66530','squarem':'#167e88'};labels={'em':'Unaccelerated','squarem':'Safeguarded SQUAREM'}
    for i,method in enumerate(('em','squarem')):
        group=[r for r in data['groups'] if r['family']=='all' and r['method']==method]
        axes[0,0].bar(np.arange(3)+(i-.5)*.32,[100*r['statuses'].get('Stationary',0)/r['count'] for r in group],.32,color=colors[method],label=labels[method])
        axes[0,1].plot((1,2,3),[r['medianWork']/1e6 for r in group],'o-',color=colors[method],label=labels[method])
        families=['curved','seam','three-separated','three-overlap','three-wrapped']
        group=[next(r for r in data['groups'] if r['family']==family and r['k']==3 and r['method']==method) for family in families]
        axes[1,0].bar(np.arange(5)+(i-.5)*.32,[100*r['statuses'].get('Stationary',0)/r['count'] for r in group],.32,color=colors[method])
    axes[0,0].set(xticks=range(3),xticklabels=['1','2','3'],ylim=(0,105),xlabel='Fitted components K',ylabel='Stationary fits (%)',title='Qualification across all 80 datasets')
    axes[0,0].axhline(95,color='gray',ls='--',lw=1);axes[0,0].legend(fontsize=9)
    axes[0,1].set(xticks=(1,2,3),xlabel='Fitted components K',ylabel='Median total work (million units)',title='Includes every budget-limited fit')
    axes[0,1].axhline(20,color='gray',ls='--',lw=1)
    axes[1,0].set(xticks=range(5),xticklabels=['Curved\ntrue K=1','Seam\ntrue K=2','Separated\ntrue K=3','Overlap\ntrue K=3','Wrapped GMM\ntrue K=3'],ylim=(0,105),ylabel='Stationary fits (%)',title='K=3: complexity and separation matter')
    for i,cell in enumerate(stats['noninferiority']):
        if cell['lower'] is not None:
            axes[1,1].plot([cell['lower'],cell['mean']],[i,i],color=colors['squarem'],lw=1)
            axes[1,1].plot(cell['mean'],i,'o',color=colors['squarem'],ms=3)
    axes[1,1].axvline(-.02,color='red',ls='--',lw=1);axes[1,1].axvline(0,color='gray',lw=1)
    axes[1,1].set(xlabel='Candidate minus baseline (nats/point)',ylabel='Frozen comparison index',title='Mean and one-sided adjusted lower bound')
    fig.suptitle('Whole-fit GVM research — qualification is not global optimality',fontsize=14)
    with output.open('xb') as stream:
        grant(output);fig.savefig(stream,format='png',dpi=160)
    plt.close(fig)


if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('summary',type=Path);p.add_argument('diagnostics',type=Path);p.add_argument('output',type=Path)
    a=p.parse_args();plot(a.summary,a.diagnostics,a.output)
