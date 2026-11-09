# ContentManager

从任意 App（知乎、B站、小红书……）「分享」链接到本 App，集中管理并导出。

纯 Android SDK 实现，**零第三方依赖**，可用 Termux 纯命令行构建，不需要 Android Studio / Gradle。

---

## 功能

| 功能 | 说明 |
|---|---|
| 接收分享 | 注册 `ACTION_SEND`（`text/plain` + `*/*`），出现在所有 App 的分享列表 |
| 自动抽链 | 正则从分享文案里提取 URL，支持中文标点截断、去重 |
| 自动标题 | HTTP 抓 `<title>` → 失败则隐藏 WebView 执行 JS 过反爬（**知乎实测可用**） |
| 短链解析 | `b23.tv` / `xhslink.com` / `t.cn` 自动解析成真实 URL |
| 跳转 | 点击条目按来源直接唤起知乎/B站 App（显式 Deep Link） |
| 管理 | 长按：打开/复制/改标题/重新抓取/删除 |
| 导出 | 系统分享面板输出 Markdown 列表 |

---

## 在 Termux 上构建

```bash
# 1. 克隆
git clone git@github.com:shuishen-cloud/ContentReader.git
cd ContentReader

# 2. 一键准备环境（装 aapt2/d8/apksigner + 下载 android.jar 61MB）
./build.sh --setup

# 3. 构建
./build.sh
```

产物：`ContentManager-debug.apk`，拷到手机点击安装即可。

### 手动准备环境（如果 `--setup` 失败）

```bash
pkg install aapt2 aidl d8 apksigner zip unzip curl

# 下载 android.jar（API 34）
mkdir -p ~/android-sdk/platforms/android-34
curl -L -o /tmp/p.zip \
  https://dl.google.com/android/repository/platform-34-ext7_r03.zip
unzip -q -o /tmp/p.zip -d /tmp/p
cp /tmp/p/android-34/android.jar ~/android-sdk/platforms/android-34/
```

如 Android SDK 在别处，设置环境变量：

```bash
export ANDROID_SDK_ROOT=/path/to/sdk
export API_LEVEL=34
./build.sh
```

---

## 在电脑上构建（Android Studio / Gradle）

当前仓库是**手工构建结构**，没有 `build.gradle`。要在电脑上用：

- **方案一**：装好 JDK + Android SDK 后照样跑 `./build.sh`（Linux/macOS/WSL 均可，改掉首行 shebang 即可）。
- **方案二**：自己加 Gradle 外壳。`app/src/main/` 是标准 AGP 目录结构，补一个 `build.gradle` 和 `settings.gradle` 就能直接用。

---

## 项目结构

```
ContentReader/
├── build.sh                          # Termux 一键构建（含 --setup）
├── .gitignore
├── README.md
└── app/src/main/
    ├── AndroidManifest.xml           # 分享接收、权限、Activity 声明
    └── java/com/termux/contentmanager/
        ├── ShareActivity.java        # 接收分享 → 抽链 → 入库 → 后台抓标题
        ├── MainActivity.java         # 列表 / 跳转 / 编辑 / 导出
        ├── DebugShareActivity.java   # 调试：dump 分享 Intent 全部字段
        ├── LinkParser.java           # URL 提取、来源识别、标题推断
        ├── LinkStore.java            # SQLite（单表 links，url 唯一索引）
        ├── FetchTitle.java           # HTTP 抓 <title>（快速通道）
        ├── WebTitleFetcher.java      # 隐藏 WebView 抓标题（过 JS 反爬）
        └── TitleResolver.java        # 抓取调度：HTTP → WebView 降级
```

---

## 技术要点

### 为什么必须用 WebView 抓知乎标题

知乎对无 Cookie 的 HTTP 请求一律返回 403 + JS 挑战页（`zh-zse-ck`）：

```
普通 UA / 完整浏览器头 / iPhone UA / 官方 API / 第三方代理 → 全部 403
```

实测知乎 App 分享时**只给裸链接**（`EXTRA_TEXT`），`EXTRA_SUBJECT` 为空字符串，
`EXTRA_TITLE`、`EXTRA_HTML_TEXT` 均不存在。

所以唯一可行方案是让 WebView 真实执行 JS 通过挑战，再读 `document.title`。

### 抓取降级链

```
HttpURLConnection 抓 <title>        → 快（<1s），B站等普通站点
   ↓ 失败
隐藏 WebView 加载 + 执行 JS         → 慢（3~8s），过知乎反爬
   ↓ 失败
长按「修改标题」手动补              → 100% 兜底
```

### 跳转到对应 App

用显式 Deep Link，不依赖 App Links 验证：

```java
Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
i.setPackage("com.zhihu.android");   // 或 tv.danmaku.bili
startActivity(i);                     // 失败自动回退系统浏览器
```

---

## 调试分享内容

`DebugShareActivity` 能 dump 任意 App 分享时传入的**完整 Intent 字段**
（action / type / ClipData / 全部 extras 的类型与值），支持一键复制。

**默认不启用**：它已从分享列表移除（`exported="false"`，无 `intent-filter`），
避免分享列表里多一个选项。排查具体 App 分享格式时，在
`AndroidManifest.xml` 里恢复注释中的 `intent-filter` 重新构建即可。

实测结果参考——知乎 App 分享时只给裸链接：

```
EXTRA_TEXT      = https://zhuanlan.zhihu.com/p/xxx
EXTRA_SUBJECT   = ""        (空字符串)
EXTRA_TITLE     = 不存在
EXTRA_HTML_TEXT = 不存在
ClipData.html   = null
```

---

## 签名说明

`debug.keystore` 在 `.gitignore` 里，**不入库**。

每台开发机首次 `./build.sh` 会自动生成自己的 debug keystore。
因此**不同机器构建的 APK 签名不同，无法覆盖安装**，需要先卸载旧版。

---

## 已知限制

- 知乎标题依赖 WebView，首次抓取需 3~8 秒；知乎改版可能需要调整 `looksLikeChallenge()`
- 导出走系统分享面板，不是直接写文件（好处：无需存储权限）
- `minSdk 24`（Android 7.0），`targetSdk 34`
