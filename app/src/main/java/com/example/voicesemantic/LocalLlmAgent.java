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
            .build();
        engine=LlmInference.createFromOptions(appContext,options);
        loadedModel=file.getName();
    }
    private static final String ACTIONS =
        "device.status.query,device.history.query,inventory.query,task.list,"+
        "map.open,location.show,group.message.publish";

    private static final String FORMAT =
        "你是离线中文指令到语义JSON的转换器。只能返回一个JSON对象，不能有Markdown。\n"+
        "每个operations[].action必须逐字等于下列七个值之一：" + ACTIONS + "。\n"+
        "绝对禁止将action写为query、list、send、search等泛化动词。\n"+
        "JSON格式：{\"intent\":\"...\",\"operations\":[{\"action\":\"允许动作名\",\"parameters\":{}}],"+
        "\"needs_clarification\":false}。\n"+
        "参数示例：device.status.query需要device_id和metric；"+
        "device.history.query需要device_id、metric、duration_minutes；"+
        "inventory.query需要warehouse_id、material_type。\n"+
        "示例输入：查询三号终端电量。\n"+
        "示例输出：{\"intent\":\"device.status.query\",\"operations\":["+
        "{\"action\":\"device.status.query\",\"parameters\":{\"device_id\":\"terminal-003\",\"metric\":\"battery_level\"}}],"+
        "\"needs_clarification\":false}。\n"+
        "不得臆造设备、仓库、工作组或数量；不能确定时operations=[]且needs_clarification=true。"+
        "只生成预览，不执行操作。\n";

    synchronized String infer(String recognizedText){
        if(engine==null)throw new IllegalStateException("尚未装载本地小模型");
        return engine.generateResponse(FORMAT + "用户输入：" + recognizedText + "\n输出JSON：");
    }

    /** One bounded repair attempt after strict protocol validation fails. */
    synchronized String repair(String recognizedText, String previousOutput, String validationError){
        if(engine==null)throw new IllegalStateException("尚未装载本地小模型");
        String bad=previousOutput==null?"":previousOutput;
        if(bad.length()>1300)bad=bad.substring(0,1300);
        String problem=validationError==null?"协议验证未通过":validationError;
        if(problem.length()>180)problem=problem.substring(0,180);
        return engine.generateResponse(FORMAT+
            "前次JSON不符合动作协议，错误："+problem+"\n"+
            "前次输出（只用来定位错误，不要照搬非法action）："+bad+"\n"+
            "原始用户输入："+recognizedText+"\n"+
            "请修正JSON；所有action必须属于允许集合。只输出修正后的JSON：");
    }
    @Override public synchronized void close(){
        if(engine!=null){engine.close();engine=null;}
        loadedModel="";
    }
}
