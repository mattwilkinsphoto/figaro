"""Render the maintained experiment report with ReportLab and marked.

Research-only documentation tooling; no Figaro runtime dependency.
See tools/docs/README.md for versions, rendering and content verification.
"""
import argparse
import hashlib
from functools import partial
from html import escape
import json
import os
from pathlib import Path
import re
import subprocess
from urllib.parse import urljoin

from reportlab.lib import colors
import reportlab
from reportlab.lib.enums import TA_LEFT
from reportlab.lib.pagesizes import letter
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.pdfgen.canvas import Canvas
from reportlab.platypus import (BaseDocTemplate, Frame, PageTemplate, Paragraph,
                               Spacer, PageBreak, CondPageBreak, LongTable, TableStyle)
from reportlab.platypus.tableofcontents import TableOfContents

NAVY = colors.HexColor('#19354A')
TEAL = colors.HexColor('#086B77')
GRAY = colors.HexColor('#52616B')
LIGHT = colors.HexColor('#EDF3F5')
BASE_URL = 'https://github.com/mattwilkinsphoto/figaro/blob/main/docs/'

def plain(text):
    return text.translate(str.maketrans({'\u2010':'-', '\u2011':'-', '\u2012':'-',
        '\u2013':'-', '\u2014':'-', '\u2212':'-', '\u00a0':' '}))

def inline(tokens):
    parts=[]
    for token in tokens:
        kind=token['type']
        value=inline(token['tokens']) if 'tokens' in token else escape(plain(token.get('text','')))
        if kind=='strong': value='<b>'+value+'</b>'
        elif kind=='em': value='<i>'+value+'</i>'
        elif kind=='codespan': value='<font name="Courier">'+value+'</font>'
        elif kind=='link':
            url=urljoin(BASE_URL,token['href'])
            value='<link href="'+escape(url,quote=True)+'" color="#086B77">'+value+'</link>'
        elif kind=='br': value='<br/>'
        elif kind not in ('text','escape','strong','em','codespan','link'):
            raise ValueError('Unsupported inline Markdown token: '+kind)
        parts.append(value)
    return ''.join(parts)

class Report(BaseDocTemplate):
    def afterFlowable(self, flowable):
        if hasattr(flowable, 'heading_level'):
            level=flowable.heading_level
            self.canv.bookmarkPage(flowable.key)
            self.canv.addOutlineEntry(flowable.getPlainText(),flowable.key,level-2,False)
            if level==2:
                self.notify('TOCEntry',(0,flowable.getPlainText(),self.page,flowable.key))

def decorate(canvas,doc):
    canvas.saveState()
    width,height=letter
    canvas.setStrokeColor(TEAL);canvas.setLineWidth(.7)
    canvas.line(54,height-38,width-54,height-38)
    canvas.setFont('Helvetica',8)
    canvas.setFillColor(GRAY)
    canvas.drawString(54,height-29,'FIGARO  /  RESEARCH RETROSPECTIVE')
    canvas.drawRightString(width-54,height-29,'Evidence through 26 October 2026')
    canvas.line(54,39,width-54,39)
    canvas.drawString(54,26,'Literature-led experiments | Published baseline: 7.4.0')
    canvas.drawRightString(width-54,26,str(doc.page))
    canvas.restoreState()

def grant(path):
    helper=os.environ.get('FIGARO_ACCESS_HELPER')
    if helper:
        subprocess.run(['pwsh','-NoProfile','-File',helper,'-Paths',str(path.resolve())],check=True)

def build(source,output):
    manifest=output.with_suffix('.json')
    if manifest.exists(): raise FileExistsError(manifest)
    tokens=json.loads(subprocess.check_output(['node',str(Path(__file__).with_name('report_tokens.cjs')),str(source)],text=True,encoding='utf-8'))
    styles=getSampleStyleSheet()
    styles.add(ParagraphStyle(name='ReportBody',fontName='Helvetica',fontSize=10.2,leading=14.4,
                              textColor=NAVY,spaceAfter=9,allowWidows=0,allowOrphans=0))
    styles.add(ParagraphStyle(name='ReportTitle',parent=styles['ReportBody'],fontName='Helvetica-Bold',fontSize=29,leading=33,spaceBefore=12,spaceAfter=16))
    styles.add(ParagraphStyle(name='ReportH2',parent=styles['ReportBody'],fontName='Helvetica-Bold',fontSize=17,leading=21,spaceBefore=12,spaceAfter=11,keepWithNext=True))
    styles.add(ParagraphStyle(name='ReportH3',parent=styles['ReportBody'],fontName='Helvetica-Bold',fontSize=12.2,leading=16,spaceBefore=14,spaceAfter=8,keepWithNext=True))
    styles.add(ParagraphStyle(name='ReportCell',parent=styles['ReportBody'],fontSize=9,leading=12.5,spaceAfter=0))
    styles.add(ParagraphStyle(name='ReportTableHeader',parent=styles['ReportCell'],fontName='Helvetica-Bold',textColor=colors.white))
    styles.add(ParagraphStyle(name='ReportBullet',parent=styles['ReportBody'],leftIndent=12,firstLineIndent=-9))
    styles.add(ParagraphStyle(name='ReportTOC',parent=styles['ReportBody'],fontSize=11,leading=16,spaceBefore=8))
    story=[]; heading_counter=0
    for token in tokens:
        kind=token['type']
        if kind=='space': continue
        if kind=='heading':
            depth=token['depth']; title=plain(token['text'])
            if title=='Executive summary':
                story.extend([PageBreak(),Paragraph('Contents',styles['ReportH2'])])
                toc=TableOfContents();toc.levelStyles=[styles['ReportTOC']]
                story.extend([toc,PageBreak()])
            elif depth==2 and re.match(r'^[1-7]\. ',title): story.append(CondPageBreak(200))
            elif title=='How to read the evidence': story.append(PageBreak())
            para=Paragraph(inline(token['tokens']),styles['ReportTitle' if depth==1 else 'ReportH2' if depth==2 else 'ReportH3'])
            if depth>=2:
                heading_counter+=1;para.key='heading-'+str(heading_counter);para.heading_level=depth
            story.append(para)
        elif kind=='paragraph': story.append(Paragraph(inline(token['tokens']),styles['ReportBody']))
        elif kind=='list':
            for item in token['items']:
                children=item['tokens']
                text=' '.join(inline(c.get('tokens',[])) for c in children if c['type'] in ('text','paragraph'))
                story.append(Paragraph('- '+text,styles['ReportBullet']))
        elif kind=='table':
            data=[[Paragraph(inline(c['tokens']),styles['ReportTableHeader']) for c in token['header']]]
            data += [[Paragraph(inline(c['tokens']),styles['ReportCell']) for c in row] for row in token['rows']]
            for start in range(1,len(data),9):
                if start>1:
                    story.extend([PageBreak(),Paragraph('Executive summary (continued)',styles['ReportH2'])])
                table=LongTable([data[0]]+data[start:start+9],colWidths=[135,240,129],repeatRows=1,hAlign='LEFT')
                table.setStyle(TableStyle([('BACKGROUND',(0,0),(-1,0),NAVY),('ROWBACKGROUNDS',(0,1),(-1,-1),[colors.white,LIGHT]),
                    ('VALIGN',(0,0),(-1,-1),'TOP'),('LEFTPADDING',(0,0),(-1,-1),8),('RIGHTPADDING',(0,0),(-1,-1),8),
                    ('TOPPADDING',(0,0),(-1,-1),7),('BOTTOMPADDING',(0,0),(-1,-1),7),
                    ('LINEBELOW',(0,0),(-1,0),1,TEAL)]))
                story.extend([table,Spacer(1,12)])
        else: raise ValueError('Unsupported block Markdown token: '+kind)
    if not output.parent.exists():
        output.parent.mkdir(parents=True);grant(output.parent)
    # Create/grant before ReportLab writes, preserving explicit workspace access.
    with output.open('xb'): grant(output)
    doc=Report(str(output),pagesize=letter,leftMargin=54,rightMargin=54,topMargin=53,bottomMargin=54,
               title='What Figaro learned from literature-led experiments',author='Figaro contributors',
               subject='Sampling, particle filtering, GVM mixtures and MCMC adaptation; evidence through 2026-10-26')
    doc.addPageTemplates(PageTemplate(id='report',frames=Frame(54,54,504,685,leftPadding=0,rightPadding=0,topPadding=0,bottomPadding=0),onPage=decorate))
    doc.multiBuild(story,canvasmaker=partial(Canvas,invariant=1))
    record=dict(sourceFilename=source.name,sourceSha256=hashlib.sha256(source.read_bytes()).hexdigest(),
                pdfFilename=output.name,pdfSha256=hashlib.sha256(output.read_bytes()).hexdigest(),
                reportlab=reportlab.Version,marked=subprocess.check_output(['node','-p',"require('marked/package.json').version"],text=True).strip(),
                generatorSha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
                tokenizerSha256=hashlib.sha256(Path(__file__).with_name('report_tokens.cjs').read_bytes()).hexdigest())
    with manifest.open('x',encoding='utf-8') as stream:
        grant(manifest);json.dump(record,stream,indent=2);stream.write('\n')
    print(json.dumps(dict(output=str(output),source=str(source),bytes=output.stat().st_size)))

if __name__=='__main__':
    parser=argparse.ArgumentParser()
    parser.add_argument('--source',type=Path,default=Path('docs/LITERATURE_EXPERIMENTS_REPORT.md'))
    parser.add_argument('--output',type=Path,default=Path('docs/reports/FIGARO_LITERATURE_EXPERIMENTS.pdf'))
    args=parser.parse_args();build(args.source,args.output)
