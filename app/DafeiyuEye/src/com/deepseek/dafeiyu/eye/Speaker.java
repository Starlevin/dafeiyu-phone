package com.deepseek.dafeiyu.eye;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.util.Log;

import com.k2fsa.sherpa.onnx.GeneratedAudio;
import com.k2fsa.sherpa.onnx.OfflineTts;
import com.k2fsa.sherpa.onnx.OfflineTtsConfig;
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig;
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 内置离线 TTS：sherpa-onnx + Piper 中文小雅（22050Hz）。
 * 不依赖系统 TTS 引擎；模型随 APK 放在 assets/tts，首次使用复制到 files/tts。
 */
public class Speaker implements TtsProvider {
    private static final String TAG = "DafeiyuEye";

    private final Context ctx;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private volatile OfflineTts tts;
    private volatile boolean ready;
    private volatile boolean closed;
    private volatile MediaPlayer player;
    private String pending;

    public Speaker(Context ctx) {
        this.ctx = ctx.getApplicationContext();
        worker.execute(this::init);
    }

    private void init() {
        try {
            File dir = new File(ctx.getFilesDir(), "tts");
            if (!dir.exists()) dir.mkdirs();

            File model = new File(dir, "model.onnx");
            File tokens = new File(dir, "tokens.txt");
            File lexicon = new File(dir, "lexicon.txt");
            File dateFst = new File(dir, "date.fst");
            File numberFst = new File(dir, "number.fst");
            File phoneFst = new File(dir, "phone.fst");

            copyAsset("tts/model.onnx", model);
            copyAsset("tts/tokens.txt", tokens);
            copyAsset("tts/lexicon.txt", lexicon);
            copyAsset("tts/date.fst", dateFst);
            copyAsset("tts/number.fst", numberFst);
            copyAsset("tts/phone.fst", phoneFst);

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

            tts = new OfflineTts(null, config);
            ready = true;
            Log.i(TAG, "sherpa TTS ready: " + tts.sampleRate() + " Hz");
            String p;
            synchronized (this) {
                p = pending;
                pending = null;
            }
            if (p != null) speak(p);
        } catch (Throwable t) {
            Log.w(TAG, "sherpa TTS init failed", t);
        }
    }

    @Override
    public void speak(String text) {
        if (text == null || text.trim().isEmpty()) return;
        if (!ctx.getSharedPreferences("eye", Context.MODE_PRIVATE).getBoolean("tts", true)) return;
        String t = shorten(text);
        if (!ready) {
            synchronized (this) { pending = t; }
            return;
        }
        worker.execute(() -> synth(t));
    }

    private void synth(String text) {
        if (closed) return;
        OfflineTts engine = tts;
        if (engine == null) return;
        File wav = null;
        try {
            GeneratedAudio audio = engine.generate(text, 0, 1.0f);
            if (audio == null) return;
            wav = File.createTempFile("feiyu-tts-", ".wav", ctx.getCacheDir());
            if (!audio.save(wav.getAbsolutePath())) {
                Log.w(TAG, "sherpa TTS save failed");
                return;
            }
            Log.i(TAG, "sherpa TTS wav " + wav.length() + " bytes");
            play(wav);
        } catch (Throwable t) {
            Log.w(TAG, "sherpa TTS synth failed", t);
        } finally {
            if (wav != null) {
                //noinspection ResultOfMethodCallIgnored
                wav.delete();
            }
        }
    }

    private void play(File wav) throws Exception {
        final CountDownLatch done = new CountDownLatch(1);
        final MediaPlayer p = new MediaPlayer();
        p.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build());
        p.setDataSource(wav.getAbsolutePath());
        p.setOnCompletionListener(mp -> done.countDown());
        p.setOnErrorListener((mp, what, extra) -> {
            done.countDown();
            return false;
        });
        p.prepare();
        player = p;
        p.start();
        try {
            done.await(120, TimeUnit.SECONDS);
        } finally {
            try { p.stop(); } catch (Throwable ignored) { }
            try { p.release(); } catch (Throwable ignored) { }
            if (player == p) player = null;
        }
    }

    private void copyAsset(String name, File target) throws Exception {
        if (target.exists() && target.length() > 0) return;
        InputStream in;
        try {
            in = ctx.getAssets().open(name);
        } catch (FileNotFoundException first) {
            try {
                in = ctx.getAssets().open("assets/" + name);
            } catch (FileNotFoundException second) {
                File external = new File("/sdcard/DeepSeekGirl/tts", new File(name).getName());
                if (!external.exists() || external.length() <= 0) throw second;
                Log.i(TAG, "TTS model from " + external.getAbsolutePath());
                in = new FileInputStream(external);
            }
        }
        FileOutputStream out = new FileOutputStream(target);
        try {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            out.flush();
        } finally {
            try { in.close(); } catch (Throwable ignored) { }
            try { out.close(); } catch (Throwable ignored) { }
        }
    }

    private String shorten(String text) {
        String t = String.valueOf(text == null ? "" : text).replace("\n", " ").trim();
        int max = 50;
        if (t.length() <= max) return t;
        int cut = max;
        for (int i = Math.min(max, t.length() - 1); i > 20; i--) {
            char c = t.charAt(i);
            if (c == '。' || c == '！' || c == '？' || c == '，' || c == '.' || c == '!' || c == '?') {
                cut = i + 1;
                break;
            }
        }
        return t.substring(0, cut) + "…";
    }

    @Override
    public void stop() {
        MediaPlayer p = player;
        if (p != null) {
            try { p.stop(); } catch (Throwable ignored) { }
        }
    }

    @Override
    public void shutdown() {
        closed = true;
        stop();
        worker.shutdownNow();
        OfflineTts engine = tts;
        tts = null;
        ready = false;
        if (engine != null) {
            try { engine.release(); } catch (Throwable ignored) { }
        }
    }
}
