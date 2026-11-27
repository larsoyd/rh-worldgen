#!/usr/bin/env bash
# usage: provenance.sh <repo>, ignore rules are not shared with clones so untracked files never count
set -uo pipefail
repo=${1:?repo}
rev=$(git -C "$repo" rev-parse --verify --short HEAD 2>/dev/null) || { echo "refused: no git revision at $repo"; exit 2; }
changes=$(git -C "$repo" status --porcelain --untracked-files=no -- fabric engine mechanics 2>/dev/null) || { echo "refused: git status failed at $repo"; exit 2; }
[ -z "$changes" ] && dirty=no || dirty=yes
echo "rev=$rev dirty=$dirty"
