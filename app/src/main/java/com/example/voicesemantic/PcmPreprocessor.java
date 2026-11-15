package com.example.voicesemantic;

/** 48kHz PCM -> 16kHz PCM，含80Hz高通和63-tap低通FIR抗混叠。线程独占。 */
final class PcmPreprocessor {
    private static final int TAPS = 63;
    private final double[] coefficients = new double[TAPS];
    private final double[] history = new double[TAPS];
    private int position = 0, index = 0;
    private double previousInput = 0.0, previousHighPass = 0.0;

    PcmPreprocessor(){
        // 截止频率7kHz，适合16kHz ASR奈奎斯特频率8kHz。
        double normalizedCutoff=7000.0/48000.0;
        double sum=0;
        for(int i=0;i<TAPS;i++){
            double d=i-(TAPS-1)/2.0;
            double sinc=(d==0)?2*normalizedCutoff:
                Math.sin(2*Math.PI*normalizedCutoff*d)/(Math.PI*d);
            double window=0.54-0.46*Math.cos(2*Math.PI*i/(TAPS-1));
            coefficients[i]=sinc*window;
            sum+=coefficients[i];
        }
        for(int i=0;i<TAPS;i++) coefficients[i]/=sum;
    }

    /** out 至少为 (count + 2) / 3 + 2；返回输出样本数。 */
    int process(short[] src,int count,short[] out){
        int written=0;
        for(int i=0;i<count;i++){
            double x=src[i];
            // 简单单极高通，抑制麦克风低频振动；不使用强硬门限避免吞字。
            double high=x-previousInput+0.9896*previousHighPass;
            previousInput=x; previousHighPass=high;
            history[position]=high;
            position=(position+1)%TAPS;
            if(index++ % 3 != 0) continue;
            double y=0;
            int hist=position;
            for(int t=0;t<TAPS;t++){
                if(--hist<0)hist=TAPS-1;
                y+=coefficients[t]*history[hist];
            }
            out[written++]=(short)Math.max(-32768,Math.min(32767,Math.round(y)));
        }
        return written;
    }
}
