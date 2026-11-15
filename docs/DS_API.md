# DeepSeek 集成 TTS / OCR

Dafeiyu-Phone 2.0 支持把部分能力切到 DeepSeek 官方应用 API，复用同一个 API Key。

## TTS

在 DafeiyuEye 首页：

```text
第三方 TTS 区域 → 下拉选择「DeepSeek TTS」
```

使用同一份：

```text
Base URL: https://api.deepseek.com
API Key:  sk-...
```

App 调用：

```http
POST https://api.deepseek.com/v1/tools/tts
Authorization: Bearer sk-...
Content-Type: application/json

{"text":"你好呀","voice_id":"stella","format":"wav"}
```

- `voice_id`：`stella` 女声 / `tide` 男声
- 返回 `audio/wav`，App 用现有队列播放，不抢上一句
- 失败会回退系统 TTS

## OCR

OCR 在 AutoX agent 侧，用远程命令切换：

```text
/ocr deepseek    # 用 DeepSeek OCR
/ocr local       # 用本地 PaddleOCR
```

DeepSeek OCR 调用：

```http
POST https://api.deepseek.com/v1/tools/ocr
Authorization: Bearer sk-...
Content-Type: multipart/form-data

purpose=text_recognition
file=<jpg/png>
```

返回：

```json
{"pages":[{"index":0,"text":"识别出的文字"}]}
```

说明：

- DeepSeek OCR 只返回文字，不返回可点击坐标
- 所以 `ocrNodes()` 的“补节点”仍用本地 PaddleOCR
- `look` / `look_screen` / `look_latest` 的文字识别可走 DeepSeek
- 失败自动回退本地，不会让任务直接失败

## 注意

- TTS/OCR 都会把文本或图片发给 DeepSeek；传感器隐私边界不覆盖网络请求
- API Key 只存在本机，不进日志、不打包
- 两个能力都按 DeepSeek 官方计费
- DeepSeek OCR 支持 PDF / Word / PPT / 图片；App 目前只上传图片
