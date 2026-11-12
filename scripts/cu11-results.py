#!/usr/bin/env python3
"""Compact results; raw logs stay under ignored target/, never in versioned documentation."""
import json,xml.etree.ElementTree as ET
from pathlib import Path
root=Path('target/cu11')
http={'executed':0,'passed':0,'failed':0,'errors':0,'skipped':0}
for p in root.glob('http-*/TEST-*.xml'):
    r=ET.parse(p).getroot()
    for key,attribute in [('executed','tests'),('failed','failures'),('errors','errors'),('skipped','skipped')]: http[key]+=int(r.get(attribute,'0'))
http['passed']=http['executed']-http['failed']-http['errors']-http['skipped']
e2e=None
if (root/'e2e.json').exists():
    stats=json.loads((root/'e2e.json').read_text())['stats']
    e2e={'passed':stats['expected'],'failed':stats['unexpected'],'skipped':stats['skipped'],'flaky':stats['flaky']}
results={'http':http,'e2e':e2e,'http_modes_recorded':sorted(p.parent.name for p in root.glob('http-*/exit-code'))}
(root/'results.json').write_text(json.dumps(results,indent=2)+'\n')
print(json.dumps(results))
