package com.example.voicesemantic;

import android.content.Context;
import com.google.mediapipe.tasks.genai.llminference.LlmInference;
import java.io.File;

/** Fully on-device LLM. Model must be explicitly imported by user; no cloud API. */
final class LocalLlmAgent implements AutoCloseable {
    private LlmInference engine;
    private String loadedModel="";
    synchronized boolean ready(){return engine!=null;}
    synchronized String modelName(){return loadedModel;}
    synchronized void load(Context appContext,File file){
        close();
        if(file==null || !file.isFile() || file.length()==0)
            throw new IllegalArgumentException("请选择有效的本地MediaPipe .task模型");
        LlmInference.LlmInferenceOptions options=LlmInference.LlmInferenceOptions.builder()
            .setModelPath(file.getAbsolutePath())
            .setMaxTokens(512)
            .setTopK(1)
            .setTemperature(0.1f)
            .build();
        engine=LlmInference.createFromOptions(appContext,options);
        loadedModel=file.getName();
    }
    synchronized String infer(String recognizedText){
        if(engine==null)throw new IllegalStateException("尚未装载本地小模型");
        String prompt="你是离线语义解析器，严格返回一个JSON对象，不要解释和markdown。"+
            "允许action只有device.status.query、device.history.query、inventory.query、"+
            "task.list、map.open、location.show、group.message.publish。"+
            "输出格式：{\"intent\":\"...\",\"operations\":[{\"action\":\"...\",\"parameters\":{...}}],"+
            "\"needs_clarification\":false}。无法确定则operations=[]且needs_clarification=true。"+
            "不得创造不存在的设备、编号、数量或接收人。只生成语义，不执行操作。"+
            "\n用户语音转写："+recognizedText+"\nJSON：";
        return engine.generateResponse(prompt);
    }
    @Override public synchronized void close(){
        if(engine!=null){engine.close();engine=null;}
        loadedModel="";
    }
}
