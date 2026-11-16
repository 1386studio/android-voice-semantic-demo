package com.example.voicesemantic;
public final class SpeechChunkerTest {
    private static void check(boolean ok,String msg){if(!ok)throw new AssertionError(msg);}
    public static void main(String[] args){
        SpeechChunker c=new SpeechChunker();
        short[] silence=new short[16000];
        check(c.accept(silence,silence.length).isEmpty(),"纯静音不得识别");
        check(c.finish().isEmpty(),"末尾静音不得输出");
        c=new SpeechChunker();
        short[] speech=new short[16000];
        for(int i=0;i<speech.length;i++)speech[i]=i%2==0?(short)1500:(short)-1500;
        check(c.accept(speech,speech.length).isEmpty(),"短句进入缓冲");
        short[] quiet=new short[16000];
        check(c.accept(quiet,quiet.length).size()==1,"静音分界后交出一句");
        check(c.finish().isEmpty(),"尾部不重复输出");
        c=new SpeechChunker();
        short[] longSpeech=new short[16000*11];
        java.util.Arrays.fill(longSpeech,(short)1200);
        check(c.accept(longSpeech,longSpeech.length).size()>=1,"9秒上限强制分段");
        check(c.finish().size()==1,"保留结尾未完成段");
        System.out.println("SpeechChunkerTest PASSED");
    }
}
