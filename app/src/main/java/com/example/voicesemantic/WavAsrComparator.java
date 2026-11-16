package com.example.voicesemantic;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/** Same 48kHz WAV -> FIR 16kHz -> same nonstreaming segmenter -> SenseVoice recognizer. */
final class WavAsrComparator {
    static String transcribe(SherpaAsrEngine model,File wav,File vadModel,boolean useNeuralVad) throws Exception {
        if(wav==null||!wav.isFile())throw new IOException("WAV文件不存在");
        StringBuilder text=new StringBuilder();
        SpeechChunker energy=useNeuralVad?null:new SpeechChunker();
        PcmPreprocessor dsp=new PcmPreprocessor();
        try(FileInputStream in=new FileInputStream(wav);
            SherpaVadSegmenter vad=useNeuralVad?new SherpaVadSegmenter(vadModel):null){
            byte[] header=new byte[44];
            int pos=0,n;
            while(pos<header.length && (n=in.read(header,pos,header.length-pos))>0)pos+=n;
            if(pos!=44 || header[0]!='R'||header[1]!='I'||header[2]!='F'||header[3]!='F'||
                header[8]!='W'||header[9]!='A'||header[10]!='V'||header[11]!='E')
                throw new IOException("WAV头无效");
            byte[] bytes=new byte[960];
            short[] frame=new short[480],resampled=new short[170];
            int carry=0;
            while(true){
                n=in.read(bytes,carry,bytes.length-carry);
                if(n<0)break;
                if(n==0)continue;
                int size=n+carry, valid=size&~1;
                for(int i=0;i<valid/2;i++)frame[i]=(short)((bytes[2*i]&255)|((bytes[2*i+1]&255)<<8));
                int count=dsp.process(frame,valid/2,resampled);
                if(vad!=null){
                    for(float[] segment:vad.accept(resampled,count))append(text,model.transcribe(segment));
                }else{
                    for(short[] segment:energy.accept(resampled,count))append(text,model.transcribe(segment));
                }
                carry=size-valid;
                if(carry>0)bytes[0]=bytes[valid];
            }
            if(vad!=null){
                for(float[] segment:vad.finish())append(text,model.transcribe(segment));
            }else{
                for(short[] segment:energy.finish())append(text,model.transcribe(segment));
            }
        }
        return text.toString();
    }
    private static void append(StringBuilder sb,String part) {
        if(part!=null&&!part.trim().isEmpty()){
            if(sb.length()>0)sb.append('，');sb.append(part.trim());
        }
    }
}
