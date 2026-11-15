package com.example.voicesemantic;

import org.json.JSONObject;
import org.vosk.Model;
import org.vosk.Recognizer;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/** Offline ASR re-decode of recorded 48kHz mono PCM16 WAV (for same-sample A/B comparison). */
final class WavAsrComparator {
    static String transcribe(Model model,File wav,boolean domainGrammar) throws Exception {
        if(wav==null||!wav.isFile())throw new IOException("WAV文件不存在");
        StringBuilder result=new StringBuilder();
        try(FileInputStream in=new FileInputStream(wav);
            Recognizer recognizer=domainGrammar?
                new Recognizer(model,16000,AudioCaptureEngine.GRAMMAR):new Recognizer(model,16000)) {
            byte[] header=new byte[44];
            if(in.read(header)!=44 || header[0]!='R' || header[8]!='W')
                throw new IOException("非预期PCM16 WAV格式");
            byte[] bytes=new byte[960];
            short[] chunk=new short[480];short[] pcm16=new short[170];
            PcmPreprocessor dsp=new PcmPreprocessor();
            int buffered=0;
            for(;;){
                int n=in.read(bytes,buffered,bytes.length-buffered);
                if(n<0)break;
                if(n==0)continue;
                buffered+=n;
                if(buffered<2)continue;
                int usable=buffered&~1;
                for(int i=0;i<usable/2;i++)
                    chunk[i]=(short)((bytes[i*2]&255) | ((bytes[i*2+1]&255)<<8));
                int count=dsp.process(chunk,usable/2,pcm16);
                if(count>0 && recognizer.acceptWaveForm(pcm16,count)){
                    String text=new JSONObject(recognizer.getResult()).optString("text","");
                    if(!text.isEmpty()){if(result.length()>0)result.append('，');result.append(text);}
                }
                buffered-=usable;
                if(buffered>0)bytes[0]=bytes[usable];
            }
            String tail=new JSONObject(recognizer.getFinalResult()).optString("text","");
            if(!tail.isEmpty()){if(result.length()>0)result.append('，');result.append(tail);}
        }
        return result.toString();
    }
}
