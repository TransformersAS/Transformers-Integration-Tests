#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
validation="$PWD/target/cu10/validation/$(date -u +%Y%m%d%H%M%S)-$$"
mkdir -p "$validation"
# Estos archivos deben publicarse junto con el workflow; node_modules se instala mediante npm ci.
for required in tools/cu10/package.json tools/cu10/package-lock.json tools/cu10/tsconfig.json; do
  if test ! -f "$required"; then
    printf 'Falta %s: incluye las herramientas CU10 en la revisión publicada.\n' "$required" >&2
    exit 1
  fi
done
for script in scripts/cu10-*.sh; do bash -n "$script"; done
python3 - <<'PY'
import ast, pathlib, xml.etree.ElementTree as ET
for p in pathlib.Path('scripts').glob('cu10-*.py'): ast.parse(p.read_text())
root=ET.parse('backend-tests/cu10/pom.xml').getroot()
ns={'m':'http://maven.apache.org/POM/4.0.0'}
assert root.find('.//m:testSourceDirectory',ns) is None
assert not list(pathlib.Path('src/test/java').rglob('*cu10*'))
assert pathlib.Path('tests/cu10-e2e').resolve().parent != pathlib.Path('tests/e2e').resolve()
PY
CU10_RUN_DIR="$validation" ./mvnw -B -f backend-tests/cu10/pom.xml test-compile > "$validation/compile.log" 2>&1
npx playwright test --config=playwright.cu10.config.ts --list > "$validation/cu10-discovery.log"
npx playwright test --list > "$validation/general-discovery.log"
if rg -i 'cu10|pedido: producto seleccionado|producto: conversación entre' "$validation/general-discovery.log"; then
  printf 'El runner general está descubriendo pruebas CU10.\n' >&2
  exit 1
fi
# La compilación del POM general se evita: solo se inspecciona su directorio convencional.
test "$(find backend-tests/cu10/src/test/java -name '*Test.java' | wc -l | tr -d ' ')" = 1
printf '%s\n' 'Aislamiento estático y descubrimiento de navegador comprobados.' > "$validation/isolation.log"
node tools/cu10/node_modules/typescript/bin/tsc -p tools/cu10/tsconfig.json > "$validation/typescript.log" 2>&1
node - <<'JS' > "$validation/yaml.log"
const fs=require('fs');const YAML=require('./tools/cu10/node_modules/yaml');
for(const p of ['scripts/cu10-environment.yaml','.github/workflows/cu10-external.yml']) {
 const doc=YAML.parseDocument(fs.readFileSync(p,'utf8'),{uniqueKeys:true});
 if(doc.errors.length) throw Error(String(doc.errors)); console.log(p+': válido');
}
JS
