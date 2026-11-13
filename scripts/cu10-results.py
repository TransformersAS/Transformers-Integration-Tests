#!/usr/bin/env python3
"""Resumen efectivo a partir de los reportes originales, sin convertir fallos en éxitos."""
import json, os, pathlib, xml.etree.ElementTree as ET
root=pathlib.Path(os.environ['CU10_RUN_DIR']);summary={}
reports=list(root.glob('http-reports/TEST-*.xml'))
if reports:
    rows=[ET.parse(p).getroot() for p in reports]
    totals={k:sum(int(r.get(k,0)) for r in rows) for k in ['tests','failures','errors','skipped']}
    summary['http']={**totals,'passed':totals['tests']-totals['failures']-totals['errors']-totals['skipped']}
else: summary['http']={'estado':'sin reporte'}
if (root/'e2e-results.json').exists():
    r=json.loads((root/'e2e-results.json').read_text());summary['navegador']=r.get('stats',{})
else: summary['navegador']={'estado':'sin reporte'}
(root/'summary.json').write_text(json.dumps(summary,indent=2,ensure_ascii=False)+'\n')
print(json.dumps(summary,ensure_ascii=False))
