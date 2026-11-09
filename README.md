# ContentManager

从任意 App（知乎、B站、小红书……）「分享」链接到本 App，集中管理并导出。

纯 Android SDK 实现，**零第三方依赖**，可用 Termux / Linux / WSL 纯命令行构建，不需要 Android Studio / Gradle。

---

## 功能

| 功能 | 说明 |
|---|---|
| 接收分享 | 注册 `ACTION_SEND`（`text/plain` + `*/*`），出现在所有 App 的分享列表 |
| 自动抽链 | 正则从分享文案里提取 URL，支持中文标点截断、去重 |
| 自动标题 | HTTP 抓 `<title>` → 失败则隐藏 WebView 执行 JS 过反爬（**知乎实测可用**） |
| 短链解析 | `b23.tv` / `xhslink.com` / `t.cn` 自动解析成真实 URL |
| 跳转 | 点击条目按来源直接唤起知乎/B站 App（显式 Deep Link） |
| **标签** | 多标签扁平管理，用于检索；可重命名、删除、按标签筛选 |
| **收藏夹** | 用户自建，**顺序可手动上下调整**（顺序即结构），用于收集材料并构建思想 |
| **评分** | 每条 1~5 星，可按评分下限筛选 |
| **评论** | 每条一段笔记，参与搜索 |
| **检索** | 关键词（标题/链接/评论）× 标签 × 评分 × 收藏夹 组合过滤 |
| **颜色** | 条目 / 标签 / 收藏夹均可配色：条目标记色显示为左侧色条，标签色显示在标签文字上 |
| 管理 | 长按：快速打标签 / 加入收藏夹 / 重新抓取 / 删除 |
| 导出 | 系统分享面板输出 Markdown（含评分、标签、评论） |

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

## 在电脑上构建（Linux / macOS / WSL）

当前仓库是**手工构建结构**，没有 `build.gradle`。

### 依赖

```bash
# JDK 17
# Android SDK：build-tools 34.0.0 + platforms;android-34 + platform-tools
sdkmanager "build-tools;34.0.0" "platforms;android-34" "platform-tools"
export ANDROID_SDK_ROOT=$HOME/android-sdk
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk   # 举例
./build.sh
```

`build.sh` 会自动从 `$ANDROID_SDK_ROOT/build-tools/34.0.0` 找 aapt2/d8/apksigner；
找不到才回退 `PATH`（Termux 下用 `pkg` 装的版本）。

没有 `zip` 命令时脚本会用 `python3 zipfile` 兜底，无需额外安装。

### 在 WSL 里跑模拟器

WSL2 需要嵌套虚拟化才有 `/dev/kvm`（否则模拟器慢到不可用）：

```bash
ls -l /dev/kvm          # 存在即可用 KVM 加速
sdkmanager "emulator" "system-images;android-30;default;x86_64"
avdmanager create avd -n test30 -k "system-images;android-30;default;x86_64"
emulator -avd test30 -gpu swiftshader_indirect &
adb install -r ContentManager-debug.apk
```

如果 `/dev/kvm` 不存在，在 Windows 的 `%UserProfile%\.wslconfig` 加
`nestedVirtualization=true` 后 `wsl --shutdown` 重启。

也可以让 Windows 侧的 adb 服务统一管理设备，WSL 里用：
`adb -H <windows-ip> -P 5037 devices`（Windows 侧需先 `adb start-server`）。

---

## 项目结构

```
ContentReader/
├── build.sh                          # 一键构建（含 --setup）
├── .gitignore
├── README.md
└── app/src/main/
    ├── AndroidManifest.xml           # 分享接收、权限、Activity 声明
    └── java/com/termux/contentmanager/
        ├── ShareActivity.java        # 接收分享 → 抽链 → 入库 → 后台抓标题
        ├── MainActivity.java         # 列表 / 搜索 / 筛选 / 跳转 / 导出
        ├── LinkDetailActivity.java   # 详情：评分 / 评论 / 标签 / 收藏夹
        ├── TagManagerActivity.java   # 标签管理：重命名 / 删除 / 按标签查条目
        ├── CollectionActivity.java   # 收藏夹管理：新建 / 排序 / 删除
        ├── CollectionLinksActivity.java # 收藏夹内条目列表（可排序）
        ├── UiKit.java                # 纯代码 UI 工具（按钮/对话框/星级）
        ├── DebugShareActivity.java   # 调试：dump 分享 Intent 全部字段
        ├── LinkParser.java           # URL 提取、来源识别、标题推断
        ├── LinkStore.java            # SQLite（links/tags/link_tags/collections）
        ├── FetchTitle.java           # HTTP 抓 <title>（快速通道）
        ├── WebTitleFetcher.java      # 隐藏 WebView 抓标题（过 JS 反爬）
        └── TitleResolver.java        # 抓取调度：HTTP → WebView 降级
```

## 数据模型

```
links           条目本体
  id, url, title, source, created_at
  comment, rating(0~5)   # 评论、评分
  color                  # 标记色（ARGB），0 = 无颜色

tags            标签（扁平，用于检索）
  id, name (COLLATE NOCASE), color

link_tags       条目 ↔ 标签 多对多
  link_id, tag_id

collections     收藏夹（顺序即结构）
  id, name, position, color     # position 决定展示顺序，用户可 ▲▼

link_collections  条目 ↔ 收藏夹 多对多
  link_id, collection_id, position   # position 决定条目在收藏夹内的顺序
```

数据库版本 v4，带 `onUpgrade` 迁移，旧数据全部保留：

| 版本 | 变更 |
|---|---|
| v2 | 加 `comment` / `rating`；建 `tags` / `link_tags` / `collections` |
| v3 | 单列 `collection_id` → 多对多 `link_collections`（旧数据自动搬迁） |
| v4 | 加 `color`（links / tags / collections）；用 `PRAGMA table_info` 判断后再 ALTER |

## 标签 vs 收藏夹

两者职责不同，刻意分开：

- **标签**：横向的**管理维度**。一条内容可有多个标签，用于快速检索（「所有 #论文」）。
- **收藏夹**：纵向的**组织容器**。条目可**同时属于多个收藏夹**（多对多），且在**每个收藏夹内各有独立顺序** —— 用来把零散材料排成一个论证结构。

所以收藏夹有 `position` 且支持 ▲▼ 手动排序，标签没有顺序。

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
