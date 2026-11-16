package com.example.voicesemantic;

import com.k2fsa.sherpa.onnx.OfflineModelConfig;
import com.k2fsa.sherpa.onnx.OfflineRecognizer;
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig;
import com.k2fsa.sherpa.onnx.OfflineRecognizerResult;
import com.k2fsa.sherpa.onnx.OfflineSenseVoiceModelConfig;
import com.k2fsa.sherpa.onnx.OfflineStream;
import java.io.File;

/** sherpa-onnx v2.0.1 SenseVoice INT8: non-streaming segment ASR, 16kHz mono PCM16 input. */
final class SherpaAsrEngine implements AutoCloseable {
    static final int SAMPLE_RATE = 16000;
    private OfflineRecognizer recognizer;

    SherpaAsrEngine(File folder) {
        File model = new File(folder, "model.int8.onnx");
        File tokens = new File(folder, "tokens.txt");
        if (!model.isFile() || model.length() < 100_000_000L ||
            !tokens.isFile() || tokens.length() < 10_000L) {
            throw new IllegalArgumentException("SenseVoice模型缺失或不完整");
        }
        OfflineSenseVoiceModelConfig sv = new OfflineSenseVoiceModelConfig();
        sv.setModel(model.getAbsolutePath());
        sv.setLanguage("zh");
        sv.setUseInverseTextNormalization(true);
        OfflineModelConfig mc = new OfflineModelConfig();
        mc.setSenseVoice(sv);
        mc.setTokens(tokens.getAbsolutePath());
        mc.setNumThreads(2);
        mc.setProvider("cpu");
        OfflineRecognizerConfig conf = new OfflineRecognizerConfig();
        conf.setModelConfig(mc);
        conf.setDecodingMethod("greedy_search");
        recognizer = new OfflineRecognizer(null, conf);
    }

    /** One model is shared between live segments and same-sample WAV comparison. */
    synchronized String transcribe(short[] samples) {
        if (recognizer == null) throw new IllegalStateException("ASR引擎已关闭");
        if (samples == null || samples.length == 0) return "";
        float[] wave = new float[samples.length];
        for (int i = 0; i < samples.length; i++) wave[i] = samples[i] / 32768.0f;
        return transcribe(wave);
    }

    /** Accepts Silero VAD's original 16kHz floating point speech segment. */
    synchronized String transcribe(float[] wave) {
        if (recognizer == null) throw new IllegalStateException("ASR引擎已关闭");
        if (wave == null || wave.length == 0) return "";
        OfflineStream stream = recognizer.createStream();
        try {
            stream.acceptWaveform(wave, SAMPLE_RATE);
            recognizer.decode(stream);
            OfflineRecognizerResult result = recognizer.getResult(stream);
            return result == null || result.getText() == null ? "" : result.getText().trim();
        } finally {
            stream.release();
        }
    }
    @Override public synchronized void close() {
        if (recognizer != null) { recognizer.release(); recognizer = null; }
    }
}
