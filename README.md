# FalconBench (armeabi-v7a)

Local GGUF lab for **32-bit Android userspace** (including 64-bit CPUs with 32-bit userspace).

## Hard constraints

- **ABI: `armeabi-v7a` only** — arm64 APK will not load on your phone.
- **llama.cpp is PINNED** (`scripts/fetch-llama.sh` → tag **b3500** by default), not `master`.
- Official upstream Android support is **arm64 only**. This is an **unofficial** v7 build path using:
  - Flags from **#5702** (`neon-vfpv4`, softfp, no unaligned access)
  - `GGML_LLAMAFILE=OFF`, `GGML_OPENMP=OFF`, `GGML_NATIVE=OFF`
  - Classic C API (`llama_load_model_from_file`, …) matching the pin era
- **#10890 is NOT armv7 support** — it removed Android armv7 FP16 hacks.

## Build

```bash
bash scripts/fetch-llama.sh          # tries b3500→b3400→b3300→b3200→b3000
# LLAMA_REF=b3200 bash scripts/fetch-llama.sh   # force one
# LLAMA_PINS="b3000 b2800" bash scripts/fetch-llama.sh
# optional: LLAMA_REF=b3400 bash scripts/fetch-llama.sh
gradle wrapper --gradle-version 8.11.1
./gradlew :app:assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk` (armeabi-v7a).

Requires: JDK 17, NDK 27.x, CMake 3.22+.

## If native build fails

1. Try an older pin: `LLAMA_REF=b3200 bash scripts/fetch-llama.sh`
2. Check NDK r26/r27 — r25 had known `vcvtnq` issues with some flags
3. Do not add `+dotprod` or `GGML_LLAMAFILE=ON`

## App features

Lab / Models registry / Session history / Memory notes / Runtime (threads, ctx, mmap, mlock, nice, affinity).

Any GGUF the pinned llama.cpp can load — not Falcon-only.
