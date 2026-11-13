#!/usr/bin/env bash
set -euo pipefail
: "${CU10_RUN_DIR:?}"
revision=3c12266316c86aa97097f0317507dd935af99286
mkdir -p "$CU10_RUN_DIR/app"
gh api "repos/TransformersAS/Transformers-AS/commits/$revision" --jq '{sha: .sha, tree: .commit.tree.sha}' > "$CU10_RUN_DIR/provenance.json"
python3 -c 'import json,sys; assert json.load(open(sys.argv[1]))["sha"] == "3c12266316c86aa97097f0317507dd935af99286"' "$CU10_RUN_DIR/provenance.json"
gh api "repos/TransformersAS/Transformers-AS/tarball/$revision" > "$CU10_RUN_DIR/source.tar.gz"
shasum -a 256 "$CU10_RUN_DIR/source.tar.gz" > "$CU10_RUN_DIR/archive.sha256"
python3 - "$CU10_RUN_DIR/source.tar.gz" <<'PYCODE'
import sys,tarfile,pathlib
with tarfile.open(sys.argv[1]) as archive:
    names=archive.getnames()
    assert names[0].split('/')[0].endswith('3c12266')
    assert all(not pathlib.PurePosixPath(n).is_absolute() and '..' not in pathlib.PurePosixPath(n).parts for n in names)
PYCODE
tar -xzf "$CU10_RUN_DIR/source.tar.gz" -C "$CU10_RUN_DIR/app" --strip-components=1
