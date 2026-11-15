package com.example.voicesemantic;

/** Per-session RNNoise state. Native processing uses 480 samples at 48kHz, PCM16 scale. */
final class RnnoiseBridge implements AutoCloseable {
    private static final boolean LOADED;
    static {
        boolean loaded=false;
        try {System.loadLibrary("rnnoise_jni"); loaded=true;}
        catch(UnsatisfiedLinkError e){android.util.Log.e("RNNoise","Native library unavailable",e);}
        LOADED=loaded;
    }
    private long handle;
    static boolean available(){return LOADED;}
    RnnoiseBridge(){
        if(!LOADED)throw new IllegalStateException("RNNoise原生库未能载入");
        handle=createNative();
        if(handle==0)throw new IllegalStateException("RNNoise初始化失败");
    }
    void process(short[] input,short[] output){
        if(handle==0)throw new IllegalStateException("RNNoise已关闭");
        if(input.length<480 || output.length<480)throw new IllegalArgumentException("需480个48kHz样本");
        processNative(handle,input,output);
    }
    @Override public void close(){if(handle!=0){destroyNative(handle);handle=0;}}
    private static native long createNative();
    private static native void processNative(long handle,short[] input,short[] output);
    private static native void destroyNative(long handle);
}
