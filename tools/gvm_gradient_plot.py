"""Plot the saved frozen-study outcomes, including unsuccessful fits."""
import argparse
import json
from pathlib import Path
import numpy as np
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
from gvm_study_io import grant


def plot(results,summary,output):
    rows=json.loads(results.read_text())['records'];stats=json.loads(summary.read_text())
    plt.rcParams.update({'font.size':10})
    fig,axes=plt.subplots(1,3,figsize=(15,4.6),layout='constrained')
    colors={'analytic':'#187b85','bobyqa':'#be6b32'}
    for index,method in enumerate(('analytic','bobyqa')):
        fractions=[];work=[]
        for k in (1,2,3):
            selected=[r for r in rows if r['section']=='primary' and r['method']==method and r['k']==k]
            fractions.append(100*np.mean([r['fit']['status']=='Converged' for r in selected]))
            work.append(np.median([r['fit']['phasePoints'] for r in selected])/1e6)
        axes[0].bar(np.arange(3)+(index-.5)*.32,fractions,.32,label=method,color=colors[method])
        axes[1].plot((1,2,3),work,'o-',label=method,color=colors[method])
    axes[0].set(xticks=range(3),xticklabels=['1','2','3'],xlabel='GVM components',ylabel='Converged fits (%)',ylim=(0,105),title='Primary completion, all 64 datasets')
    axes[0].axhline(95,color='gray',ls='--',lw=1);axes[0].legend()
    axes[1].set(xticks=(1,2,3),xlabel='GVM components',ylabel='Median phase points (millions)',title='Work includes unsuccessful fits')
    axes[1].axhline(20,color='gray',ls='--',lw=1)
    for i,cell in enumerate(stats['pairedScoreComparisons']):
        if cell['mean'] is not None:
            axes[2].errorbar(cell['mean'],i,xerr=[[cell['mean']-cell['lower']],[cell['upper']-cell['mean']]],fmt='o',color=colors['analytic'],markersize=3)
    axes[2].axvline(0,color='gray',lw=1);axes[2].axvline(-.02,color='red',ls='--',lw=1)
    axes[2].set(xlabel='Analytic minus BOBYQA (nats/point)',ylabel='Predeclared comparison index',title='Paired score intervals, all retained fits')
    fig.suptitle('Test-only GVM optimizer study — no public promotion',fontsize=14)
    with output.open('xb') as stream:
        grant(output);fig.savefig(stream,format='png',dpi=150)
    plt.close(fig)


if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('results',type=Path);parser.add_argument('summary',type=Path)
    parser.add_argument('output',type=Path);args=parser.parse_args();plot(args.results,args.summary,args.output)
