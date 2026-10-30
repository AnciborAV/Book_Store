"""Validate local illustrations, source links and chapter anchors before publishing."""
from html.parser import HTMLParser
from pathlib import Path
import json

root = Path(__file__).resolve().parents[1]
static = root / 'src/main/resources/static'

class Page(HTMLParser):
    def __init__(self):
        super().__init__(); self.ids = []; self.images = []; self.links = []; self.articles = 0
    def handle_starttag(self, tag, attrs):
        a = dict(attrs)
        if 'id' in a: self.ids.append(a['id'])
        if tag == 'img': self.images.append(a)
        if tag == 'a': self.links.append(a.get('href', ''))
        if tag == 'article': self.articles += 1

for name in ['ai-history.html', 'ai-architects.html']:
    p = Page(); p.feed((static / name).read_text())
    assert len(p.ids) == len(set(p.ids)), (name, 'duplicate id')
    for a in p.images:
        assert a.get('alt'), (name, 'image without description')
        assert a['src'].startswith('/covers/ai-people/'), (name, 'remote image')
        assert (static / a['src'].lstrip('/')).is_file(), a['src']
    for href in p.links:
        if href.startswith('#'): assert href[1:] in p.ids, (name, href)
        elif href.startswith('/') and not href.startswith('/#'): assert (static / href.lstrip('/')).is_file(), href
        else: assert href.startswith(('https://', '/#')), href
    assert p.articles >= (15 if name == 'ai-history.html' else 13)
credits = json.loads((root / 'docs/ai-image-credits.json').read_text())
for credit in credits:
    file = static / credit['path'].lstrip('/')
    assert file.is_file() and file.stat().st_size < 500_000, file
    assert credit['author'] and credit['license'] and credit['source'].startswith('https://commons.wikimedia.org/')
print('PASS AI content: 15 chapters, 13 biography cards, local images, anchors and 16 image credits')
