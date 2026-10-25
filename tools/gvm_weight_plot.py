"""Visualize saved GVM-08 outcomes; no selection of successful-only fits."""
import argparse,json
from pathlib import Path
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
import numpy as np
from gvm_study_io import grant

def draw(source,output):
    data=json.loads(source.read_text());arms=list(data['arms']);labels=['Baseline','Weights','Balanced','Combined']
    fig,axes=plt.subplots(1,2,figsize=(11,4.8),layout='constrained')
    colors=['#586575','#2563a6','#bf721b','#21806a'];x=np.arange(3);width=.19
    for i,arm in enumerate(arms):
        cells=data['arms'][arm]['byK']
        for ax,key in zip(axes,['completed','retainedStationary']):
            values=[cells[str(k)][key] for k in (1,2,3)]
            rect=ax.bar(x+(i-1.5)*width,values,width,label=labels[i],color=colors[i])
            ax.bar_label(rect,padding=2,fontsize=8)
    for ax,title in zip(axes,['Complete stationary workflow','Retained stationary model']):
        ax.set_title(title);ax.set_xticks(x,['K=1','K=2','K=3']);ax.set_ylim(0,90)
        ax.set_ylabel('Fits out of 80 (all attempts included)');ax.grid(axis='y',alpha=.2);ax.set_axisbelow(True)
    axes[0].legend(ncol=2,loc='upper right',fontsize=8)
    fig.suptitle('Conditional weight polishing and restart allocation\nSame 20M-unit budget per fit; retained qualification is not search completion',fontsize=12)
    with open(output,'xb') as stream:grant(output);fig.savefig(stream,format='png',dpi=160)
    plt.close(fig)

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('source',type=Path);p.add_argument('output',type=Path);a=p.parse_args();draw(a.source,a.output)
