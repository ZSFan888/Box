#!/usr/bin/env bash
# place_libs.sh
# 将三大核心的预编译 .so 文件分发到 Android jniLibs 目录
# 用法：bash scripts/place_libs.sh [CORES_DIR]
set -euo pipefail

CORES_DIR="${1:-cores}"
JNI_DIR="app/src/main/jniLibs"
ABIS=("arm64-v8a" "armeabi-v7a" "x86_64")

MIHOMO_VER="${MIHOMO_VERSION:-v1.18.9}"
XRAY_VER="${XRAY_VERSION:-v25.4.30}"
SINGBOX_VER="${SINGBOX_VERSION:-v1.9.7}"

for ABI in "${ABIS[@]}"; do
    mkdir -p "$JNI_DIR/$ABI"
done

echo "📦 Processing cores from: $CORES_DIR"

# ════════════════════════════
# mihomo → libmihomo.so
# ════════════════════════════
MIHOMO_AAR="$CORES_DIR/mihomo/mihomo.aar"
if [ -f "$MIHOMO_AAR" ]; then
    echo "🔧 Extracting mihomo.aar..."
    unzip -o "$MIHOMO_AAR" "jni/*" -d "$CORES_DIR/mihomo/aar_out" 2>/dev/null || true
    for ABI in "${ABIS[@]}"; do
        SO="$CORES_DIR/mihomo/aar_out/jni/$ABI/libmihomo.so"
        [ -f "$SO" ] && cp "$SO" "$JNI_DIR/$ABI/" && echo "  ✓ mihomo $ABI"
    done
else
    # 尝试直接下载 gomobile 编译产物（CMFA build artifacts）
    MIHOMO_URL="https://github.com/MetaCubeX/mihomo/releases/download/${MIHOMO_VER}"
    for ABI_INFO in "arm64-v8a:arm64" "armeabi-v7a:armv7" "x86_64:amd64"; do
        ABI="${ABI_INFO%%:*}"; ARCH="${ABI_INFO##*:}"
        URL="${MIHOMO_URL}/mihomo-android-${ARCH}-${MIHOMO_VER}.gz"
        DST="$JNI_DIR/$ABI/libmihomo.so"
        if [ ! -f "$DST" ]; then
            echo "⬇️  Downloading mihomo $ABI..."
            curl -fsSL "$URL" | gunzip > "$DST" 2>/dev/null                 && echo "  ✓ mihomo $ABI"                 || { echo "  ⚠️  mihomo $ABI download failed (stub will be used)"; rm -f "$DST"; }
        fi
    done
fi

# ════════════════════════════
# xray-core → libv2ray.so
# ════════════════════════════
XRAY_BASE="$CORES_DIR/xray"
for ABI_INFO in "arm64-v8a:arm64-v8a" "armeabi-v7a:armeabi-v7a" "x86_64:x86_64"; do
    ABI="${ABI_INFO%%:*}"; ABI_ARCH="${ABI_INFO##*:}"
    ZIP="$XRAY_BASE/xray-${ABI_ARCH}.zip"
    DST="$JNI_DIR/$ABI/libv2ray.so"
    if [ -f "$ZIP" ]; then
        echo "🔧 Extracting xray $ABI..."
        unzip -p "$ZIP" "*.so" > "$DST" 2>/dev/null             && echo "  ✓ xray $ABI" || rm -f "$DST"
    elif [ ! -f "$DST" ]; then
        URL="https://github.com/2dust/v2rayNG/releases/download/latest/v2rayNG_${ABI}.apk"
        echo "⬇️  Downloading xray from v2rayNG APK ($ABI)…"
        curl -fsSL -L "$URL" -o "/tmp/v2rayng_${ABI}.apk" 2>/dev/null || continue
        unzip -p "/tmp/v2rayng_${ABI}.apk" "lib/$ABI/libv2ray.so" > "$DST" 2>/dev/null             && echo "  ✓ xray $ABI (from v2rayNG APK)" || rm -f "$DST"
    fi
done

# ════════════════════════════
# sing-box → libsingbox.so
# ════════════════════════════
SINGBOX_BASE="$CORES_DIR/singbox"
SINGBOX_URL="https://github.com/SagerNet/sing-box/releases/download/${SINGBOX_VER}"
for ABI_INFO in "arm64-v8a:arm64-v8a" "armeabi-v7a:armeabi-v7a" "x86_64:x86_64"; do
    ABI="${ABI_INFO%%:*}"; ABI_ARCH="${ABI_INFO##*:}"
    TGZ="$SINGBOX_BASE/singbox-${ABI}.tar.gz"
    DST="$JNI_DIR/$ABI/libsingbox.so"
    if [ -f "$TGZ" ]; then
        echo "🔧 Extracting sing-box $ABI..."
        tar -xzf "$TGZ" --wildcards "*.so" -O > "$DST" 2>/dev/null             && echo "  ✓ sing-box $ABI" || rm -f "$DST"
    elif [ ! -f "$DST" ]; then
        URL="${SINGBOX_URL}/sing-box-${SINGBOX_VER#v}-android-${ABI_ARCH}.tar.gz"
        echo "⬇️  Downloading sing-box $ABI…"
        curl -fsSL "$URL" | tar -xz --wildcards "*.so" -O > "$DST" 2>/dev/null             && echo "  ✓ sing-box $ABI" || { echo "  ⚠️  sing-box $ABI failed"; rm -f "$DST"; }
    fi
done

echo ""
echo "═══════════ 结果汇总 ═══════════"
for ABI in "${ABIS[@]}"; do
    echo "[$ABI]"
    for LIB in libmihomo.so libv2ray.so libsingbox.so; do
        F="$JNI_DIR/$ABI/$LIB"
        if [ -f "$F" ]; then
            SIZE=$(du -sh "$F" | cut -f1)
            echo "  ✓ $LIB  ($SIZE)"
        else
            echo "  ✗ $LIB  (missing - JNI calls will stub)"
        fi
    done
done
