#!/data/data/com.termux/files/usr/bin/bash
# ContentManager —— Termux 纯命令行 Android 构建脚本
#
# 依赖: aapt2 aidl d8 apksigner javac zip curl unzip
# SDK:  $ANDROID_SDK_ROOT/platforms/android-$API_LEVEL/android.jar
#
# 用法:
#   ./build.sh              正常构建
#   ./build.sh --setup      自动下载 android.jar 并安装依赖工具
#
set -euo pipefail

SDK="${ANDROID_SDK_ROOT:-$HOME/android-sdk}"
API="${API_LEVEL:-34}"
PLATFORM="$SDK/platforms/android-$API/android.jar"

ROOT="$(cd "$(dirname "$0")" && pwd)"
APP="$ROOT/app/src/main"
OUT="$ROOT/build"
KEYSTORE="$ROOT/debug.keystore"
KS_PASS="android"
KS_ALIAS="androiddebugkey"

# ---------------------------------------------------------------- 环境准备
setup_env() {
  echo "==> 安装构建工具（apt）"
  pkg install -y aapt2 aidl d8 apksigner zip unzip curl < /dev/null

  if [ -f "$PLATFORM" ]; then
    echo "==> android.jar 已存在，跳过"
    return
  fi

  echo "==> 下载 Android $API 平台包（约 61MB）"
  mkdir -p "$SDK/platforms/android-$API"
  mkdir -p "$SDK/.tmp"

  # 官方仓库清单里查真实文件名
  local repo="$SDK/.tmp/repo.xml"
  echo "    获取仓库清单..."
  curl -s --max-time 120 \
    https://dl.google.com/android/repository/repository2-3.xml -o "$repo"

  local zipname
  zipname=$(python3 - "$repo" "$API" <<'PY'
import sys, xml.etree.ElementTree as ET
repo, api = sys.argv[1], sys.argv[2]
root = ET.parse(repo).getroot()
want = "platforms;android-%s" % api
best = None
for p in root.iter():
    if p.tag.endswith("remotePackage") and p.get("path") == want:
        for a in p.iter():
            if a.tag.endswith("archive"):
                for c in a.iter():
                    if c.tag.endswith("url"):
                        best = c.text
print(best or "")
PY
)
  [ -n "$zipname" ] || { echo "❌ 找不到 android-$API 平台包"; exit 1; }
  echo "    平台包: $zipname"

  curl -# -L --max-time 1800 \
    -o "$SDK/.tmp/platform.zip" \
    "https://dl.google.com/android/repository/$zipname"

  echo "==> 解压"
  unzip -q -o "$SDK/.tmp/platform.zip" -d "$SDK/.tmp/platform"
  cp -r "$SDK/.tmp/platform/android-$API/." "$SDK/platforms/android-$API/"
  rm -rf "$SDK/.tmp"

  [ -f "$PLATFORM" ] || { echo "❌ android.jar 解压失败"; exit 1; }
  echo "==> android.jar 就绪: $PLATFORM"
}

if [ "${1:-}" = "--setup" ]; then
  setup_env
  echo
fi

# ---------------------------------------------------------------- 前置检查
[ -f "$PLATFORM" ] || {
  echo "❌ 缺少 android.jar: $PLATFORM"
  echo "   运行 ./build.sh --setup 自动准备环境"
  exit 1
}
for t in aapt2 d8 apksigner javac zip; do
  command -v "$t" >/dev/null || {
    echo "❌ 缺少工具: $t"
    echo "   运行 ./build.sh --setup 自动安装"
    exit 1
  }
done

rm -rf "$OUT"; mkdir -p "$OUT/res" "$OUT/gen" "$OUT/classes" "$OUT/dex"

# ---------------------------------------------------------------- 1. 资源
echo "==> 1/6 aapt2 compile (资源)"
if [ -d "$APP/res" ] && [ -n "$(ls -A "$APP/res" 2>/dev/null)" ]; then
  aapt2 compile --dir "$APP/res" -o "$OUT/res.zip"
  RES_ARG="$OUT/res.zip"
else
  echo "    无资源，跳过"
  RES_ARG=""
fi

# ---------------------------------------------------------------- 2. 链接
echo "==> 2/6 aapt2 link (生成 R.java + 基础 APK)"
# shellcheck disable=SC2086
aapt2 link \
  -I "$PLATFORM" \
  --manifest "$APP/AndroidManifest.xml" \
  --java "$OUT/gen" \
  --min-sdk-version 24 \
  --target-sdk-version "$API" \
  -o "$OUT/base.apk" \
  $RES_ARG

# ---------------------------------------------------------------- 3. javac
echo "==> 3/6 javac (编译 Java)"
find "$APP/java" "$OUT/gen" -name '*.java' > "$OUT/sources.txt"
javac -source 11 -target 11 -encoding UTF-8 \
  -classpath "$PLATFORM" \
  -d "$OUT/classes" @"$OUT/sources.txt" || { echo "❌ javac 编译失败"; exit 1; }

# ---------------------------------------------------------------- 4. dex
echo "==> 4/6 d8 (转 dex)"
# shellcheck disable=SC2046
d8 --min-api 24 --lib "$PLATFORM" --output "$OUT/dex" \
  $(find "$OUT/classes" -name '*.class')

# ---------------------------------------------------------------- 5. 打包
echo "==> 5/6 打包 APK"
cp "$OUT/base.apk" "$OUT/unsigned.apk"
(cd "$OUT/dex" && zip -q -j "$OUT/unsigned.apk" classes.dex)

# ---------------------------------------------------------------- 6. 签名
echo "==> 6/6 签名"
if [ ! -f "$KEYSTORE" ]; then
  echo "    生成 debug keystore（首次构建）"
  keytool -genkeypair -v -keystore "$KEYSTORE" -storepass "$KS_PASS" \
    -keypass "$KS_PASS" -alias "$KS_ALIAS" -keyalg RSA -keysize 2048 \
    -validity 10000 -dname "CN=Android Debug,O=Android,C=US" >/dev/null 2>&1
fi
apksigner sign --ks "$KEYSTORE" --ks-pass "pass:$KS_PASS" \
  --key-pass "pass:$KS_PASS" --ks-key-alias "$KS_ALIAS" \
  --out "$ROOT/ContentManager-debug.apk" "$OUT/unsigned.apk"
apksigner verify --verbose "$ROOT/ContentManager-debug.apk" | head -5

echo
echo "✅ 构建完成: $ROOT/ContentManager-debug.apk"
ls -lh "$ROOT/ContentManager-debug.apk"
