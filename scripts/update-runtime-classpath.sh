#!/usr/bin/env bash
set -euo pipefail

echo "Runtime preparation must use the managed serial SBT execution route." >&2
echo "Request cozyPrepareRuntime for this component through the workflow command runner." >&2
echo "This convenience script does not start SBT or modify runtime artifacts." >&2
exit 1
