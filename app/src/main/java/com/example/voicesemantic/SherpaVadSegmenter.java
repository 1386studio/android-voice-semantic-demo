package com.example.voicesemantic;

import com.k2fsa.sherpa.onnx.SileroVadModelConfig;
import com.k2fsa.sherpa.onnx.TenVadModelConfig;
import com.k2fsa.sherpa.onnx.VadModelConfig;
import com.k2fsa.sherpa.onnx.Vad;
import com.k2fsa.sherpa.onnx.SpeechSegment;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** True neural VAD, not an RMS gate. One detector per audio stream; never share VAD state. */
final class SherpaVadSegmenter implements AutoCloseable {
    static final int SAMPLE_RATE = 16000;
    static final int WINDOW_SAMPLES = 512;       // 32 ms Silero 16k window
    static final float THRESHOLD = 0.50f;
    static final float MIN_SILENCE_SECONDS = 0.50f;
    static final float MIN_SPEECH_SECONDS = 0.25f;
    static final float MAX_SPEECH_SECONDS = 8.0f;
    private Vad vad;
    private int emittedSegments = 0;

    SherpaVadSegmenter(File modelFile) {
        if(modelFile == null || !modelFile.isFile() || modelFile.length() < 100_000L)
            throw new IllegalArgumentException("Silero VAD模型不存在或不完整");
        SileroVadModelConfig silero = new SileroVadModelConfig(
            modelFile.getAbsolutePath(), THRESHOLD, MIN_SILENCE_SECONDS,
            MIN_SPEECH_SECONDS, WINDOW_SAMPLES, MAX_SPEECH_SECONDS);
        VadModelConfig config = new VadModelConfig(
            silero, new TenVadModelConfig(), SAMPLE_RATE, 1, "cpu", false);
        vad = new Vad(null, config);
    }

    List<float[]> accept(short[] pcm, int count) {
        if(vad == null) throw new IllegalStateException("VAD已关闭");
        if(pcm == null || count < 0 || count > pcm.length)
            throw new IllegalArgumentException("无效PCM输入");
        if(count == 0) return new ArrayList<>();
        float[] samples = new float[count];
        for(int i=0; i<count; ++i) samples[i] = pcm[i]/32768.0f;
        vad.acceptWaveform(samples);
        return collect();
    }

    /** Flush is essential: without it, the last utterance can be lost on stop. */
    List<float[]> finish() {
        if(vad == null) throw new IllegalStateException("VAD已关闭");
        vad.flush();
        return collect();
    }

    boolean isSpeaking() { return vad != null && vad.isSpeechDetected(); }
    int segmentCount() { return emittedSegments; }

    private List<float[]> collect() {
        List<float[]> segments = new ArrayList<>();
        while(!vad.empty()) {
            SpeechSegment segment = vad.front();
            float[] samples = segment.getSamples();
            if(samples != null && samples.length > 0) {
                segments.add(samples);
                emittedSegments++;
            }
            vad.pop();
        }
        return segments;
    }

    @Override public void close() {
        if(vad != null) { vad.release(); vad=null; }
    }
}
