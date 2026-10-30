"""Build the handbook. Requires reportlab; set HANDBOOK_FONT_DIR to DejaVu fonts."""
from pathlib import Path
import os,re,html,math
from reportlab.platypus import BaseDocTemplate,PageTemplate,Frame,Paragraph,Spacer,PageBreak,Flowable
from reportlab.platypus.tableofcontents import TableOfContents
from reportlab.lib.styles import ParagraphStyle
from reportlab.lib.enums import TA_JUSTIFY
from reportlab.lib.colors import HexColor
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.lib.pagesizes import A4
ROOT=Path(__file__).parent
fonts=Path(os.environ.get('HANDBOOK_FONT_DIR','/usr/share/fonts/truetype/dejavu'))
for name,file in [('Body','DejaVuSerif'),('Bold','DejaVuSerif-Bold'),('Sans','DejaVuSans'),('SansBold','DejaVuSans-Bold')]:
    pdfmetrics.registerFont(TTFont(name,str(fonts/(file+'.ttf'))))
W,H=A4
INK=HexColor('#302820');GOLD=HexColor('#9a7339');PAPER=HexColor('#faf7ef')
styles={
'body':ParagraphStyle('body',alignment=TA_JUSTIFY,fontName='Body',fontSize=11.6,leading=17.4,textColor=INK,spaceAfter=11,allowWidows=0,allowOrphans=0),
'h1':ParagraphStyle('h1',fontName='Bold',fontSize=24,leading=31,textColor=INK,spaceAfter=22,keepWithNext=True),
'h2':ParagraphStyle('h2',fontName='SansBold',fontSize=13,leading=19,textColor=GOLD,spaceBefore=10,spaceAfter=9,keepWithNext=True),
'note':ParagraphStyle('note',alignment=TA_JUSTIFY,fontName='Sans',fontSize=10.5,leading=16,textColor=INK,backColor=HexColor('#eee5d3'),borderPadding=10,spaceBefore=10,spaceAfter=18),
'small':ParagraphStyle('small',alignment=TA_JUSTIFY,fontName='Sans',fontSize=9.5,leading=14,textColor=INK,spaceAfter=13,splitLongWords=True)}
class Doc(BaseDocTemplate):
    def afterFlowable(self,f):
        if isinstance(f,Paragraph) and f.style.name=='h1':
            title=f.getPlainText();key='chapter-'+str(self.seq.nextf('chapter'))
            self.canv.bookmarkPage(key);self.canv.addOutlineEntry(title,key,0,False)
            self.notify('TOCEntry',(0,title,self.page,key))
def cover(c):
    c.setFillColor(HexColor('#1b1916'));c.rect(0,0,W,H,fill=1,stroke=0)
    c.setStrokeColor(HexColor('#c7a56d'));c.setLineWidth(.8);c.rect(26,26,W-52,H-52,fill=0)
    c.rect(34,34,W-68,H-68,fill=0)
    c.setFillColor(HexColor('#c7a56d'));c.setFont('Sans',11);c.drawCentredString(W/2,H-86,'БИБЛИОТЕКА «ПЕРЕПЛЁТ»')
    c.setFont('Bold',37);c.setFillColor(HexColor('#efe6d6'));c.drawCentredString(W/2,H-169,'Погружение в AI')
    c.setFont('Body',15);c.drawCentredString(W/2,H-211,'Технологии, методы и практика')
    c.drawCentredString(W/2,H-235,'искусственного интеллекта')
    # An open volume and a constellation of connected ideas, all vector geometry.
    c.setStrokeColor(HexColor('#c7a56d'));c.setLineWidth(1)
    for i in range(6):
        y=300+i*6
        path=c.beginPath();path.moveTo(110,y);path.curveTo(180,y+35,240,y+35,W/2,y+3)
        path.curveTo(355,y+35,420,y+35,W-110,y);c.drawPath(path)
    c.line(W/2,302,W/2,365)
    nodes=[(150,430),(225,480),(300,419),(380,478),(445,424),(300,540)]
    for i,j in [(0,1),(0,2),(1,2),(1,5),(2,3),(2,4),(3,4),(3,5)]:c.line(*nodes[i],*nodes[j])
    for x,y in nodes:c.setFillColor(HexColor('#c7a56d'));c.circle(x,y,4,fill=1)
    c.setFont('Body',12);c.setFillColor(HexColor('#c1b69f'));c.drawCentredString(W/2,181,'От первого вопроса - к осмысленной практике')
    c.setFont('Sans',10);c.setFillColor(HexColor('#c7a56d'));c.drawCentredString(W/2,100,'УЧЕБНОЕ ПОСОБИЕ  /  2026')
def page(c,d):
    if d.page==1:cover(c);return
    c.saveState();c.setFillColor(PAPER);c.rect(0,0,W,H,fill=1,stroke=0)
    c.setStrokeColor(HexColor('#d6c5a7'));c.line(54,H-38,W-54,H-38);c.line(54,38,W-54,38)
    c.setFillColor(GOLD);c.setFont('Sans',8);c.drawString(54,H-27,'ПОГРУЖЕНИЕ В AI');c.drawRightString(W-54,H-27,'БИБЛИОТЕКА «ПЕРЕПЛЁТ»')
    c.drawString(54,24,'УЧЕБНОЕ ПОСОБИЕ / 2026');c.drawRightString(W-54,24,str(d.page));c.restoreState()
def markup(t):
    t=html.escape(t)
    t=re.sub(r'(https://[^\s]+)',lambda m:'<br/><font name="Sans" size="8.5" color="#806035"><link href="'+m[0]+'">'+m[0]+'</link></font>',t)
    return t
class Diagram(Flowable):
    def wrap(self,a,b):self.width=a;return a,88
    def draw(self):
        c=self.canv;labels=['ВОПРОС','ПОИСК','КОНТЕКСТ','ОТВЕТ'];bw=(self.width-36)/4
        for i,label in enumerate(labels):
            x=i*(bw+12);c.setFillColor(HexColor('#eee5d3'));c.setStrokeColor(GOLD);c.roundRect(x,25,bw,40,4,fill=1)
            c.setFillColor(INK);c.setFont('SansBold',9);c.drawCentredString(x+bw/2,41,label)
            if i<3:c.line(x+bw,45,x+bw+12,45)
output=ROOT/'AI-Handbook-2026.pdf'
output.parent.mkdir(parents=True,exist_ok=True)
doc=Doc(str(output),pagesize=A4,leftMargin=54,rightMargin=54,topMargin=60,bottomMargin=56,title='Погружение в AI',author='Анцибор А.В.',subject='Технологии, методы и практика. Учебное пособие, 2026')
doc.addPageTemplates(PageTemplate(id='book',frames=[Frame(54,56,W-108,H-116,id='body',leftPadding=0,rightPadding=0,topPadding=0,bottomPadding=0)],onPage=page))
story=[Spacer(1,1),PageBreak(),Paragraph('Содержание',styles['h1'])]
toc=TableOfContents();toc.levelStyles=[ParagraphStyle('toc',fontName='Sans',fontSize=10,leading=15,spaceBefore=5,textColor=INK,rightIndent=25)]
story.extend([toc,PageBreak()]);first=True
source=(ROOT/'book.md').read_text();source=source[source.index('# Предисловие'):]
for b in re.split(r'\n\s*\n',source):
    b=b.strip()
    if not b:continue
    if b.startswith('# '):
        if not first:story.append(PageBreak())
        first=False;story.append(Paragraph(markup(b[2:]),styles['h1']))
        if b.startswith('# 11.'):story.append(Diagram())
    elif b.startswith('## '):story.append(Paragraph(markup(b[3:]),styles['h2']))
    elif b.startswith('> '):story.append(Paragraph(markup(b[2:]),styles['note']))
    else:story.append(Paragraph(markup(' '.join(b.splitlines())),styles['small'] if re.match(r'^\[\d+\]',b) else styles['body']))
doc.multiBuild(story)
print(output)
