"""Check the published models guide against its dated source inventory."""
from html.parser import HTMLParser
from pathlib import Path
import json

root=Path(__file__).resolve().parents[1]
static=root/'src/main/resources/static'
metadata=json.loads((root/'docs/ai-models-sources.json').read_text())
class Page(HTMLParser):
    def __init__(self):
        super().__init__();self.ids=[];self.links=[];self.cards={};self.guides=[];self.scripts=[];self.styles=[]
    def handle_starttag(self,tag,attrs):
        a=dict(attrs)
        if 'id' in a:self.ids.append(a['id'])
        if tag=='a':self.links.append(a.get('href',''))
        if 'data-ai-model' in a:self.cards[a['data-ai-model']]=a
        if 'data-ai-profession' in a:self.guides.append(a['data-ai-profession'])
        if tag=='script':self.scripts.append(a['src'])
        if tag=='link' and a.get('rel')=='stylesheet':self.styles.append(a['href'])
p=Page();text=(static/'ai-models.html').read_text();p.feed(text)
assert len(p.ids)==len(set(p.ids))
assert len(p.cards)==10 and len(p.guides)==9
for model in metadata['models']:
    card=p.cards[model['id']]
    assert set(card['data-roles'].split())==set(model['roles'])
    assert model['source'] in p.links
    assert model['name'] in text
    assert card['id']=='model-'+model['id']
for role in metadata['professions']:
    assert role['id'] in p.guides
    assert set(role['models'])=={m['id'] for m in metadata['models'] if role['id'] in m['roles']}
for href in p.links:
    if href.startswith('#'):assert href[1:] in p.ids,href
    elif href.startswith('/') and not href.startswith('/#'):assert (static/href.lstrip('/')).is_file(),href
    else:assert href.startswith(('https://','/#')),href
for asset in p.scripts+p.styles:assert (static/asset.lstrip('/')).is_file(),asset
assert f'datetime="{metadata["checkedAt"]}"' in text
assert '<form id="ai-model-filter" class="ai-model-filter" hidden>' in text
index=Page();index.feed((static/'index.html').read_text())
assert 'ai-tab-models' in index.ids
print('PASS models content: ten sourced systems, nine consistent professions, anchors, local assets, dated snapshot and progressive enhancement')
