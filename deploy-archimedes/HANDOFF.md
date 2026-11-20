# 小爱老师 · dafeiyu-phone 配置交接

更新时间：2026-11-20 10:55（CST）
设备：Xiaomi `Archimedes` / Android 9（SDK 28）/ 纯 32 位 `armeabi-v7a` / 480×800 / 3 GB RAM
USB ADB：`24473/207709145618`（当前可用）
Wi-Fi ADB：`192.169.3.30:5555`（重启后 offline，ping 不通；USB 正常）
仓库：`C:\Users\gosso\Doubao\chats\2026-11-20\dafeiyu-phone`

## 1. 结论

`dafeiyu-phone` 的 1.1.0 主链路已全部配置并跑通；2.0 的 `DafeiyuEye` 官方构建只打 `arm64-v8a`，本机是纯 32 位，**已自行构筑 `armeabi-v7a` 版本并安装成功**，相机、视觉观察、内置 sherpa-onnx ASR、内置 Piper 离线 TTS 都已验证。

当前手机状态：AutoX agent 已重新拉起并运行、处于 23:00–08:00 睡眠作息、ntfy 远程可收可回、Chat App 可显示；Eye 为手动打开（不自动开相机），测试后已停掉相机释放。

## 2. 版本选型

| 组件 | 包名 | 版本 | 说明 |
|---|---|---|---|
| AutoX.js | `org.autojs.autoxjs.v6` | 6.6.8（versionCode 668） | 仓库 README 写 arm64，但 AutoX 安装包本身有 armv7 版本，本机运行正常 |
| DafeiyuChat | `com.deepseek.dafeiyu.chat` | 1.1.0 | 纯 Java，无 ABI 限制 |
| DafeiyuBoot | `com.deepseek.dafeiyu.boot` | 1.0.0 | 纯 Java，负责开机自启 |
| DafeiyuEye | `com.deepseek.dafeiyu.eye` | 2.0 自构筑（无 versionName） | `primaryCpuAbi=armeabi-v7a`，APK 126,052,474 B，含 Piper 小雅离线 TTS 模型，v2/v3 签名 |

选 1.1.0 的原因：设备 `abilist64` 为空，官方 2.0 Eye 的原生库只有 arm64；1.1.0 的 AutoX/Chat/Boot 全部能在 32 位跑。2.0 Eye 通过下面的改造补上，不需要替换主链路。

## 3. 模型网关

主线路：手机 → Pi `192.169.3.200:8318`（隔离 LAN 反向代理）→ Pi 本机 `127.0.0.1:8317` WorkBuddyGateway。
手机 `/sdcard/PhoneAgent/config.local.json`：

```json
{
  "apiKey": "<Pi proxy token，见手机配置，不在文档重复>",
  "visionKey": "",
  "baseUrl": "http://192.169.3.200:8318/v1",
  "model": "deepseek-v4.1-flash",
  "visionModel": "glm-5v-turbo",
  "visionBase": ""
}
```

- 主对话：`deepseek-v4.1-flash`，已验证 200。
- 视觉：`glm-5v-turbo` + `reasoning_effort: "minimal"`，已验证文本/图片都 200。
- `glm-4.6v` 当前上游返回 “model service info not found”，账号池冷却，**不可用**；已统一改到 `glm-5v-turbo`。
- PC 备用线路：`http://192.169.3.35:8317/v1`，key 在 `E:\Program\WorkBuddyGateway\config.json` 的 `gateway.apiKey`；`glm-5v-turbo` 同样需要 `reasoning_effort=minimal`。
- MiMo 直连（`api.xiaomimimo.com`）返回 402 余额不足；Gemini key 返回 400 invalid，均未采用。

## 4. 远程聊天

- ntfy：`https://ntfy.envs.net/dafeiyu-i6bfmeiz4obbxej2z4gnn41h`
- topic 文件：`/sdcard/DeepSeekGirl/remote/topic.txt`
- Chat App 显示“睡觉中/在线”，远程消息会回帖；重启后 02:53:58 收到测试消息并回睡眠文案，03:07:43 再次收到最终检查消息。
- 注意：该公共 ntfy 实例的 `poll=1&since=...` 查询不稳定，验证以 `/sdcard/PhoneAgent/events.log`、`/sdcard/DeepSeekGirl/memory/chat/*.jsonl` 和 Chat App 截图为准。

## 5. 开机自启

- `DafeiyuBoot` 的 `BOOT_COMPLETED` receiver 已注册，`RECEIVE_BOOT_COMPLETED granted=true`，包 `stopped=false`。
- BootService 收到开机广播后延迟 8/20/40 秒重试，通过 AutoX `ShortcutActivity` + `path=/sdcard/PhoneAgent/agent.js` 启动 agent。
- AutoX 自带开机任务已关闭（`/sdcard/PhoneAgent/boot_setup.txt` = `off ...`），所以拉起链路是 DafeiyuBoot。
- 实测：02:51 左右重启 → 02:52:53 agent 启动并进入睡眠作息，**开机自启生效**，启动约 90 秒。
- 说明：BootService 是 `exported=false`，用 `adb shell am start-foreground-service` 手动启动会报 “not exported”，这是正常的；不要把它当成开机失败。服务启动后会 `stopSelf()`，所以 `dumpsys activity services com.deepseek.dafeiyu.boot` 为空也正常。
- 三个包都在 deviceidle 白名单；无障碍包含 AutoX 的 `AccessibilityService`。

## 6. DafeiyuEye 2.0 自构筑

官方仓库的 `app/DafeiyuEye` 只有 Java 源码，`build.ps1` 只打 `libs/arm64-v8a`，`dist` 里没有 APK。已做以下改造：

1. `tools/fetch_eye_deps.ps1`：同时拷贝 `arm64-v8a` 与 `armeabi-v7a` 的 sherpa-onnx `.so`。
2. `app/DafeiyuEye/build.ps1`：JDK 改为优先用 `JAVA_HOME`（本机 `C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot`）；原生库同时打包两个 ABI。
3. `AndroidManifest.xml`：`usesCleartextTraffic=true`（Pi 是 HTTP）；补 `WRITE_EXTERNAL_STORAGE`/`READ_EXTERNAL_STORAGE`（Android 9 写 `/sdcard` 必需）。
4. `res/values/styles.xml`：补 `AppTheme`（否则 aapt2 报 `style/AppTheme not found`）。
5. `VisionClient.java` / `ChatClient.java`：base URL 统一补 `/v1`；`glm-5` 模型显式加 `reasoning_effort=minimal`（否则网关默认档位被上游 400 拒绝）；Chat max_tokens 60→120。
6. `Speaker.java` 重写为内置 sherpa-onnx 离线 TTS：Piper `zh_CN-xiao_ya-medium-int8`（22050 Hz 女声），模型放 `assets/tts`，首次复制到 `files/tts`；`OfflineTts` 构造时 **AssetManager 必须传 `null`**，否则绝对路径会被当资产路径，native 直接 fatal（本次已修复）。
7. `MainActivity.java`：TTS 下拉首项改为“内置离线 TTS”，`speaker` 改为懒加载；`TtsFactory` 的 `system` 模式直接映射到内置 `Speaker`，不再依赖系统 TTS。
8. 构建产物：`app/DafeiyuEye/dist/DafeiyuEye.apk`（126 MB，含 32/64 位库 + TTS 模型，v2/v3 签名）。

手机侧：

- 安装后 `dumpsys package` 显示 `primaryCpuAbi=armeabi-v7a`。
- 已授权 CAMERA、RECORD_AUDIO、READ/WRITE_EXTERNAL_STORAGE，`SYSTEM_ALERT_WINDOW: allow`。
- Eye 配置：`/sdcard/DeepSeekGirl/eye_config.json`

```json
{"baseUrl":"http://192.169.3.200:8318/v1","apiKey":"<Pi proxy token>","model":"glm-5v-turbo","intervalMs":80,"ttsMode":"system","ttsVoice":"feiyu","ttsStyle":"cute"}
```

- `ttsMode:"system"` 现在不再调用系统 TTS，而是映射到 Eye 内置 `Speaker`（Piper 小雅）。系统 TTS 引擎缺失不影响出声。

验证结果：

- 相机：`dumpsys media.camera` 显示 `com.deepseek.dafeiyu.eye` PID 5339 打开 Camera ID 0。
- 视觉：`/sdcard/DeepSeekGirl/vision/observations.jsonl` 03:04–03:07 出现多条 `observation`，例如“全黑，略偏红，无可见物体或文字”“全黑，无可见物体”“一片黑，隐约偏红，无可见物”。
- agent 侧 `syncVisionMemory()` 已把 Eye 观察写入 `memory/vision.md`，events.log 03:04:46 起出现“视觉记忆 +3 条/+4 条/+6 条”。
- ASR：03:06:25 logcat `ASR model loaded`，81.8 MB Paraformer 模型在 32 位 sherpa-onnx 上加载成功；本次环境无语音输入，未做完整转写。
- TTS：**已改为内置 Piper 离线 TTS，不再依赖系统 TTS**。logcat 已出现 `sherpa TTS ready: 22050 Hz`；试听生成 WAV 162,300 B / 173,860 B / 173,526 B，`MediaPlayer` 无报错。冷启动删除 `files/tts` 后，6 个文件从 `/sdcard/DeepSeekGirl/tts` 自动复制并再次 ready，说明重装/清数据后也能恢复。
- TTS 模型镜像：`/sdcard/DeepSeekGirl/tts/` 已放全套 6 个文件（model.onnx 18,652,795 B、lexicon.txt 2,064,505 B、tokens.txt 485 B、date/number/phone FST）。APK 资产里也有一份，但这台 Android 9 的 `AssetManager.open("tts/...")` 会 FileNotFound，所以代码增加了 sdcard 兜底；**不要删 sdcard 这份**。
- 启动耗时：首次加载模型约 17 秒（10:52:48 复制完成 → 10:53:05 ready），之后每句合成约 16 秒；这是纯 32 位 CPU 的硬限制，先保证能出声。正式用时建议把播报文本压到 50 字以内（`Speaker.shorten` 已做）。
- 测试后已 `am force-stop com.deepseek.dafeiyu.eye`，`dumpsys media.camera` 的 Active Camera Clients 为空，相机已释放。

## 7. 已改动的文件

- 设备 `/sdcard/PhoneAgent/agent.js`：172,742 B，SHA256 `37627dfae0cb3905bdf09f56800583cde8e78e481c035c654cc607fdee4c04f8`；保留 Codex 的改动，新增 `glm-5` 视觉请求的 `reasoning_effort=minimal`。
- `_device/agent.js`：同上，作为设备副本。
- `_device/config.local.json`：`visionModel` 改为 `glm-5v-turbo`。
- `app/DafeiyuEye/AndroidManifest.xml`、`build.ps1`、`src/.../VisionClient.java`、`src/.../ChatClient.java`、`tools/fetch_eye_deps.ps1`、`res/values/styles.xml`。
- `app/DafeiyuEye/src/.../Speaker.java`：新增内置 sherpa-onnx TTS；`MainActivity.java`：TTS 下拉首项“内置离线 TTS”、speaker 懒加载；`TtsFactory.java`：`system` → `Speaker`。
- `app/DafeiyuEye/assets/tts/`：Piper 小雅模型、词典、tokens、三个 FST；APK 里的同名资产是备用。
- `/sdcard/DeepSeekGirl/tts/`：设备端模型镜像，Eye 冷启动的兜底来源。
- `_device/agent.devicebak.js` 是改视觉参数前的设备备份。
- 仓库 `autox/agent.js` 仍是上游原版，没有覆盖。

## 8. 常用操作

```powershell
# ADB
C:\Users\gosso\AppData\Local\Android\Sdk\platform-tools\adb.exe devices

# 重启 agent / 切网关（Antigravity 留下的脚本）
pwsh -File E:\chat\dafeiyu-phone\deploy-archimedes\switch-gateway.ps1 `
  -Target Pi -Model deepseek-v4.1-flash -VisionModel glm-5v-turbo `
  -Serial 24473/207709145618 -RestartAgent

# 看 agent 日志
adb -s 24473/207709145618 shell tail -n 30 /sdcard/PhoneAgent/events.log

# 打开 Eye（进入后手动点“带她看看”，不会自动开相机）
adb -s 24473/207709145618 shell am start -n com.deepseek.dafeiyu.eye/.MainActivity

# 看 Eye 观察
adb -s 24473/207709145618 shell tail -n 10 /sdcard/DeepSeekGirl/vision/observations.jsonl

# 看 TTS 日志
adb -s 24473/207709145618 shell logcat -d -s DafeiyuEye sherpa-onnx | tail -20

# 恢复 TTS 模型镜像（清数据/换机后）
adb -s 24473/207709145618 push .\dafeiyu-phone\app\DafeiyuEye\assets\tts\. /sdcard/DeepSeekGirl/tts/

# 测试 Eye 按钮前先停 AutoX，避免悬浮窗抢焦点；测完再拉起
adb -s 24473/207709145618 shell am force-stop org.autojs.autoxjs.v6
adb -s 24473/207709145618 shell am start -n org.autojs.autoxjs.v6/org.autojs.autojs.external.shortcut.ShortcutActivity -e path /sdcard/PhoneAgent/agent.js
```

## 9. 已知限制 / 后续

- 系统 TTS 引擎仍然没有，但 Eye 已改为内置 Piper，不再依赖它；`ttsMode` 保持 `system` 即可。
- Piper `zh_CN-xiao_ya-medium-int8` 的数据集是 BZNSYP，**非商用许可**；个人自用可以，后续商用/分发要换模型或确认授权。
- TTS 在这台 32 位机器上首次加载约 17 秒、每句约 16 秒；不是卡死，是算力限制。
- AutoX 悬浮窗会抢焦点，导致 `input tap` 点到它的“窗口大小”弹窗。测试 Eye 按钮时先 `am force-stop org.autojs.autoxjs.v6`，测完再拉起 agent；不要长期停 AutoX。
- Eye 观察都是“全黑”是因为测试时镜头处于暗处/被遮挡，管线本身正常；在光下打开“带她看看”即可。
- Wi-Fi ADB 重启后 offline，USB 可用；如需无线调试要等 Wi-Fi 重连或重新 `adb connect`。
- ntfy 公共实例查询接口不可靠，不要用 poll 查询判断在线。
- `glm-4.6v` 上游不可用；若换回或换别的视觉模型，记得保留 `reasoning_effort=minimal`，否则网关默认档位可能 400。
- Eye 的 `libs/`、`res/raw/`、`build/`、`dist/` 和 `.eye-deps/` 都是构建产物/大文件，不建议提交 Git。

## 10. 验证时间线

- 02:51 重启手机；02:52:53 agent 自动启动（睡眠作息）。
- 02:53:58 ntfy 重启后消息收到；Chat App 显示睡眠回复。
- 03:04:46 / 03:05:17 agent `视觉记忆 +3/+4 条`（来自 Eye observations）。
- 03:05:37 推送视觉补丁并重启 agent；03:05:38 恢复睡眠。
- 03:06:25 Eye `ASR model loaded`。
- 03:07:43 ntfy 最终检查消息收到；03:07:45 Eye 相机已释放。
- 10:33 内置 TTS 版 APK 安装；10:41 首次试听暴露 `AssetManager` 读不到 assets，原生 fatal。
- 10:47 改为 `OfflineTts(null, config)` 后首次成功：`sherpa TTS ready: 22050 Hz`，WAV 162,300 B。
- 10:52 冷启动删除 `files/tts`，6 个文件从 `/sdcard/DeepSeekGirl/tts` 自动恢复；10:53:05 ready，10:53:21 生成 173,526 B WAV。
- 10:54:03 AutoX 重新拉起 agent，日志 `开始运行[/sdcard/PhoneAgent/agent.js]`。
