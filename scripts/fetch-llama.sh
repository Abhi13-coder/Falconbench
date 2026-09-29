#!/usr/bin/env bash
# Multi-pin fallback for unofficial Android armeabi-v7a builds.
# Tries each ref until clone+checkout succeeds.
# Override list: LLAMA_PINS="b3500 b3400 b3200" bash scripts/fetch-llama.sh
# Force one:     LLAMA_REF=b3200 bash scripts/fetch-llama.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEST="$ROOT/app/src/main/cpp/third_party/llama.cpp"
PIN_FILE="$ROOT/app/src/main/cpp/third_party/LLAMA_PIN.txt"
REPO="https://github.com/ggml-org/llama.cpp.git"

# Default pins: newer → older within classic C API / pre-#10890 window
# Edit this list if your NDK needs an even older tree.
DEFAULT_PINS=(b3500 b3400 b3300 b3200 b3000)

if [ -n "${LLAMA_REF:-}" ]; then
  PINS=("$LLAMA_REF")
elif [ -n "${LLAMA_PINS:-}" ]; then
  # shellcheck disable=SC2206
  PINS=($LLAMA_PINS)
else
  PINS=("${DEFAULT_PINS[@]}")
fi

mkdir -p "$(dirname "$DEST")"
rm -rf "$DEST"

try_pin() {
  local ref="$1"
  echo "=== trying pin: $ref ==="
  if git clone --depth 1 --branch "$ref" "$REPO" "$DEST" 2>/tmp/falcon-llama-clone.err; then
    echo "OK clone --branch $ref"
    return 0
  fi
  # branch/tag name failed — shallow clone main then fetch tag
  rm -rf "$DEST"
  if ! git clone --depth 1 "$REPO" "$DEST" 2>>/tmp/falcon-llama-clone.err; then
    echo "FAIL base clone"
    return 1
  fi
  if git -C "$DEST" fetch --depth 1 origin "refs/tags/${ref}:refs/tags/${ref}" 2>>/tmp/falcon-llama-clone.err \
     || git -C "$DEST" fetch --depth 1 origin "$ref" 2>>/tmp/falcon-llama-clone.err; then
    if git -C "$DEST" checkout -f "$ref" 2>>/tmp/falcon-llama-clone.err; then
      echo "OK fetch+checkout $ref"
      return 0
    fi
  fi
  echo "FAIL pin $ref"
  cat /tmp/falcon-llama-clone.err 2>/dev/null | tail -5 || true
  rm -rf "$DEST"
  return 1
}

CHOSEN=""
for ref in "${PINS[@]}"; do
  if try_pin "$ref"; then
    CHOSEN="$ref"
    break
  fi
done

if [ -z "$CHOSEN" ]; then
  echo "ERROR: no pin worked. Tried: ${PINS[*]}"
  echo "Set LLAMA_REF=... to a tag that exists, or LLAMA_PINS=\"b2800 b2500\""
  exit 1
fi

SHORT=$(git -C "$DEST" rev-parse --short HEAD)
echo "$CHOSEN" > "$PIN_FILE"
echo "$SHORT  $CHOSEN" > "$ROOT/app/src/main/cpp/third_party/LLAMA_PIN_FULL.txt"
echo "============================================"
echo "Pinned llama.cpp: $CHOSEN ($SHORT)"
echo "Wrote $PIN_FILE"
echo "============================================"
