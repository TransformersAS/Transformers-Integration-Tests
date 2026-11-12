#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p target/cu09
bash -n scripts/cu09-source.sh scripts/cu09-compose.sh scripts/cu09-run.sh scripts/cu09-validate.sh
python3 -c 'import ast,pathlib; ast.parse(pathlib.Path("scripts/cu09-fixture.py").read_text())'
node target/cu09/tools/node_modules/typescript/bin/tsc --noEmit --strict --target ES2022 --module commonjs --moduleResolution node --typeRoots target/cu09/tools/node_modules/@types tests/e2e/cu09-profile.spec.ts playwright.cu09.config.ts
node <<'JS'
const fs = require('node:fs');
const assert = require('node:assert/strict');
const YAML = require('./target/cu09/tools/node_modules/yaml');
for (const path of ['scripts/cu09-environment.yaml', '.github/workflows/cu09-external.yml']) YAML.parse(fs.readFileSync(path, 'utf8'), { uniqueKeys: true });
const workflow = YAML.parse(fs.readFileSync('.github/workflows/cu09-external.yml', 'utf8'));
assert.deepEqual(workflow.on.push.branches, ['feature/cu09-integration-tests']);
assert.deepEqual(Object.keys(workflow.on).sort(), ['push', 'workflow_dispatch']);
assert(fs.readFileSync('scripts/cu09-source.sh', 'utf8').includes('revision=836c9d72377acc768380288260fc640532f47afd'));
console.log('TypeScript, Python, Bash, YAML, disparadores y revisión fijada: correctos');
JS
target/cu09/bin/actionlint .github/workflows/cu09-external.yml
printf 'Actionlint: correcto\n'
