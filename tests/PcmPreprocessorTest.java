package com.example.voicesemantic;
/** 纯JVM验证48k/16k重采样样本数及抗混叠，不依赖Android SDK。 */
public final class PcmPreprocessorTest {
    private static double measureOutputRms(int frequency) {
        PcmPreprocessor dsp = new PcmPreprocessor();
        short[] samples=new short[480];
        short[] dest=new short[162];
        double energy=0; int count=0;
        for(int block=0;block<100;block++){
            for(int i=0;i<480;i++) {
                int sample=block*480+i;
                samples[i]=(short)Math.round(10000*Math.sin(2*Math.PI*frequency*sample/48000.0));
            }
            int n=dsp.process(samples,480,dest);
            if(n!=160)throw new AssertionError("每480输入样本应输出160，实际:"+n);
            if(block>5)for(int i=0;i<n;i++){
                energy+=(double)dest[i]*dest[i]; count++;
            }
        }
        return Math.sqrt(energy/count);
    }
    public static void main(String[] args){
        double oneK=measureOutputRms(1000);
        double twelveK=measureOutputRms(12000);
        if(oneK<4000 || oneK>9000)
            throw new AssertionError("1kHz通带异常："+oneK);
        if(twelveK>oneK*0.04)
            throw new AssertionError("12kHz应被低通显著抑制："+twelveK);
        System.out.printf("PcmPreprocessorTest PASSED (1kHz rms=%.1f, 12kHz rms=%.1f)%n",oneK,twelveK);
    }
}
