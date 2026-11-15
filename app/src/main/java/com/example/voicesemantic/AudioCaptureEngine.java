package com.example.voicesemantic;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.audiofx.NoiseSuppressor;
import android.os.SystemClock;
import org.json.JSONObject;
import org.vosk.Model;
import org.vosk.Recognizer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 48kHz/mono/16-bit AudioRecord -> 10ms DSP块 -> 16kHz Vosk；
 * ASR独立线程，音频线程不做模型推理，队列上限防止内存无限增长。
 */
final class AudioCaptureEngine {
    interface Listener {
        void onAudioStatus(String message);
        void onLevel(int percentage, String detail);
        void onPartial(String text);
        void onFinal(String text);
        void onError(String message);
    }
    private static final int SAMPLE_RATE=48000, FRAME_SIZE=480;
    private static final short[] END=new short[0];
    private final Model model;
    private final Listener listener;
    private final AtomicBoolean running=new AtomicBoolean(false);
    private final ArrayBlockingQueue<short[]> queue=new ArrayBlockingQueue<>(220);
    private volatile boolean captureFinished=false;
    private volatile boolean overrun=false;
    private volatile AudioRecord activeRecord;
    private Thread captureThread, recognitionThread;

    AudioCaptureEngine(Model model, Listener listener){this.model=model;this.listener=listener;}

    synchronized boolean start(boolean enableSystemNs){
        if(running.get())return false;
        if((captureThread!=null&&captureThread.isAlive()) ||
           (recognitionThread!=null&&recognitionThread.isAlive())){
            listener.onError("上一轮语音流尚未完成，请稍后重试");
            return false;
        }
        queue.clear(); captureFinished=false; overrun=false;
        int min=AudioRecord.getMinBufferSize(SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);
        if(min<=0){listener.onError("当前设备不支持48kHz单通道录音");return false;}
        AudioRecord rec=null;
        try{
            rec=new AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
                .setAudioFormat(new AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO).build())
                .setBufferSizeInBytes(Math.max(min,FRAME_SIZE*2*8))
                .build();
            if(rec.getState()!=AudioRecord.STATE_INITIALIZED)
                throw new IllegalStateException("AudioRecord初始化失败");
            rec.startRecording();
            if(rec.getRecordingState()!=AudioRecord.RECORDSTATE_RECORDING)
                throw new IllegalStateException("麦克风可能被其他应用占用");
            activeRecord=rec;
            running.set(true);
            final AudioRecord captured=rec;
            final boolean ns=enableSystemNs;
            recognitionThread=new Thread(this::recognizeLoop,"offline-asr");
            captureThread=new Thread(()->captureLoop(captured,ns),"audio-capture");
            recognitionThread.start(); captureThread.start();
            return true;
        }catch(Exception e){
            running.set(false);
            if(rec!=null){try{rec.release();}catch(Exception ignored){}}
            activeRecord=null;
            listener.onError("录音启动失败："+e.getMessage());
            return false;
        }
    }

    void stop(){running.set(false);}
    boolean isRunning(){return running.get();}

    private void captureLoop(AudioRecord record,boolean nsRequested){
        NoiseSuppressor suppressor=null;
        try{
            boolean nsActive=false;
            if(NoiseSuppressor.isAvailable()){
                try{
                    suppressor=NoiseSuppressor.create(record.getAudioSessionId());
                    if(suppressor!=null){
                        // VOICE_RECOGNITION可能默认已有音效，显式读取实际状态。
                        suppressor.setEnabled(nsRequested);
                        nsActive=suppressor.getEnabled();
                    }
                }catch(Exception ex){ listener.onAudioStatus("系统降噪开关不可用，继续原始采音"); }
            }
            listener.onAudioStatus("采音中 | 48kHz PCM | 系统NS："+(nsActive?"已启用":"未启用/设备不支持"));
            PcmPreprocessor dsp=new PcmPreprocessor();
            short[] input=new short[FRAME_SIZE];
            short[] downsampled=new short[FRAME_SIZE/3+4];
            long lastLevel=0;
            int dropped=0;
            while(running.get()){
                int read=record.read(input,0,input.length,AudioRecord.READ_BLOCKING);
                if(read<0)throw new IllegalStateException("AudioRecord读取错误："+read);
                if(read==0)continue;
                long now=SystemClock.uptimeMillis();
                if(now-lastLevel>=90){
                    double sq=0.0; int peak=0;
                    for(int i=0;i<read;i++){
                        sq+=(double)input[i]*input[i];
                        peak=Math.max(peak,Math.abs((int)input[i]));
                    }
                    double db=20*Math.log10(Math.max(1,Math.sqrt(sq/read))/32768.0);
                    int level=(int)Math.round(Math.min(100,Math.max(0,(db+65)/65*100)));
                    listener.onLevel(level,"峰值："+peak+" / 32768"+(peak>=32500?" · 削波风险":""));
                    lastLevel=now;
                }
                int n=dsp.process(input,read,downsampled);
                if(n>0){
                    short[] packet=new short[n];
                    System.arraycopy(downsampled,0,packet,0,n);
                    if(!queue.offer(packet)){
                        queue.poll(); // 优先保持实时性；不让队列无界增长。
                        if(!queue.offer(packet))dropped++;
                        overrun=true;
                    }
                }
            }
            if(overrun)listener.onAudioStatus("注意：ASR处理速度不足，曾丢弃音频片段；请减少后台负载");
        }catch(Exception e){listener.onError("采集异常："+e.getMessage());}
        finally{
            running.set(false);
            if(suppressor!=null){try{suppressor.release();}catch(Exception ignored){}}
            try{record.stop();}catch(Exception ignored){}
            record.release(); activeRecord=null;
            captureFinished=true;
            // 通知ASR线程消费最后数据，不能在队列满时阻塞UI。
            if(!queue.offer(END)){
                queue.poll();queue.offer(END);
            }
        }
    }

    private void recognizeLoop(){
        StringBuilder committed=new StringBuilder();
        try(Recognizer recognizer=new Recognizer(model,16000.0f)){
            long lastPartialAt=0;
            while(true){
                short[] chunk=queue.poll(300, TimeUnit.MILLISECONDS);
                if(chunk==null){if(captureFinished&&queue.isEmpty())break;continue;}
                if(chunk.length==0)break;
                if(recognizer.acceptWaveForm(chunk,chunk.length)){
                    String part=extract(recognizer.getResult(),"text");
                    if(!part.isEmpty()){
                        if(committed.length()>0)committed.append('，');
                        committed.append(part);
                        listener.onPartial(committed.toString());
                    }
                }else{
                    long now=SystemClock.uptimeMillis();
                    if(now-lastPartialAt>160){
                        String partial=extract(recognizer.getPartialResult(),"partial");
                        if(!partial.isEmpty())listener.onPartial(committed+" "+partial);
                        lastPartialAt=now;
                    }
                }
            }
            String tail=extract(recognizer.getFinalResult(),"text");
            if(!tail.isEmpty()){
                if(committed.length()>0)committed.append('，');
                committed.append(tail);
            }
            listener.onFinal(committed.toString().trim());
        }catch(Exception e){listener.onError("离线识别异常："+e.getMessage());}
    }

    private static String extract(String json,String key){
        try{return new JSONObject(json).optString(key,"").trim();}
        catch(Exception ignored){return "";}
    }
}
