from pathlib import Path
from html.parser import HTMLParser
import json
root=Path(__file__).resolve().parents[1]
static=root/'src/main/resources/static'
class Page(HTMLParser):
 def __init__(self):super().__init__();self.ids=[];self.links=[];self.assets=[];self.stack=[];self.errors=[]
 def handle_starttag(self,tag,attrs):
  a=dict(attrs)
  if 'id' in a:self.ids.append(a['id'])
  if tag=='a':self.links.append(a.get('href',''))
  if tag in ('script','img','link'):self.assets.append(a.get('src') or a.get('href'))
  if tag not in ('meta','link','img','br','hr','input','source','wbr'):self.stack.append(tag)
 def handle_endtag(self,tag):
  if not self.stack or self.stack[-1]!=tag:self.errors.append((tag,self.stack[-1:]));return
  self.stack.pop()
p=Page();p.feed((static/'ai-russia.html').read_text())
assert not p.errors and not p.stack,(p.errors,p.stack)
assert len(p.ids)==len(set(p.ids)),'duplicate IDs'
for link in p.links:
 if link.startswith('#'):assert link[1:] in p.ids,link
 elif link.startswith('/') and not link.startswith('/#'):assert (static/link[1:]).is_file(),link
 else:assert link.startswith(('https://','/#')),link
for asset in p.assets:assert asset and asset.startswith('/') and (static/asset[1:]).is_file(),asset
sources=json.loads((root/'docs/ai-russia-sources.json').read_text())
assert sources['checkedAt']=='2026-10-30'
assert len(sources['sources'])==30
for source in sources['sources']:assert source['url'] in p.links,source['id']
assert len(set(s['id'] for s in sources['sources']))==30
index=(static/'index.html').read_text();assert index.count('data-ai-view="russia"')==1
assert index.count('id="ai-tab-russia"')==1
assert 'text-align:justify' in (static/'ai-story.css').read_text()
assert len([i for i in p.ids if i in ['ru-alice','ru-foundation','ru-giga','ru-tpro','ru-tsearch','ru-cotype','ru-kandinsky']])==7
print('PASS AI Russia: balanced HTML, unique IDs, all chapter links/assets, 30 cited sources, seven cards, tab and justified text')
