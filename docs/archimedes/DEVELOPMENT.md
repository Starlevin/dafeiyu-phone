# 小爱老师大肥鱼 · 开发文档

更新时间：2026-11-20  
适用范围：`dafeiyu-phone` 在小爱老师 `Archimedes` 上的 32 位适配、Eye 2.0、内置 TTS 及后续开发。  
文档位置：`E:\个人\大肥鱼\小爱老师大肥鱼\开发文档.md`

> 先说清楚：本目录是**文档、规划和交接中心**，不是源码副本。真正改代码请去本文“代码工作区”一节指定的目录，避免多份源码并行修改。

---

## 1. 项目定位

`dafeiyu-phone` 是一个完全跑在 Android 手机里的陪伴 Agent：

- AutoX.js 负责手机操作、自主生活、任务队列和远程桥。
- `DafeiyuChat` 是聊天窗口，通过 ntfy 远程收发消息。
- `DafeiyuBoot` 负责开机自启。
- `DafeiyuEye` 是 2.0 的“眼睛 / 耳朵 / 嘴”：Camera2 看画面、sherpa-onnx Paraformer 做离线 ASR、TTS 说话。

本机不是普通手机，而是一台小米小爱老师 `Archimedes`：

| 项 | 值 |
|---|---|
| 系统 | Android 9 / SDK 28 |
| ABI | 纯 32 位 `armeabi-v7a`，`abilist64` 为空 |
| 屏幕 | 480×800 |
| RAM | 3 GB |
| Root | Magisk，已可用 `su` |
| 用途 | 专用陪伴设备，不登录主微信/支付/唯一 2FA |

因此本项目的核心开发约束不是“功能越多越好”，而是：**所有原生库必须有 32 位版本，所有功能必须在 3 GB / 480×800 的设备上能跑。**

---

## 2. 代码工作区与目录

### 2.1 三个位置，不要混

| 位置 | 用途 | 当前状态 |
|---|---|---|
| `E:\个人\大肥鱼` | 个人大肥鱼项目总入口 | 统一项目根，见 `README_大肥鱼统一入口.md` |
| `E:\个人\大肥鱼\开发工作区\2026-11-20-dafeiyu-phone` | **E 盘正式代码工作区**（Git `main`） | 目前是上游基线；只有 `app/DafeiyuEye/build.ps1` 有本地修改，**还没有内置 TTS 代码** |
| `C:\Users\gosso\Doubao\chats\2026-11-20\dafeiyu-phone` | 本次内置 TTS 的临时工作副本 | 有未提交的 Eye/TTS 改动，126 MB APK 在这里构建成功并安装 |

**重要**：本次 TTS 实装是在 C 盘临时工作副本完成的；E 盘正式工作区尚未合并这些改动。后续第一件工程任务就是**把 C 盘改动合入 E 盘工作区，确认构建和安装，再以 E 盘为唯一开发源**。在合并前不要同时修改两份源码。

当前可运行 APK 已备份到：

```text
E:\个人\大肥鱼\小爱老师大肥鱼\产物\DafeiyuEye-2.0-内置TTS.apk
126,052,474 B
SHA256 9317199A27986EAD38F17A0B1771239719B319932CE497D54BFBF3542A951F73
```

### 2.2 各目录职责

```text
E:\个人\大肥鱼\开发工作区\2026-11-20-dafeiyu-phone\
├─ app\
│  ├─ DafeiyuChat\          聊天 App（WebView + ntfy bridge）
│  ├─ DafeiyuBoot\          开机自启
│  └─ DafeiyuEye\           眼睛/耳朵/嘴，Camera2 + ASR + TTS
├─ autox\
│  ├─ agent.js              自主生活主循环、任务队列、记忆、远程桥
│  ├─ chat.js               Chat App 配套脚本
│  └─ setup_boot.js         AutoX 自带开机任务（本机已关闭）
├─ docs\                    上游架构、安装、Eye、TTS、视觉等文档
├─ deploy-archimedes\       本机部署与交接材料
├─ tools\                   Eye 依赖拉取、打包辅助
├─ scripts\                 推送/安装脚本
└─ dist\                    发布产物
```

### 2.3 手机上的关键路径

```text
/sdcard/PhoneAgent/
├─ agent.js                 主脚本
├─ config.local.json        网关配置（含 Pi proxy token，勿公开）
├─ events.log                agent 运行日志
└─ boot_setup.txt           AutoX 开机任务状态

/sdcard/DeepSeekGirl/
├─ remote/topic.txt         ntfy topic（topic 即密码）
├─ memory/                  事实、自述、对话、每日回顾
├─ vision/observations.jsonl Eye 观察
├─ vision/frames/           临时选帧 JPEG（识别后删除）
├─ eye_config.json          Eye 配置
└─ tts/                     TTS 模型镜像（6 个文件，勿删）

/data/user/0/com.deepseek.dafeiyu.eye/files/tts/
                             内置 TTS 首次运行后的私有模型副本
```

---

## 3. 运行架构

```text
┌────────────────────── 小爱老师 Android 9 ──────────────────────┐
│                                                                 │
│  AutoX.js 6.6.8                                                │
│   └─ agent.js                                                  │
│       ├─ observe()        读屏幕节点 / OCR                      │
│       ├─ selfThink()      10–20 分钟自主活动                     │
│       ├─ memory/          事实、自述、日记、每日回顾              │
│       └─ remote bridge    轮询 ntfy                              │
│                                                                 │
│  DafeiyuChat   ── WebView + ntfy publish/poll                   │
│  DafeiyuBoot   ── BOOT_COMPLETED → BootService → AutoX 快捷启动  │
│  DafeiyuEye    ── Camera2 / Paraformer ASR / Piper TTS          │
│                                                                 │
└───────────────┬─────────────────────────────────────────────────┘
                │ HTTP(S)
      ┌─────────▼─────────┐         ┌────────────────────┐
      │ Pi 8318 反向代理   │────────▶│ Pi 8317 Gateway     │
      │ 192.169.3.200     │         │ 127.0.0.1:8317      │
      └───────────────────┘         └────────────────────┘
                │
      ┌─────────▼─────────┐
      │ ntfy.envs.net     │
      │ topic（即密码）     │
      └───────────────────┘
```

### 3.1 Eye 内部链路

```text
Camera2 取景
→ 64×36 HSV 变化检测 / 切点 / 积分 / 静默兜底 / 去重
→ 选中帧 JPEG
→ 视觉模型生成一句观察
→ /sdcard/DeepSeekGirl/vision/observations.jsonl
→ agent 的 syncVisionMemory() 精炼、去重后写入 memory/vision.md
```

Eye 不直接写 `memory/`。记忆仍由 agent 统一写入，这是 2.0 的隐私边界。

### 3.2 语音链路

```text
EyeService / MainActivity
→ TtsFactory.create()
   ├─ deepseek 模式：DeepSeekTtsProvider（需要视觉配置）
   ├─ http 模式：HttpTtsProvider（同步返回音频）
   └─ 其他/默认：Speaker（内置 Piper）
→ Speaker 复制模型到 files/tts
→ OfflineTts.generate(text)
→ GeneratedAudio.save(cache/feiyu-tts-*.wav)
→ MediaPlayer(USAGE_ASSISTANT) 播放
```

> 当前本机配置是 `ttsMode:"system"`，但 `TtsFactory` 已把 `system` 映射到内置 `Speaker`。这台机器没有系统 TTS，也不需要装第三方引擎。

---

## 4. 目标设备与硬限制

| 限制 | 影响 | 处理 |
|---|---|---|
| 纯 32 位 ABI | 官方 Eye 只打 `arm64-v8a`，直接装会 `UnsatisfiedLinkError` | 自构筑 `armeabi-v7a`，同时打两个 ABI；`fetch_eye_deps.ps1` 拷贝 32 位 `.so` |
| Android 9 / SDK 28 | 新 API、部分权限模型不适用 | 保持 `targetSdk` 兼容；用 `READ/WRITE_EXTERNAL_STORAGE` |
| 3 GB RAM | 81.8 MB ASR + 18.6 MB TTS 同时加载压力大 | Eye 手动打开；ASR/TTS 按需初始化；测试后停 EyeService |
| 480×800 | Eye 表单很长，按钮在屏幕下方 | 测试时滚动到按钮；`uiautomator` 的 bounds 会超出屏幕，以实际截图核对 |
| CPU 弱 | TTS 首次加载约 17 秒，每句约 16 秒 | 播报文本压到 50 字内；不把延迟误判为卡死 |
| 无系统 TTS | 原版 Eye 无法出声 | 内置 Piper 小雅，不依赖系统 TTS |

---

## 5. 版本策略

| 组件 | 版本 | 说明 |
|---|---|---|
| AutoX.js | 6.6.8 / versionCode 668 | 独立安装；必须保留 armv7 运行能力 |
| DafeiyuChat | 1.1.0 | 纯 Java，无 ABI 限制 |
| DafeiyuBoot | 1.0.0 | 纯 Java，开机自启 |
| DafeiyuEye | 2.0 自构筑 | 无 `versionName`；`primaryCpuAbi=armeabi-v7a`；APK 126,052,474 B |

为什么主链路选 **1.1.0**：官方 2.0 Eye 的原生库只有 arm64，而本机 `abilist64` 为空；1.1.0 的 AutoX/Chat/Boot 全部能在 32 位跑。2.0 Eye 通过自构筑补上，不需要替换主链路。

---

## 6. DafeiyuEye 构建

### 6.1 依赖

- JDK 17：`C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot`
- Android SDK：`platforms\android-34\android.jar`、`build-tools` 里的 `aapt2`、`d8`、`zipalign`、`apksigner`
- sherpa-onnx Android classes / 原生 `.so`（`libs/`）
- Paraformer ASR 模型（`res/raw/asr_model.onnx`、`asr_tokens.txt`）
- Piper 小雅 TTS 模型（`assets/tts/`）

### 6.2 构建步骤

在代码工作区执行：

```powershell
$env:JAVA_HOME = 'C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot'
Set-Location 'E:\个人\大肥鱼\开发工作区\2026-11-20-dafeiyu-phone\app\DafeiyuEye'
.\build.ps1
```

`build.ps1` 的实际流程：

1. `aapt2 compile` 编译 `res/`
2. `aapt2 link` 链接资源与 `assets/`
3. `javac` 编译 `src/` 与生成的 `R.java`
4. `d8` 生成 `classes.dex`
5. 把 `classes.dex` 和 32/64 位原生库放入 APK
6. `zipalign -p 4`
7. `apksigner` 签名（v2/v3）

产物：

```text
app\DafeiyuEye\dist\DafeiyuEye.apk
```

### 6.3 本机特有的构建修改

- `tools/fetch_eye_deps.ps1`：同时拷贝 `arm64-v8a` 和 `armeabi-v7a`。
- `build.ps1`：优先读 `JAVA_HOME`；双 ABI 打包。
- `AndroidManifest.xml`：`usesCleartextTraffic=true`；补 `READ/WRITE_EXTERNAL_STORAGE`。
- `res/values/styles.xml`：补 `AppTheme`，否则 `aapt2` 报 `style/AppTheme not found`。
- `VisionClient.java` / `ChatClient.java`：base URL 补 `/v1`；`glm-5` 加 `reasoning_effort=minimal`；Chat `max_tokens` 120。
- `Speaker.java`、`MainActivity.java`、`TtsFactory.java`：内置 TTS。

> 注意：E 盘正式工作区目前还没有上述 TTS 改动。重新构建前先完成第 12 节的“合并 TTS 改动”。

---

## 7. 内置 TTS（重点）

### 7.1 为什么自己装

这台小爱老师没有可用的系统 TTS 引擎：logcat 里原版会报 `TTS engine=null` / `TTS init failed: -1`。原版 Eye 的“嘴”因此不可用。

选择的方案不是 Google TTS / eSpeak / 第三方在线 TTS，而是**内置 sherpa-onnx + Piper 中文小雅**：

- 完全离线，不需要额外 APK；
- 模型随 APK 打包，也可以从 sdcard 兜底；
- `MediaPlayer` 播放，不依赖系统 TTS；
- `ttsMode:"system"` 继续可用，因为 `TtsFactory` 已把它映射到内置 `Speaker`。

### 7.2 模型

| 文件 | 大小（字节） |
|---|---:|
| `model.onnx` | 18,652,795 |
| `lexicon.txt` | 2,064,505 |
| `tokens.txt` | 485 |
| `date.fst` | 59,154 |
| `number.fst` | 64,482 |
| `phone.fst` | 88,630 |

- 模型名：`zh_CN-xiao_ya-medium-int8`
- 采样率：22050 Hz
- 单女声
- 数据集：BZNSYP，**非商用许可**；个人自用可以，商用/分发需换模型或确认授权

模型有两个位置：

1. APK 内：`app/DafeiyuEye/assets/tts/`（打包用）
2. 设备镜像：`/sdcard/DeepSeekGirl/tts/`（冷启动兜底，**不要删**）

首次运行会复制到：

```text
/data/user/0/com.deepseek.dafeiyu.eye/files/tts/
```

### 7.3 代码调用链

```text
MainActivity
  └─ “试听大肥鱼声音” / “带她看看”
      └─ Speaker（懒加载）
EyeService
  └─ onCreate → TtsFactory.create(...)
      └─ Speaker
```

`TtsFactory` 当前逻辑：

```java
String mode = cfg == null ? "system" : cfg.ttsMode;
if ("deepseek".equalsIgnoreCase(mode) && cfg.hasVision()) {
    return new DeepSeekTtsProvider(ctx, cfg);
}
if ("http".equalsIgnoreCase(mode) && cfg.ttsUrl != null && !cfg.ttsUrl.trim().isEmpty()) {
    return new HttpTtsProvider(ctx, cfg.ttsUrl, cfg.ttsKey, cfg.ttsVoice, cfg.ttsStyle);
}
return new Speaker(ctx);
```

### 7.4 Speaker 初始化

核心配置：

```java
OfflineTtsVitsModelConfig vits = new OfflineTtsVitsModelConfig();
vits.setModel(model.getAbsolutePath());
vits.setTokens(tokens.getAbsolutePath());
vits.setLexicon(lexicon.getAbsolutePath());
vits.setNoiseScale(0.667f);
vits.setNoiseScaleW(0.8f);
vits.setLengthScale(1.0f);

OfflineTtsModelConfig modelConfig = new OfflineTtsModelConfig();
modelConfig.setVits(vits);
modelConfig.setNumThreads(2);
modelConfig.setDebug(false);
modelConfig.setProvider("cpu");

OfflineTtsConfig config = new OfflineTtsConfig();
config.setModel(modelConfig);
config.setRuleFsts(dateFst.getAbsolutePath() + "," +
        numberFst.getAbsolutePath() + "," + phoneFst.getAbsolutePath());
config.setMaxNumSentences(1);

// 关键：加载绝对路径文件时不能传 AssetManager
tts = new OfflineTts(null, config);
```

### 7.5 一个必须记住的坑

**`OfflineTts` 构造时如果传入 `ctx.getAssets()`，sherpa 会把绝对路径当 asset 路径读。**

本机实际遇到过：

```text
W/sherpa-onnx: You are using an absolute path '/data/user/0/.../model.onnx',
but assetManager is NOT set to null.
F/sherpa-onnx: Read binary file: Load '...model.onnx' failed
→ 进程直接死亡
```

修复：**`new OfflineTts(null, config)`**。

另一个坑：这台 Android 9 的 `AssetManager.open("tts/...")` 会 `FileNotFoundException`，即使 APK 的 zip 里确实有 `assets/tts/model.onnx`。因此 `copyAsset()` 做了三级兜底：

1. `ctx.getAssets().open(name)`
2. `ctx.getAssets().open("assets/" + name)`
3. `/sdcard/DeepSeekGirl/tts/<basename>`

只要 sdcard 镜像在，清空应用数据/重装后仍能自动恢复。

### 7.6 播放

```text
Text
→ Speaker.speak()
→ 单线程 worker 排队
→ OfflineTts.generate(text, 0, 1.0f)
→ GeneratedAudio.save(cache/feiyu-tts-*.wav)
→ MediaPlayer(USAGE_ASSISTANT)
→ 播放完成后删除临时 WAV
```

约束：

- `speak()` 可以频繁调用，不能打断上一句；
- 未 ready 时只保留一个 `pending`；
- `shutdown()` 释放 engine、线程和播放器；
- 文本自动压到 50 字内；
- 临时 WAV 播放后删除，不在 cache 留垃圾。

### 7.7 验证结果

已实测通过：

```text
I/DafeiyuEye: sherpa TTS ready: 22050 Hz
I/DafeiyuEye: sherpa TTS wav 162300 bytes
I/DafeiyuEye: sherpa TTS wav 173860 bytes
I/DafeiyuEye: sherpa TTS wav 173526 bytes
```

冷启动（删除 `files/tts`）也通过：

```text
I/DafeiyuEye: TTS model from /sdcard/DeepSeekGirl/tts/model.onnx
...
I/DafeiyuEye: sherpa TTS ready: 22050 Hz
I/DafeiyuEye: sherpa TTS wav 173526 bytes
```

### 7.8 性能与后续优化

- 首次加载约 17 秒（复制 21 MB + 加载 18.6 MB 模型）。
- 每句合成约 16 秒（纯 32 位 CPU，2 线程）。
- 当前是“能出声”的可用版本，不是低延迟版本。

后续可做：

1. 常用短句预生成并缓存，避免每次合成。
2. 评估更小的中文 VITS 模型，或降低采样率。
3. 用 `generateWithCallback` 做流式播放，减少首句等待。
4. 对 Eye 观察做更短的播报摘要（当前 50 字仍偏长）。
5. 把 TTS 常驻到独立前台服务，避免 Activity 销毁后重新加载。

---

## 8. 视觉、ASR 与记忆

### 8.1 Eye 观察格式

`/sdcard/DeepSeekGirl/vision/observations.jsonl` 每行一条，例如：

```json
{"ts":1795143112223,"type":"observation","source":"camera","reason":"mark","desc":"画面全黑，无可见物体。"}
```

字段：

- `ts`：毫秒时间戳
- `type`：`frame` / `observation`
- `source`：`camera`
- `reason`：`mark` 等
- `score` / `dup`：选帧评分/去重
- `desc`：视觉模型输出的一句观察

### 8.2 ASR

- 模型：`sherpa-onnx-paraformer-zh-small-2024-03-09`
- 大小：约 81.8 MB
- 已验证：`ASR model loaded`
- 麦克风只在点“听我说”时启用；退到后台立即停

### 8.3 记忆

- `memory/chat/YYYY-MM-DD.jsonl`：原文
- `memory/facts.md`：长期事实
- `memory/self.md`：自述
- `diary/YYYY-MM-DD.md`：日记
- `memory/summaries/`：每日回顾
- `memory/vision.md`：Eye 观察精炼后的事实

Eye 本身不写 `memory/`，只写 `observations.jsonl`。

---

## 9. 网关与模型

主线路：

```text
手机 → Pi 192.169.3.200:8318（wb_lan_proxy.py）
     → Pi 127.0.0.1:8317 WorkBuddyGateway
```

手机 `/sdcard/PhoneAgent/config.local.json`：

```json
{
  "baseUrl": "http://192.169.3.200:8318/v1",
  "model": "deepseek-v4.1-flash",
  "visionModel": "glm-5v-turbo",
  "visionBase": "",
  "visionKey": "",
  "apiKey": "<Pi proxy token，勿写入文档>"
}
```

要点：

- `glm-5v-turbo` 请求必须显式带 `reasoning_effort: "minimal"`，否则网关默认档位可能被上游 400。
- `glm-4.6v` 当前上游返回 `model service info not found`，不可用。
- PC 备用网关：`http://192.169.3.35:8317/v1`；key 在 `E:\Program\WorkBuddyGateway\config.json` 的 `gateway.apiKey`。
- 不要把 API Key、ntfy topic、proxy token 写进公开仓库或本文档。

---

## 10. 开发流程

### 10.1 开始前

1. 确认代码工作区：优先 E 盘正式工作区；如果 TTS 改动尚未合并，先按第 12 节处理。
2. `git status`，确认没有别人的未提交改动。
3. 备份要改的文件；大文件不要进 Git。
4. 设备连接：`adb devices`，确认 `24473/207709145618` 在线。

### 10.2 构建与安装

```powershell
$env:JAVA_HOME = 'C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot'
Set-Location 'E:\个人\大肥鱼\开发工作区\2026-11-20-dafeiyu-phone\app\DafeiyuEye'
.\build.ps1

adb -s 24473/207709145618 install -r .\dist\DafeiyuEye.apk
adb -s 24473/207709145618 shell am start -n com.deepseek.dafeiyu.eye/.MainActivity
```

### 10.3 测试前必须知道

**AutoX 悬浮窗会抢焦点。** 它在运行时，`input tap` 可能点到 AutoX 的“窗口大小”弹窗，而不是 Eye 的按钮。

测试 Eye UI 时：

```powershell
adb -s 24473/207709145618 shell am force-stop org.autojs.autoxjs.v6
# 测完再拉起 agent
adb -s 24473/207709145618 shell am start -n org.autojs.autoxjs.v6/org.autojs.autojs.external.shortcut.ShortcutActivity -e path /sdcard/PhoneAgent/agent.js
```

不要长期停 AutoX；它停了 agent 就不工作。

### 10.4 日志

```powershell
# Eye + TTS
adb -s 24473/207709145618 shell logcat -d -s DafeiyuEye sherpa-onnx | tail -30

# Eye 观察
adb -s 24473/207709145618 shell tail -n 10 /sdcard/DeepSeekGirl/vision/observations.jsonl

# agent
adb -s 24473/207709145618 shell tail -n 30 /sdcard/PhoneAgent/events.log
```

### 10.5 测试矩阵

| 场景 | 预期 |
|---|---|
| 打开 Eye | 不自动开相机 |
| 点“带她看看” | Camera2 打开，observations 增加 |
| 点“试听大肥鱼声音” | 约 17 秒后 ready，生成 WAV，MediaPlayer 播放 |
| 清空 `files/tts` 后重启 | 从 `/sdcard/DeepSeekGirl/tts` 自动恢复 |
| 点“听我说” | ASR 加载，麦克风只在前台 |
| 退出 Eye | EyeService 停止，相机释放 |
| 重启手机 | DafeiyuBoot 拉起 AutoX agent |
| ntfy 发消息 | Chat App 显示并回复 |
| Eye 观察 | agent 的 `syncVisionMemory()` 写入 `memory/vision.md` |

---

## 11. 开发约定

1. **ABI**：新增任何原生库，必须同时提供 `armeabi-v7a` 和 `arm64-v8a`；本机以 32 位为主。
2. **不引入 Gradle**：保持 `aapt2 + javac + d8 + zipalign + apksigner` 的轻量构建，避免网络依赖。
3. **大文件**：ASR 模型、TTS 模型、`libs/`、`build/`、`dist/` 不进 Git；用 `.gitignore` 和 `tools/fetch_eye_deps.ps1` 管理。
4. **日志**：Eye 统一 `Log` tag `DafeiyuEye`；sherpa 原生日志会带 `sherpa-onnx`，不要过滤掉。
5. **TTS 接口**：实现 `TtsProvider` 时必须保证 `speak()` 排队、`stop()` 可中断、`shutdown()` 释放资源。
6. **隐私**：摄像头/麦克风只在 Eye 前台且用户手动点击时启用；自主任务不能拉起 Eye 的传感器。
7. **密钥**：API Key、ntfy topic、proxy token 只写在设备配置或本地 secrets，不进文档、不进 Git。
8. **单一源码**：合并完成后只改 E 盘工作区；C 盘临时副本只作为参考，避免双写。

---

## 12. 当前最大的工程待办：合并 TTS 改动

C 盘临时工作副本相对 E 盘正式工作区，至少有这些改动：

```text
app/DafeiyuEye/AndroidManifest.xml
app/DafeiyuEye/build.ps1
app/DafeiyuEye/src/.../MainActivity.java
app/DafeiyuEye/src/.../Speaker.java
app/DafeiyuEye/src/.../TtsFactory.java
app/DafeiyuEye/src/.../VisionClient.java
app/DafeiyuEye/src/.../ChatClient.java
app/DafeiyuEye/assets/tts/           （新增，6 个文件，约 20 MB）
tools/fetch_eye_deps.ps1
deploy-archimedes/                   （部署材料）
```

建议顺序：

1. 在 E 盘工作区开分支，例如 `feat/eye-builtin-tts-20261120`。
2. 从 C 盘复制上述源码与 `assets/tts`，不要复制 `build/`、`dist/`、`libs/` 等大目录。
3. 在 E 盘重新跑 `tools/fetch_eye_deps.ps1` 和 `build.ps1`。
4. 安装到设备，按第 10.5 节验证。
5. 提交源码、文档和 `.gitignore` 变更；APK 只留在 `产物/` 或 `dist/`，不进 Git。
6. 更新本目录的交接文档，把“C 盘临时副本”改成历史记录，E 盘成为唯一开发源。

---

## 13. 后续路线

- [ ] 合并 TTS 改动到 E 盘工作区并提交。
- [x] 把 126 MB APK 备份到 `小爱老师大肥鱼\产物\DafeiyuEye-2.0-内置TTS.apk`（SHA256 `9317199A27986EAD38F17A0B1771239719B319932CE497D54BFBF3542A951F73`）。
- [ ] 评估 TTS 低延迟方案（缓存短句、流式回调、更小模型）。
- [ ] 完成 ASR 真实语音转写 E2E（当前只验证模型加载）。
- [ ] 检查 AutoX 无障碍服务状态；如果 `auto` 点击失效，手动开启 AutoX.js 无障碍。
- [ ] 在光照环境下验证 Eye 视觉描述，而不是全黑测试画面。
- [ ] 评估是否把 Eye 的 TTS 改成长驻前台服务，减少 Activity 重建开销。
- [ ] 定期更新交接文档的“当前状态”和“验证时间线”。

---

## 14. 参考文档

- 上游总说明：`开发工作区\2026-11-20-dafeiyu-phone\README.md`
- 架构：`docs\ARCHITECTURE.md`
- Eye：`docs\EYE.md`
- TTS 接口：`docs\TTS.md`
- 安装：`docs\SETUP.md`
- 本机旧交接：`开发工作区\2026-11-20-dafeiyu-phone\deploy-archimedes\HANDOFF.md`
- 个人项目总入口：`E:\个人\大肥鱼\README_大肥鱼统一入口.md`
- Pi / QQ / 微信全模块交接：`E:\个人\大肥鱼\文档与优化\大肥鱼_全模块上线后交接文档_2026-11-20.md`

---

## 15. 许可与边界

- 视觉选帧算法参考 `SAOS-3624/dsh-see-now`（Apache-2.0），见 Eye 的 `NOTICE.md`。
- Piper `zh_CN-xiao_ya-medium-int8` 的数据集 BZNSYP 为非商用许可；个人自用可以，商用/分发前必须确认授权或换模型。
- 本项目是专用设备上的个人陪伴项目，不应用于监控、跟踪或未经同意的录音录像。
