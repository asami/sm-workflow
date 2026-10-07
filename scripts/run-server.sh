#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
RUNTIME_DIRECTORY="$PROJECT_ROOT/target/cncf.d"

for artifact in runtime-classpath.txt component-descriptor.json car-runtime-manifest.json; do
  if [[ ! -s "$RUNTIME_DIRECTORY/$artifact" ]]; then
    echo "Official runtime preparation is missing: $RUNTIME_DIRECTORY/$artifact" >&2
    echo "Run cozyPrepareRuntime through the configured serial SBT execution route." >&2
    exit 1
  fi
done

exec "$PROJECT_ROOT/bin/launcher" \
  --classpath-file "$RUNTIME_DIRECTORY/runtime-classpath.txt" \
  --main-class org.goldenport.cncf.CncfMain \
  -- \
  --component-dev-dir "$PROJECT_ROOT" \
  "$@" \
  server
