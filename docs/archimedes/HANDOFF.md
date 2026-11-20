# 小爱老师大肥鱼 · 交接文档

更新时间：2026-11-20  
交接对象：接手 `dafeiyu-phone` 在小爱老师 `Archimedes` 上继续开发的工程师/模型  
文档位置：`E:\个人\大肥鱼\小爱老师大肥鱼\交接文档.md`

> 先读结论，再读“源码现状”和“已知问题”。当前最容易踩的坑不是设备，而是 **E 盘正式工作区还没有合并内置 TTS 改动**。

---

## 1. 一句话结论

小爱老师上的 `dafeiyu-phone` 1.1.0 主链路已配置可用；`DafeiyuEye` 2.0 已自构筑 32 位版本，相机、视觉观察、Paraformer ASR、内置 Piper 小雅 TTS 都已验证。当前可运行 APK 为 **126,052,474 B**，安装包在 C 盘临时工作副本；E 盘正式工作区尚未合并 TTS 改动。

---

## 2. 当前运行状态（2026-11-20 11:20 左右）

| 项 | 状态 |
|---|---|
| AutoX agent | 已重新拉起，10:54:03 `开始运行[/sdcard/PhoneAgent/agent.js]` |
| DafeiyuChat | 进程在跑，已拉到前台 |
| DafeiyuEye | 进程在跑；`MainActivity` 手动打开，不自动开相机 |
| EyeService | 测试后已停止，相机释放 |
| 内置 TTS | 已 ready；模型在 `files/tts` 和 sdcard 镜像 |
| 媒体音量 | `STREAM_MUSIC speaker = 7/15`（原为 1/15，已调高） |
| 睡眠作息 | 23:00–08:00，当前白天应在线 |
| 远程 ntfy | 凌晨和早上均验证过收发；topic 见设备文件 |
| 开机自启 | 02:51 重启后 02:52:53 自动拉起 agent，已验证 |

设备当前进程：

```text
com.deepseek.dafeiyu.chat
org.autojs.autoxjs.v6
com.deepseek.dafeiyu.eye
```

---

## 3. 设备与连接

| 项 | 值 |
|---|---|
| 型号 | Xiaomi `Archimedes`（小爱老师） |
| 系统 | Android 9 / SDK 28 |
| ABI | 纯 32 位 `armeabi-v7a`；`abilist64` 为空 |
| 屏幕 | 480×800 |
| RAM | 3 GB |
| Root | Magisk，`su` 可用 |
| USB ADB | `24473/207709145618`（当前可用） |
| Wi-Fi ADB | `192.169.3.30:5555`（重启后 offline，ping 不通；以 USB 为准） |
| ADB 路径 | `C:\Users\gosso\AppData\Local\Android\Sdk\platform-tools\adb.exe` |

常用检查：

```powershell
adb -s 24473/207709145618 devices
adb -s 24473/207709145618 shell getprop ro.build.version.sdk
adb -s 24473/207709145618 shell getprop ro.product.cpu.abilist
adb -s 24473/207709145618 shell dumpsys package com.deepseek.dafeiyu.eye | grep -E "primaryCpuAbi|lastUpdateTime"
```

---

## 4. 已安装组件

| 组件 | 包名 | 版本/状态 |
|---|---|---|
| AutoX.js | `org.autojs.autoxjs.v6` | 6.6.8 / versionCode 668 |
| DafeiyuChat | `com.deepseek.dafeiyu.chat` | 1.1.0 |
| DafeiyuBoot | `com.deepseek.dafeiyu.boot` | 1.0.0 |
| DafeiyuEye | `com.deepseek.dafeiyu.eye` | 2.0 自构筑，无 `versionName`；`primaryCpuAbi=armeabi-v7a` |

Eye 权限：

- `CAMERA`、`RECORD_AUDIO`、`READ_EXTERNAL_STORAGE`、`WRITE_EXTERNAL_STORAGE` 已授予
- `SYSTEM_ALERT_WINDOW: allow`
- `usesCleartextTraffic=true`，允许访问 Pi 的 HTTP 网关

---

## 5. 源码现状（最重要）

### 5.1 E 盘正式工作区

```text
E:\个人\大肥鱼\开发工作区\2026-11-20-dafeiyu-phone
```

- Git 分支：`main`
- 当前只有 `app/DafeiyuEye/build.ps1` 有本地修改
- `Speaker.java` 仍是上游旧版（约 3.3 KB），**没有内置 TTS**
- 没有 `assets/tts`、没有 126 MB APK

### 5.2 C 盘临时工作副本（当前可运行版本）

```text
C:\Users\gosso\Doubao\chats\2026-11-20\dafeiyu-phone
```

有未提交改动：

```text
app/DafeiyuEye/AndroidManifest.xml
app/DafeiyuEye/build.ps1
app/DafeiyuEye/src/com/deepseek/dafeiyu/eye/MainActivity.java
app/DafeiyuEye/src/com/deepseek/dafeiyu/eye/Speaker.java
app/DafeiyuEye/src/com/deepseek/dafeiyu/eye/TtsFactory.java
app/DafeiyuEye/src/com/deepseek/dafeiyu/eye/VisionClient.java
app/DafeiyuEye/src/com/deepseek/dafeiyu/eye/ChatClient.java
app/DafeiyuEye/assets/tts/            新增，6 个文件，约 20 MB
tools/fetch_eye_deps.ps1
deploy-archimedes/                     部署材料
```

APK：

```text
C:\Users\gosso\Doubao\chats\2026-11-20\dafeiyu-phone\app\DafeiyuEye\dist\DafeiyuEye.apk
126,052,474 B
2026-11-20 10:49:41
```

已备份到 E 盘（同哈希）：

```text
E:\个人\大肥鱼\小爱老师大肥鱼\产物\DafeiyuEye-2.0-内置TTS.apk
126,052,474 B
SHA256 9317199A27986EAD38F17A0B1771239719B319932CE497D54BFBF3542A951F73
```

**接手第一件事**：把 C 盘改动合入 E 盘工作区，在 E 盘重新构建、安装、验证；完成前不要以 E 盘旧源码重新打包，否则会得到没有 TTS 的版本。

详细合并步骤见 `开发文档.md` 第 12 节。

---

## 6. 配置

### 6.1 Agent 网关

文件：

```text
/sdcard/PhoneAgent/config.local.json
```

关键值：

```json
{
  "baseUrl": "http://192.169.3.200:8318/v1",
  "model": "deepseek-v4.1-flash",
  "visionModel": "glm-5v-turbo",
  "visionBase": "",
  "visionKey": "",
  "apiKey": "<Pi proxy token，见设备文件，勿写入文档>"
}
```

- 主对话：`deepseek-v4.1-flash`
- 视觉：`glm-5v-turbo`，必须带 `reasoning_effort: "minimal"`
- `glm-4.6v` 不可用：上游 `model service info not found`
- PC 备用网关：`http://192.169.3.35:8317/v1`，key 在 `E:\Program\WorkBuddyGateway\config.json` 的 `gateway.apiKey`

### 6.2 Eye 配置

文件：

```text
/sdcard/DeepSeekGirl/eye_config.json
```

```json
{
  "baseUrl": "http://192.169.3.200:8318/v1",
  "apiKey": "<Pi proxy token>",
  "model": "glm-5v-turbo",
  "intervalMs": 80,
  "ttsMode": "system",
  "ttsVoice": "feiyu",
  "ttsStyle": "cute"
}
```

`ttsMode:"system"` 现在不是系统 TTS，而是映射到内置 `Speaker`。不要因为“系统 TTS 缺失”去装第三方 TTS。

### 6.3 远程聊天

- topic 文件：`/sdcard/DeepSeekGirl/remote/topic.txt`
- Chat App 顶部显示“睡觉中/在线”
- topic 就是密码，不要公开
- 公共 ntfy 实例的 `poll=1&since=...` 查询不稳定；判断在线以 `events.log`、`memory/chat/*.jsonl`、Chat App 截图为准

---

## 7. 内置 TTS 交接

### 7.1 方案

- 引擎：sherpa-onnx `OfflineTts`
- 模型：Piper `zh_CN-xiao_ya-medium-int8`
- 采样率：22050 Hz
- APK 内：`app/DafeiyuEye/assets/tts/`
- 设备镜像：`/sdcard/DeepSeekGirl/tts/`
- 私有副本：`/data/user/0/com.deepseek.dafeiyu.eye/files/tts/`

### 7.2 已验证日志

```text
I/DafeiyuEye: sherpa TTS ready: 22050 Hz
I/DafeiyuEye: sherpa TTS wav 162300 bytes
I/DafeiyuEye: sherpa TTS wav 173860 bytes
I/DafeiyuEye: sherpa TTS wav 173526 bytes
```

冷启动兜底：

```text
I/DafeiyuEye: TTS model from /sdcard/DeepSeekGirl/tts/model.onnx
...
I/DafeiyuEye: sherpa TTS ready: 22050 Hz
I/DafeiyuEye: sherpa TTS wav 173526 bytes
```

### 7.3 关键代码点

- `OfflineTts` 必须传 `null` AssetManager：

```java
tts = new OfflineTts(null, config);
```

如果传 `ctx.getAssets()`，sherpa 会把绝对路径当 asset 读取，直接 fatal。

- `copyAsset` 三级兜底：assets → `assets/` → `/sdcard/DeepSeekGirl/tts`。
- `TtsFactory` 的 `system` 分支返回 `Speaker`。
- `MainActivity` 的 TTS 下拉首项是“内置离线 TTS”。
- `Speaker` 单线程排队，`MediaPlayer(USAGE_ASSISTANT)` 播放，播放后删临时 WAV。

### 7.4 不要做的事

- **不要删 `/sdcard/DeepSeekGirl/tts`**：这是清数据/重装后的恢复源。
- **不要把 `OfflineTts` 改回 `ctx.getAssets()`**：会原生崩溃。
- **不要指望系统 TTS**：这台设备没有。
- **不要商用分发当前声音**：BZNSYP 数据集为非商用许可。

### 7.5 延迟

- 首次加载约 17 秒
- 每句合成约 16 秒
- 这是 32 位 CPU 的硬限制；点“试听”后要等，不是没反应
- 播报文本已在 `Speaker.shorten()` 压到 50 字内

---

## 8. 构建与安装

### 8.1 在 C 盘当前副本构建（现在可复现）

```powershell
$env:JAVA_HOME = 'C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot'
Set-Location 'C:\Users\gosso\Doubao\chats\2026-11-20\dafeiyu-phone\app\DafeiyuEye'
.\build.ps1
```

### 8.2 安装

```powershell
adb -s 24473/207709145618 install -r 'C:\Users\gosso\Doubao\chats\2026-11-20\dafeiyu-phone\app\DafeiyuEye\dist\DafeiyuEye.apk'
adb -s 24473/207709145618 shell am start -n com.deepseek.dafeiyu.eye/.MainActivity
```

`install -r` 会保留应用数据；模型文件仍在 `files/tts`。如果是清数据/全新安装，会从 `/sdcard/DeepSeekGirl/tts` 复制。

### 8.3 模型恢复

```powershell
adb -s 24473/207709145618 push 'C:\Users\gosso\Doubao\chats\2026-11-20\dafeiyu-phone\app\DafeiyuEye\assets\tts\.' /sdcard/DeepSeekGirl/tts/
adb -s 24473/207709145618 shell su -c 'rm -rf /data/user/0/com.deepseek.dafeiyu.eye/files/tts'
adb -s 24473/207709145618 shell am force-stop com.deepseek.dafeiyu.eye
adb -s 24473/207709145618 shell am start -n com.deepseek.dafeiyu.eye/.MainActivity
```

然后再点“试听大肥鱼声音”。

---

## 9. 快速验证清单

### 9.1 进程

```powershell
adb -s 24473/207709145618 shell ps -A | findstr /i "autojs dafeiyu"
```

预期至少看到 Chat、AutoX、Eye。

### 9.2 TTS

```powershell
adb -s 24473/207709145618 shell logcat -d -s DafeiyuEye sherpa-onnx | tail -20
```

预期：

```text
sherpa TTS ready: 22050 Hz
sherpa TTS wav ... bytes
```

### 9.3 Eye 观察

```powershell
adb -s 24473/207709145618 shell tail -n 5 /sdcard/DeepSeekGirl/vision/observations.jsonl
```

预期有 `type":"observation"` 和 `desc`。

### 9.4 相机释放

```powershell
adb -s 24473/207709145618 shell dumpsys media.camera | grep -A3 "Active Camera Clients"
```

退出 Eye 后应为空。

### 9.5 Agent

```powershell
adb -s 24473/207709145618 shell tail -n 20 /sdcard/PhoneAgent/events.log
```

### 9.6 媒体音量

```powershell
adb -s 24473/207709145618 shell dumpsys audio | findstr /i "STREAM_MUSIC"
```

当前 speaker 应为 `7`。

---

## 10. 已知问题与风险

### 10.1 E 盘源码未合并（最高优先级）

E 盘工作区没有内置 TTS 改动；C 盘副本有。不要在 E 盘直接打旧包，否则 TTS 会消失。合并方案见开发文档第 12 节。

### 10.2 AssetManager 读不到 APK 资产

这台 Android 9 上 `ctx.getAssets().open("tts/...")` 会 `FileNotFoundException`，尽管 APK zip 内确实有 `assets/tts/model.onnx`。代码已用 sdcard 镜像兜底。不要删 sdcard 模型，也不要误以为 APK 没打包。

### 10.3 AutoX 悬浮窗抢焦点

AutoX 运行时，Eye 的按钮点击可能被 AutoX 的“窗口大小”弹窗截走。测试 Eye UI 前先：

```powershell
adb -s 24473/207709145618 shell am force-stop org.autojs.autoxjs.v6
```

测试完必须重新拉起：

```powershell
adb -s 24473/207709145618 shell am start -n org.autojs.autoxjs.v6/org.autojs.autojs.external.shortcut.ShortcutActivity -e path /sdcard/PhoneAgent/agent.js
```

### 10.4 AutoX 无障碍服务状态

设备 `accessibility_enabled=1`，但当前 `enabled_accessibility_services` 列表是：

```text
com.tombayley.volumepanel
com.farplace.qingzhuo
com.sohu.inputmethod.sogou
me.hisn.mygesture
```

**不包含 AutoX**。Agent 仍能以 root/屏幕节点方式跑；如果后续 `auto` 点击/读屏失效，先在系统设置里手动开启 **AutoX.js 无障碍**，再重启 agent。不要用 adb 强行改无障碍列表。

### 10.5 TTS 延迟

首次 17 秒、每句 16 秒。不是死机；不要反复点“试听”，否则会排队。

### 10.6 许可

Piper 小雅模型数据集 BZNSYP 为非商用许可。个人自用可以；商用/分发要换模型或确认授权。

### 10.7 其他

- Wi-Fi ADB 重启后 offline；USB 可用。
- ntfy 公共实例的查询接口不稳定，不要用 poll 判断在线。
- `glm-4.6v` 不可用；视觉统一 `glm-5v-turbo` + `reasoning_effort=minimal`。
- Eye 测试画面全黑是镜头暗/遮挡，不等于视觉管线坏了；到光下再试。
- `libs/`、`build/`、`dist/`、`res/raw/`、`.eye-deps/` 是大文件/构建产物，不进 Git。

---

## 11. 恢复与回滚

### 11.1 应用更新

同签名 `install -r` 覆盖；应用数据保留。

### 11.2 清数据后 TTS 没声音

1. 确认 `/sdcard/DeepSeekGirl/tts` 有 6 个文件。
2. 重新 `am force-stop` 并启动 Eye。
3. 点“试听”，等 17 秒。
4. 看 logcat 是否出现 `TTS model from /sdcard/...` 和 `sherpa TTS ready`。

### 11.3 Agent 没起来

```powershell
adb -s 24473/207709145618 shell am start -n org.autojs.autoxjs.v6/org.autojs.autojs.external.shortcut.ShortcutActivity -e path /sdcard/PhoneAgent/agent.js
```

看 `events.log` 和 `开始运行[...]`。

### 11.4 开机自启

`DafeiyuBoot` 的 `BOOT_COMPLETED` → `BootService` 8/20/40 秒重试 → AutoX `ShortcutActivity`。`BootService` 是 `exported=false`，手动 `am start-foreground-service` 报 “not exported” 属正常；服务会 `stopSelf()`，`dumpsys` 为空也正常。

### 11.5 回滚

- 当前 126 MB APK 已覆盖早期 105 MB 版本；旧 APK 没有保留。
- 如需回滚到无 TTS 版本，从 E 盘上游源码重新构建；但会失去内置 TTS。
- 当前 APK 已备份到 `E:\个人\大肥鱼\小爱老师大肥鱼\产物\DafeiyuEye-2.0-内置TTS.apk`（SHA256 `9317199A27986EAD38F17A0B1771239719B319932CE497D54BFBF3542A951F73`）。不要只依赖 C 盘临时目录。

---

## 12. 接手后的建议顺序

1. **备份当前 APK** 到 `小爱老师大肥鱼\产物\`。
2. **合并 TTS 改动**到 E 盘工作区，重新构建、安装、验证。
3. 在 E 盘提交源码；确认 `dist/`、`libs/`、`assets/tts` 的 Git 策略。
4. 验证 ASR 真实语音转写（当前只验证模型加载）。
5. 在光照环境验证 Eye 视觉描述。
6. 评估 TTS 低延迟方案。
7. 检查 AutoX 无障碍与开机自启在重启后的实际表现。
8. 更新本目录两份文档的“当前状态”和“验证时间线”。

---

## 13. 验证时间线

| 时间 | 事件 |
|---|---|
| 02:51 | 重启手机 |
| 02:52:53 | DafeiyuBoot 自动拉起 agent，进入睡眠作息 |
| 02:53:58 | ntfy 重启后消息收到，Chat App 回睡眠文案 |
| 03:04–03:07 | Eye 视觉观察写入 observations；agent `视觉记忆 +3/+4/+6` |
| 03:06:25 | `ASR model loaded`，81.8 MB Paraformer 加载成功 |
| 03:07:45 | Eye 相机释放 |
| 10:33 | 内置 TTS 版 APK 安装 |
| 10:41 | 首次试听暴露 `AssetManager` 读不到 assets，原生 fatal |
| 10:47 | 改为 `OfflineTts(null, config)` 后首次成功，WAV 162,300 B |
| 10:52 | 冷启动删除 `files/tts`，6 个文件从 sdcard 恢复 |
| 10:53:05 | 冷启动 TTS ready；10:53:21 生成 173,526 B WAV |
| 10:54:03 | AutoX 重新拉起 agent，`开始运行[/sdcard/PhoneAgent/agent.js]` |
| 11:20 | 媒体音量从 1/15 调到 7/15；Chat App 前台 |
| 11:21 | 创建 `E:\个人\大肥鱼\小爱老师大肥鱼` 专用文件夹 |

---

## 14. 关键路径速查

```text
个人项目根：E:\个人\大肥鱼
专用文档：E:\个人\大肥鱼\小爱老师大肥鱼
备份 APK：E:\个人\大肥鱼\小爱老师大肥鱼\产物\DafeiyuEye-2.0-内置TTS.apk
正式代码：E:\个人\大肥鱼\开发工作区\2026-11-20-dafeiyu-phone
当前 TTS 副本：C:\Users\gosso\Doubao\chats\2026-11-20\dafeiyu-phone
当前 APK：C:\Users\gosso\Doubao\chats\2026-11-20\dafeiyu-phone\app\DafeiyuEye\dist\DafeiyuEye.apk
设备模型：/sdcard/DeepSeekGirl/tts
私有模型：/data/user/0/com.deepseek.dafeiyu.eye/files/tts
Eye 配置：/sdcard/DeepSeekGirl/eye_config.json
Agent 配置：/sdcard/PhoneAgent/config.local.json
Agent 日志：/sdcard/PhoneAgent/events.log
Eye 观察：/sdcard/DeepSeekGirl/vision/observations.jsonl
```

---

## 15. 变更记录

### 2026-11-20

- 创建 `E:\个人\大肥鱼\小爱老师大肥鱼` 专用开发/交接文件夹。
- 写入开发文档和交接文档。
- 记录内置 TTS 的完整方案、验证结果、延迟和许可限制。
- 标记 E 盘正式工作区与 C 盘临时副本的源码差异。
