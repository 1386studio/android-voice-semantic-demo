package com.example.voicesemantic;

import android.content.Context;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/** Deploy bundled SenseVoice INT8 model to app-private files, no network at runtime. */
final class ModelManager {
    static final String ASSET_ROOT="asr/sensevoice";
    static final String INSTALL_ROOT="sherpa-sensevoice-int8-2024-07-17";
    static File ensureInstalled(Context context) throws IOException {
        File dir=new File(context.getFilesDir(), INSTALL_ROOT);
        File model=new File(dir,"model.int8.onnx");
        File tokens=new File(dir,"tokens.txt");
        if(model.isFile() && model.length()>100_000_000L && tokens.isFile() && tokens.length()>10_000L)
            return dir;
        if(!dir.exists()&&!dir.mkdirs())throw new IOException("无法创建模型目录");
        copy(context, "model.int8.onnx", model,100_000_000L);
        copy(context, "tokens.txt",tokens,10_000L);
        return dir;
    }
    static File ensureVadInstalled(Context context) throws IOException {
        File dir=new File(context.getFilesDir(),"sherpa-silero-vad-v1");
        File target=new File(dir,"silero_vad.onnx");
        if(target.isFile() && target.length()==643854L)return target;
        if(!dir.exists()&&!dir.mkdirs())throw new IOException("无法创建VAD模型目录");
        File tmp=new File(dir,"silero_vad.onnx.part");
        try {
            try(InputStream in=context.getAssets().open("asr/vad/silero_vad.onnx");
                FileOutputStream out=new FileOutputStream(tmp)){
                byte[] buf=new byte[65536];int n;
                while((n=in.read(buf))!=-1)out.write(buf,0,n);
                out.getFD().sync();
            }
            if(tmp.length()!=643854L)throw new IOException("Silero VAD模型大小异常");
            if(target.exists()&&!target.delete())throw new IOException("VAD旧模型无法删除");
            if(!tmp.renameTo(target))throw new IOException("无法安装Silero VAD模型");
            return target;
        }finally{if(tmp.exists())tmp.delete();}
    }
    private static void copy(Context context,String name,File target,long minimum) throws IOException {
        File tmp=new File(target.getParentFile(),name+".part");
        try {
            try(InputStream in=context.getAssets().open(ASSET_ROOT+"/"+name);
                FileOutputStream out=new FileOutputStream(tmp)){
                byte[] buf=new byte[1024*1024]; int n;
                while((n=in.read(buf))!=-1)out.write(buf,0,n);
                out.getFD().sync();
            }
            if(tmp.length()<minimum)throw new IOException(name+"长度异常");
            if(target.exists()&&!target.delete())throw new IOException("无法替换旧模型："+name);
            if(!tmp.renameTo(target))throw new IOException("模型移动失败："+name);
        }finally{if(tmp.exists())tmp.delete();}
    }
}
