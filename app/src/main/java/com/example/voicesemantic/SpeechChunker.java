package com.example.voicesemantic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** 16kHz PCM16 segmentation with a silence boundary and bounded segment length.
 *  Not a neural VAD; boundaries/thresholds must be evaluated on representative devices.
 */
final class SpeechChunker {
    private static final int RATE=16000;
    private static final int FRAME=160;           // 10 ms
    private static final int MAX_SAMPLES=RATE*9;  // hard cap to avoid unbounded ASR latency
    private static final int SILENCE_SAMPLES=RATE*65/100; // 650ms
    private static final int MIN_SAMPLES=RATE*35/100; // 350ms
    private short[] buf=new short[RATE*2];
    private int used=0,quiet=0;
    private boolean heardSpeech=false;
    private final short[] tail=new short[FRAME];
    private int tailUsed=0;

    List<short[]> accept(short[] pcm,int count) {
        List<short[]> ready=new ArrayList<>();
        if (pcm==null||count<0||count>pcm.length) throw new IllegalArgumentException("invalid PCM chunk");
        for(int i=0;i<count;i++) {
            tail[tailUsed++]=pcm[i];
            if(tailUsed==FRAME) { processFrame(tail, FRAME, ready);tailUsed=0; }
        }
        return ready;
    }
    List<short[]> finish() {
        List<short[]> ready=new ArrayList<>();
        if(tailUsed>0){processFrame(tail,tailUsed,ready);tailUsed=0;}
        if(used>=MIN_SAMPLES && heardSpeech) ready.add(Arrays.copyOf(buf,used));
        reset();
        return ready;
    }
    private void processFrame(short[] frame,int len,List<short[]> ready){
        if(buf.length<used+len)buf=Arrays.copyOf(buf,Math.max(buf.length*2,used+len));
        System.arraycopy(frame,0,buf,used,len);used+=len;
        double sum=0;
        for(int i=0;i<len;i++)sum+=(double)frame[i]*frame[i];
        boolean speech=Math.sqrt(sum/len)>=220.0;
        if(speech){heardSpeech=true;quiet=0;}
        else quiet+=len;
        if(used>=MAX_SAMPLES || (heardSpeech && used>=MIN_SAMPLES && quiet>=SILENCE_SAMPLES)){
            int cut=used;
            // Keep 150ms of trailing silence to smooth the word boundary.
            if(quiet>=SILENCE_SAMPLES)cut=Math.max(0,used-(quiet-RATE*15/100));
            if(heardSpeech && cut>=MIN_SAMPLES)ready.add(Arrays.copyOf(buf,cut));
            reset();
        } else if(!heardSpeech && used>=RATE*2){
            // Silence-only input should not accumulate indefinitely.
            reset();
        }
    }
    private void reset(){used=0;quiet=0;heardSpeech=false;}
}
