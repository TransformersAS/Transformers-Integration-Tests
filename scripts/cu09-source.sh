#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
revision=836c9d72377acc768380288260fc640532f47afd
mkdir -p target/cu09
# La API descarga un archivo inmutable; no se ejecutan operaciones Git.
gh api "repos/TransformersAS/Transformers-AS/commits/$revision" --jq '{sha: .sha, tree: .commit.tree.sha}' > target/cu09/provenance.json
python3 -c 'import json; assert json.load(open("target/cu09/provenance.json"))["sha"] == "836c9d72377acc768380288260fc640532f47afd"'
gh api "repos/TransformersAS/Transformers-AS/tarball/$revision" > target/cu09/source.tar.gz
# Solo se reemplaza la copia desechable creada por esta suite.
python3 -c 'import shutil; shutil.rmtree("target/cu09/app", ignore_errors=True)'
mkdir -p target/cu09/app
tar -xzf target/cu09/source.tar.gz -C target/cu09/app --strip-components=1
shasum -a 256 target/cu09/source.tar.gz > target/cu09/archive.sha256
