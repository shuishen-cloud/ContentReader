#!/usr/bin/env bash
# ContentManager —— 纯命令行 Android 构建脚本（Termux / Linux / WSL / macOS）
#
# 依赖: aapt2 aidl d8 apksigner javac zip curl unzip
# SDK:  $ANDROID_SDK_ROOT/platforms/android-$API_LEVEL/android.jar
#
# 用法:
#   ./build.sh              debug 构建（自动生成 debug.keystore，产物 *-debug.apk）
#   ./build.sh --release    release 构建（用 release.keystore，产物 ContentManager-<版本>.apk）
#   ./build.sh --setup      自动下载 android.jar 并安装依赖工具
#
# release keystore 可通过环境变量覆盖:
#   RELEASE_KEYSTORE / RELEASE_KS_PASS / RELEASE_KS_ALIAS
# 首次 --release 会自动生成 release.keystore（不入库，请自行备份）。
#
set -euo pipefail

SDK="${ANDROID_SDK_ROOT:-$HOME/android-sdk}"
API="${API_LEVEL:-34}"
PLATFORM="$SDK/platforms/android-$API/android.jar"

# 工具解析：优先用 Android SDK build-tools（Linux/WSL/macOS），
# 找不到再回退到 PATH（Termux 下由 pkg 提供）。
resolve_tools() {
  local bt="$SDK/build-tools/${BUILD_TOOLS:-34.0.0}"
  if [ -x "$bt/aapt2" ]; then
    AAPT2="$bt/aapt2"; D8="$bt/d8"; APKSIGNER="$bt/apksigner"
  else
    AAPT2="$(command -v aapt2 || true)"
    D8="$(command -v d8 || true)"
    APKSIGNER="$(command -v apksigner || true)"
  fi
}
resolve_tools

ROOT="$(cd "$(dirname "$0")" && pwd)"
APP="$ROOT/app/src/main"
OUT="$ROOT/build"
KEYSTORE="$ROOT/debug.keystore"
KS_PASS="android"
KS_ALIAS="androiddebugkey"
# 产物文件名里的版本，与 AndroidManifest.xml 的 versionName 保持一致
VERSION="${VERSION:-1.0.1}"

# ---------------------------------------------------------------- 环境准备
setup_env() {
  if command -v pkg >/dev/null 2>&1; then
    echo "==> 安装构建工具（Termux pkg）"
    pkg install -y aapt2 aidl d8 apksigner zip unzip curl < /dev/null
  else
    echo "==> 非 Termux 环境：改用 Android SDK build-tools"
    local sm="$SDK/cmdline-tools/latest/bin/sdkmanager"
    if [ -x "$sm" ]; then
      yes | "$sm" --sdk_root="$SDK" \
        "build-tools;${BUILD_TOOLS:-34.0.0}" "platforms;android-$API" > /dev/null
      resolve_tools
    else
      echo "    未找到 sdkmanager，请自行准备 build-tools 与 platform"
    fi
  fi

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

RELEASE=0
for a in "$@"; do
  case "$a" in
    --setup) SETUP=1 ;;
    --release) RELEASE=1 ;;
  esac
done
if [ "${SETUP:-0}" = "1" ]; then
  setup_env
  echo
fi

# release 模式下用另一套 keystore 与产物名
if [ "$RELEASE" = "1" ]; then
  KEYSTORE="${RELEASE_KEYSTORE:-$ROOT/release.keystore}"
  KS_PASS="${RELEASE_KS_PASS:-android}"
  KS_ALIAS="${RELEASE_KS_ALIAS:-contentmanager}"
  OUT_APK="$ROOT/ContentManager-$VERSION.apk"
else
  OUT_APK="$ROOT/ContentManager-debug.apk"
fi

# ---------------------------------------------------------------- 前置检查
[ -f "$PLATFORM" ] || {
  echo "❌ 缺少 android.jar: $PLATFORM"
  echo "   运行 ./build.sh --setup 自动准备环境"
  exit 1
}
for t in "$AAPT2" "$D8" "$APKSIGNER"; do
  [ -n "$t" ] && [ -x "$t" ] || {
    echo "❌ 缺少 Android 构建工具（aapt2/d8/apksigner）"
    echo "   运行 ./build.sh --setup，或设置 ANDROID_SDK_ROOT"
    exit 1
  }
done
command -v javac >/dev/null || { echo "❌ 缺少工具: javac (需要 JDK 17+)"; exit 1; }
# zip 非必需：没有 zip 命令时用 python3 的 zipfile 兜底
HAVE_ZIP=1; command -v zip >/dev/null || HAVE_ZIP=0
if [ "$HAVE_ZIP" = 0 ]; then
  command -v python3 >/dev/null || { echo "❌ 缺少 zip 和 python3，至少需要一个"; exit 1; }
fi

rm -rf "$OUT"; mkdir -p "$OUT/res" "$OUT/gen" "$OUT/classes" "$OUT/dex"

# ---------------------------------------------------------------- 1. 资源
echo "==> 1/6 aapt2 compile (资源)"
if [ -d "$APP/res" ] && [ -n "$(ls -A "$APP/res" 2>/dev/null)" ]; then
  "$AAPT2" compile --dir "$APP/res" -o "$OUT/res.zip"
  RES_ARG="$OUT/res.zip"
else
  echo "    无资源，跳过"
  RES_ARG=""
fi

# ---------------------------------------------------------------- 2. 链接
echo "==> 2/6 aapt2 link (生成 R.java + 基础 APK)"
# shellcheck disable=SC2086
"$AAPT2" link \
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
"$D8" --min-api 24 --lib "$PLATFORM" --output "$OUT/dex" \
  $(find "$OUT/classes" -name '*.class')

# ---------------------------------------------------------------- 5. 打包
echo "==> 5/6 打包 APK"
cp "$OUT/base.apk" "$OUT/unsigned.apk"
if [ "$HAVE_ZIP" = 1 ]; then
  (cd "$OUT/dex" && zip -q -j "$OUT/unsigned.apk" classes.dex)
else
  # 用 python zipfile 把 classes.dex 追加进 APK（保持 deflate 压缩）
  python3 - "$OUT/unsigned.apk" "$OUT/dex/classes.dex" <<'PYZIP'
import sys, zipfile, shutil
apk, dex = sys.argv[1], sys.argv[2]
with zipfile.ZipFile(apk, "a", zipfile.ZIP_DEFLATED) as z:
    z.write(dex, "classes.dex")
PYZIP
fi

# ---------------------------------------------------------------- 6. 签名
echo "==> 6/6 签名"
if [ ! -f "$KEYSTORE" ]; then
  if [ "$RELEASE" = "1" ]; then
    echo "    生成 release keystore（首次 --release）"
    echo "    ⚠ 请备份 $KEYSTORE —— 丢失后无法再对已发布的 APK 做覆盖升级"
    DN="CN=ContentManager,O=ContentManager,C=CN"
  else
    echo "    生成 debug keystore（首次构建）"
    DN="CN=Android Debug,O=Android,C=US"
  fi
  keytool -genkeypair -v -keystore "$KEYSTORE" -storepass "$KS_PASS" \
    -keypass "$KS_PASS" -alias "$KS_ALIAS" -keyalg RSA -keysize 2048 \
    -validity 10000 -dname "$DN" >/dev/null 2>&1
fi
"$APKSIGNER" sign --ks "$KEYSTORE" --ks-pass "pass:$KS_PASS" \
  --key-pass "pass:$KS_PASS" --ks-key-alias "$KS_ALIAS" \
  --out "$OUT_APK" "$OUT/unsigned.apk"
"$APKSIGNER" verify --verbose "$OUT_APK" | head -5

echo
echo "✅ 构建完成: $OUT_APK"
ls -lh "$OUT_APK"
