#!/usr/bin/env bash
set -uo pipefail
cd "$(dirname "$0")" || exit 1
timeout "${TEST_TIMEOUT:-300}" python3 -m unittest discover -s tests -t . "$@"
