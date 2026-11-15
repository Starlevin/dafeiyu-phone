package com.deepseek.dafeiyu.eye;

import android.content.Context;

import org.json.JSONObject;

/**
 * DeepSeek 官方语音合成：
 * POST https://api.deepseek.com/v1/tools/tts
 * {"text":"...","voice_id":"stella","format":"wav"}
 *
 * 复用 HttpTtsProvider 的串行播放队列；TTS Key 与视觉 API Key 共用。
 */
public class DeepSeekTtsProvider extends HttpTtsProvider {
    private final String voiceId;

    public DeepSeekTtsProvider(Context ctx, EyeConfig cfg) {
        super(ctx, ttsUrl(cfg), cfg == null ? "" : cfg.apiKey, "", "");
        this.voiceId = cfg == null || cfg.ttsVoice == null || cfg.ttsVoice.trim().isEmpty()
                ? "stella" : cfg.ttsVoice.trim();
    }

    private static String ttsUrl(EyeConfig cfg) {
        String base = cfg == null ? "https://api.deepseek.com" : cfg.baseUrl;
        base = String.valueOf(base == null ? "" : base).replaceAll("/+$", "");
        if (base.isEmpty()) base = "https://api.deepseek.com";
        if (base.endsWith("/v1")) return base + "/tools/tts";
        if (base.endsWith("/v1/")) return base + "tools/tts";
        return base + "/v1/tools/tts";
    }

    @Override
    protected JSONObject buildBody(String text) throws Exception {
        JSONObject body = new JSONObject();
        body.put("text", text);
        body.put("voice_id", "tide".equalsIgnoreCase(voiceId) ? "tide" : "stella");
        body.put("format", "wav");
        body.put("filter_markdown", true);
        return body;
    }
}
