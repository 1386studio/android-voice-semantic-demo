package com.example.voicesemantic;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

/** Writes 16-bit little-endian PCM WAV, finalizing the header even on early stop. */
public final class PcmWavWriter implements Closeable {
    private final RandomAccessFile file;
    private final int sampleRate;
    private final int channels;
    private long samples = 0;
    private boolean closed = false;

    public PcmWavWriter(File output, int sampleRate, int channels) throws IOException {
        this.sampleRate=sampleRate; this.channels=channels;
        if(output.getParentFile()!=null && !output.getParentFile().exists() && !output.getParentFile().mkdirs())
            throw new IOException("无法创建录音目录");
        file=new RandomAccessFile(output,"rw");
        file.setLength(0); file.write(new byte[44]);
    }
    public void append(short[] pcm,int count) throws IOException {
        if(closed)throw new IOException("录音文件已关闭");
        if(count<0 || count>pcm.length)throw new IllegalArgumentException("PCM长度无效");
        byte[] bytes=new byte[count*2];
        for(int i=0;i<count;i++){
            bytes[i*2]=(byte)(pcm[i]&255);
            bytes[i*2+1]=(byte)((pcm[i]>>>8)&255);
        }
        file.write(bytes);
        samples+=count;
    }
    private void le16(int v) throws IOException {file.write(v&255);file.write((v>>>8)&255);}
    private void le32(long v) throws IOException {for(int i=0;i<4;i++)file.write((int)(v>>>(8*i))&255);}
    @Override public void close() throws IOException {
        if(closed)return;
        closed=true;
        try {
            long data=samples*2;
            if(data>0xFFFFFFFFL-36)throw new IOException("WAV文件超过4GB限制");
            file.seek(0);
            file.writeBytes("RIFF");le32(data+36);
            file.writeBytes("WAVEfmt ");le32(16);le16(1);le16(channels);
            le32(sampleRate);le32((long)sampleRate*channels*2);
            le16(channels*2);le16(16);
            file.writeBytes("data");le32(data);
        } finally {file.close();}
    }
}
