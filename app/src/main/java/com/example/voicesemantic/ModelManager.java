package com.example.voicesemantic;

import android.content.Context;
import android.content.res.AssetManager;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/** 仅将随APK构建时打包的ASR模型复制至私有目录；运行期无下载。 */
final class ModelManager {
    private static final String ASSET_ROOT="model-cn";
    private static final String INSTALL_ROOT="vosk-small-cn-0.22";

    static File ensureInstalled(Context context) throws IOException {
        File dest=new File(context.getFilesDir(), INSTALL_ROOT);
        if(new File(dest,"am/final.mdl").isFile()) return dest;
        AssetManager am=context.getAssets();
        // 用必需权重文件判断是否包含真实模型，而非仅检查assets目录。
        try(InputStream ignored=am.open(ASSET_ROOT+"/am/final.mdl")) {
            // found
        }catch(IOException e){
            throw new IOException("缺少中文离线模型；请在构建电脑执行 python3 scripts/fetch_model.py，再重新构建APK。",e);
        }
        File temp=new File(context.getFilesDir(),INSTALL_ROOT+".installing");
        deleteRecursively(temp);
        if(!temp.mkdirs())throw new IOException("无法创建模型临时目录");
        copyTree(am,ASSET_ROOT,temp);
        if(!new File(temp,"am/final.mdl").isFile())
            throw new IOException("ASR模型缺少am/final.mdl");
        deleteRecursively(dest);
        if(!temp.renameTo(dest))throw new IOException("ASR模型安装目录重命名失败");
        return dest;
    }
    private static void copyTree(AssetManager assets,String assetPath,File target) throws IOException {
        String[] children=assets.list(assetPath);
        if(children==null)throw new IOException("无法读取资产: "+assetPath);
        if(children.length>0){
            if(!target.exists()&&!target.mkdirs()) throw new IOException("无法建立目录: "+target);
            for(String child:children)copyTree(assets,assetPath+"/"+child,new File(target,child));
        }else{
            try(InputStream input=assets.open(assetPath);
                FileOutputStream output=new FileOutputStream(target)){
                byte[] buffer=new byte[32768]; int n;
                while((n=input.read(buffer))!=-1)output.write(buffer,0,n);
            }
        }
    }
    private static void deleteRecursively(File f){
        if(f.isDirectory()){
            File[] children=f.listFiles();
            if(children!=null)for(File child:children)deleteRecursively(child);
        }
        if(f.exists())f.delete();
    }
}
