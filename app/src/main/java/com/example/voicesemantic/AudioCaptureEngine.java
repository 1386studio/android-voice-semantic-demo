package com.example.voicesemantic;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.audiofx.NoiseSuppressor;
import android.os.Environment;
import android.os.SystemClock;



import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** 48kHz AudioRecord -> optional real RNNoise 480-sample frames -> 48kHz WAV x2 -> 16kHz Sherpa-ONNX SenseVoice (segment ASR). */
final class AudioCaptureEngine {
    interface Listener {
        void onAudioStatus(String message);
        void onLevel(int percentage,String detail);
        void onPartial(String text);
        void onFinal(String text);
        void onError(String message);
        void onSaved(File original,File enhanced,String pathInfo);
        void onVadState(boolean speaking,int segments);
    }
    static final int SAMPLE_RATE=48000, FRAME_SIZE=480;
    private static final int MAX_SECONDS=180;
    private static final short[] END=new short[0];
    private final Context context;
    private final SherpaAsrEngine model;
    private final File vadModel;
    private final Listener listener;
    private final AtomicBoolean running=new AtomicBoolean(false);
    private final ArrayBlockingQueue<short[]> queue=new ArrayBlockingQueue<>(2500);
    private volatile boolean captureFinished=false,overrun=false;
    private Thread captureThread,asrThread;

    AudioCaptureEngine(Context context,SherpaAsrEngine model,File vadModel,Listener listener){
        this.context=context.getApplicationContext();this.model=model;this.vadModel=vadModel;this.listener=listener;
    }
    synchronized boolean start(boolean enableSystemNs,boolean enableRnnoise,boolean useNeuralVad){
        if(running.get())return false;
        if((captureThread!=null&&captureThread.isAlive()) || (asrThread!=null&&asrThread.isAlive())){
            listener.onError("上一轮音频仍在处理");return false;
        }
        if(enableRnnoise&&!RnnoiseBridge.available()){
            listener.onError("RNNoise原生库不可用，无法启用神经降噪");return false;
        }
        queue.clear(); captureFinished=false;overrun=false;
        int min=AudioRecord.getMinBufferSize(SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);
        if(min<=0){listener.onError("设备不支持48kHz PCM录音");return false;}
        AudioRecord rec=null;NoiseSuppressor ns=null;
        try {
            AudioManager manager=(AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
            boolean supportsRaw=manager!=null && "true".equalsIgnoreCase(manager.getProperty(
                AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED));
            int source=supportsRaw ? MediaRecorder.AudioSource.UNPROCESSED : MediaRecorder.AudioSource.VOICE_RECOGNITION;
            rec=new AudioRecord.Builder().setAudioSource(source)
                .setAudioFormat(new AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO).build())
                .setBufferSizeInBytes(Math.max(min,FRAME_SIZE*2*8)).build();
            if(rec.getState()!=AudioRecord.STATE_INITIALIZED)throw new IllegalStateException("麦克风初始化失败");
            boolean nsEnabled=false;
            if(NoiseSuppressor.isAvailable()){
                ns=NoiseSuppressor.create(rec.getAudioSessionId());
                if(ns!=null){ns.setEnabled(enableSystemNs&&!enableRnnoise);nsEnabled=ns.getEnabled();}
            }
            // Capture the hardware/OS-processed stream, not physically unmodified ADC samples.
            rec.startRecording();
            if(rec.getRecordingState()!=AudioRecord.RECORDSTATE_RECORDING)
                throw new IllegalStateException("麦克风被占用或权限不足");
            running.set(true);
            final AudioRecord record=rec;
            final NoiseSuppressor effect=ns;
            final boolean systemNsActive=nsEnabled;
            final String rawMode=supportsRaw?"UNPROCESSED(请求)":"VOICE_RECOGNITION(后备)";
            asrThread=new Thread(()->recognizeLoop(useNeuralVad),"voice-asr");
            captureThread=new Thread(()->captureLoop(record,effect,rawMode,systemNsActive,enableRnnoise),"voice-capture");
            asrThread.start();captureThread.start();
            return true;
        }catch(Exception e){
            running.set(false);
            if(ns!=null)try{ns.release();}catch(Exception ignored){}
            if(rec!=null)try{rec.release();}catch(Exception ignored){}
            listener.onError("无法开始录音："+e.getMessage());return false;
        }
    }
    void stop(){running.set(false);}
    boolean isRunning(){return running.get();}

    private void captureLoop(AudioRecord record,NoiseSuppressor ns,String rawMode,boolean systemNsActive,boolean enableRnnoise){
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);
        PcmWavWriter rawWriter=null,enhancedWriter=null;
        RnnoiseBridge denoiser=null;
        File rawFile=null,enhancedFile=null;
        boolean success=false;
        try {
            File root=context.getExternalFilesDir(Environment.DIRECTORY_MUSIC);
            if(root==null)root=context.getFilesDir();
            File dir=new File(root,"voice-sessions");
            String session=new SimpleDateFormat("yyyyMMdd-HHmmss-SSS",Locale.US).format(new Date());
            rawFile=new File(dir,session+"-capture.wav");
            enhancedFile=new File(dir,session+"-enhanced.wav");
            rawWriter=new PcmWavWriter(rawFile,SAMPLE_RATE,1);
            enhancedWriter=new PcmWavWriter(enhancedFile,SAMPLE_RATE,1);
            if(enableRnnoise)denoiser=new RnnoiseBridge();
            listener.onAudioStatus("录音中 | "+rawMode+" | 系统NS:"+systemNsActive+" | RNNoise:"+enableRnnoise);
            PcmPreprocessor dsp=new PcmPreprocessor();
            short[] readBuffer=new short[FRAME_SIZE],frame=new short[FRAME_SIZE],processed=new short[FRAME_SIZE];
            short[] downsampled=new short[FRAME_SIZE/3+4];
            int frameFill=0;
            int slowFrames=0;long maxDenoiseNanos=0;
            long start=SystemClock.uptimeMillis(),lastMeter=0;
            while(running.get()){
                if(SystemClock.uptimeMillis()-start>MAX_SECONDS*1000L){
                    listener.onAudioStatus("达到180秒测试录音上限，自动停止");break;
                }
                int read=record.read(readBuffer,0,FRAME_SIZE,AudioRecord.READ_BLOCKING);
                if(read<0)throw new IllegalStateException("AudioRecord读取失败："+read);
                if(read==0)continue;
                rawWriter.append(readBuffer,read);
                long now=SystemClock.uptimeMillis();
                if(now-lastMeter>100){
                    double sum=0;for(int i=0;i<read;i++)sum+=(double)readBuffer[i]*readBuffer[i];
                    double db=20*Math.log10(Math.max(1,Math.sqrt(sum/read))/32768d);
                    listener.onLevel((int)Math.max(0,Math.min(100,(db+65)/65*100)),"dBFS="+db);
                    lastMeter=now;
                }
                for(int pos=0;pos<read;){
                    int n=Math.min(FRAME_SIZE-frameFill,read-pos);
                    System.arraycopy(readBuffer,pos,frame,frameFill,n);
                    pos+=n;frameFill+=n;
                    if(frameFill==FRAME_SIZE){
                        if(denoiser!=null){
                            long t=System.nanoTime();
                            denoiser.process(frame,processed);
                            long duration=System.nanoTime()-t;
                            maxDenoiseNanos=Math.max(maxDenoiseNanos,duration);
                            if(duration>10_000_000L)slowFrames++;
                        }else System.arraycopy(frame,0,processed,0,FRAME_SIZE);
                        enhancedWriter.append(processed,FRAME_SIZE);
                        feedAsr(dsp,downsampled,processed,FRAME_SIZE);
                        frameFill=0;
                    }
                }
            }
            if(frameFill>0){
                // Pad only for model invocation; do not write padded data into WAV.
                for(int i=frameFill;i<FRAME_SIZE;i++)frame[i]=0;
                if(denoiser!=null)denoiser.process(frame,processed);
                else System.arraycopy(frame,0,processed,0,FRAME_SIZE);
                enhancedWriter.append(processed,frameFill);
                feedAsr(dsp,downsampled,processed,frameFill);
            }
            if(enableRnnoise)listener.onAudioStatus(String.format(Locale.US,
                "RNNoise最大单帧计算耗时%.2fms；超过10ms帧数%d",maxDenoiseNanos/1e6,slowFrames));
            success=true;
        }catch(Exception e){listener.onError("录音/降噪失败："+e.getMessage());}
        finally{
            running.set(false);
            if(denoiser!=null)denoiser.close();
            if(rawWriter!=null)try{rawWriter.close();}catch(Exception e){listener.onError("原始WAV关闭失败");}
            if(enhancedWriter!=null)try{enhancedWriter.close();}catch(Exception e){listener.onError("增强WAV关闭失败");}
            if(ns!=null)try{ns.release();}catch(Exception ignored){}
            try{record.stop();}catch(Exception ignored){}
            record.release();
            captureFinished=true;
            if(!queue.offer(END)){queue.poll();queue.offer(END);overrun=true;}
            if(success && rawFile!=null && enhancedFile!=null){
                listener.onSaved(rawFile,enhancedFile,"采集源="+rawMode+"；系统NS="+systemNsActive+"；RNNoise="+enableRnnoise);
            }
            if(overrun)listener.onAudioStatus("ASR队列曾溢出：结果可能漏字");
        }
    }
    private void feedAsr(PcmPreprocessor dsp,short[] output,short[] processed,int count){
        int n=dsp.process(processed,count,output);
        if(n==0)return;
        short[] packet=new short[n];System.arraycopy(output,0,packet,0,n);
        if(!queue.offer(packet)){
            queue.poll();
            if(!queue.offer(packet))overrun=true;
            overrun=true;
        }
    }

    private void recognizeLoop(boolean useNeuralVad){
        StringBuilder committed=new StringBuilder();
        int completed=0;
        boolean lastSpeaking=false;
        if(useNeuralVad){
            try(SherpaVadSegmenter vad=new SherpaVadSegmenter(vadModel)){
                listener.onVadState(false,0);
                while(true){
                    short[] packet=queue.poll(300,TimeUnit.MILLISECONDS);
                    if(packet==null){if(captureFinished&&queue.isEmpty())break;continue;}
                    if(packet.length==0)break;
                    for(float[] segment:vad.accept(packet,packet.length)){
                        String phrase=model.transcribe(segment);
                        if(!phrase.isEmpty()){
                            append(committed,phrase);
                            listener.onPartial(committed.toString());
                        }
                    }
                    boolean speaking=vad.isSpeaking();
                    int count=vad.segmentCount();
                    if(speaking!=lastSpeaking || completed!=count){
                        listener.onVadState(speaking,count);
                        lastSpeaking=speaking;completed=count;
                    }
                }
                for(float[] segment:vad.finish()){
                    append(committed,model.transcribe(segment));
                }
                listener.onVadState(false,vad.segmentCount());
                listener.onFinal(committed.toString().trim());
            }catch(Exception | LinkageError e){
                running.set(false);
                listener.onError("Silero VAD/Sherpa-ONNX识别失败："+e.getMessage());
            }
        }else{
            SpeechChunker chunks=new SpeechChunker();
            listener.onVadState(false,-1);
            try{
                while(true){
                    short[] packet=queue.poll(300,TimeUnit.MILLISECONDS);
                    if(packet==null){if(captureFinished&&queue.isEmpty())break;continue;}
                    if(packet.length==0)break;
                    for(short[] segment:chunks.accept(packet,packet.length)){
                        String phrase=model.transcribe(segment);
                        if(!phrase.isEmpty()){
                            append(committed,phrase);
                            listener.onPartial(committed.toString());
                        }
                    }
                }
                for(short[] segment:chunks.finish())append(committed,model.transcribe(segment));
                listener.onFinal(committed.toString().trim());
            }catch(Exception e){running.set(false);listener.onError("能量分段识别失败："+e.getMessage());}
        }
    }
    private static void append(StringBuilder out,String phrase){
        if(phrase!=null && !phrase.trim().isEmpty()){
            if(out.length()>0)out.append('，');
            out.append(phrase.trim());
        }
    }
}
