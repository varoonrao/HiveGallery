#!/usr/bin/env python3
"""Convert the bundled Feather SVG subset to Android VectorDrawable resources."""
from pathlib import Path
import xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[1]
NS='http://schemas.android.com/apk/res/android'
ET.register_namespace('android',NS)
def attrs(**kw): return {'{'+NS+'}'+k:str(v) for k,v in kw.items()}
def path_for(n):
    a=n.attrib;t=n.tag.split('}')[-1]
    if t=='path':return a['d']
    if t=='line':return f'M{a["x1"]},{a["y1"]} L{a["x2"]},{a["y2"]}'
    if t in ('polyline','polygon'):
        pts=a['points'].replace(',',' ').split();return 'M'+' L'.join(','.join(pts[i:i+2]) for i in range(0,len(pts),2))+(' Z' if t=='polygon' else '')
    if t=='circle':
        x,y,r=map(float,(a['cx'],a['cy'],a['r']));return f'M{x-r},{y} A{r},{r} 0 1,0 {x+r},{y} A{r},{r} 0 1,0 {x-r},{y} Z'
    if t=='rect':
        x,y,w,h=map(float,(a.get('x',0),a.get('y',0),a['width'],a['height']));r=float(a.get('rx',a.get('ry',0)))
        if not r:return f'M{x},{y} H{x+w} V{y+h} H{x} Z'
        return f'M{x+r},{y} H{x+w-r} A{r},{r} 0 0,1 {x+w},{y+r} V{y+h-r} A{r},{r} 0 0,1 {x+w-r},{y+h} H{x+r} A{r},{r} 0 0,1 {x},{y+h-r} V{y+r} A{r},{r} 0 0,1 {x+r},{y} Z'
    raise ValueError(t)
for source in sorted((ROOT/'design/icons').glob('*.svg')):
    svg=ET.parse(source).getroot();root=ET.Element('vector',attrs(width='24dp',height='24dp',viewportWidth=24,viewportHeight=24))
    for n in svg:
        ET.SubElement(root,'path',attrs(pathData=path_for(n),fillColor='#00000000',strokeColor='#FFFFFFFF',strokeWidth='1.8',strokeLineCap='round',strokeLineJoin='round'))
    ET.indent(root);dest=ROOT/'app/src/main/res/drawable'/('ic_'+source.stem.replace('-','_')+'.xml');ET.ElementTree(root).write(dest,encoding='utf-8',xml_declaration=True)
