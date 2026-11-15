package com.example.voicesemantic;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.HashSet;
import java.util.Set;

/** Rejects unknown actions, dangerous writes and malformed JSON. Always preview only. */
final class LlmSemanticValidator {
    private static final Set<String> ALLOWED=new HashSet<>();
    static {
        for(String name:new String[]{"device.status.query","device.history.query","inventory.query",
            "task.list","map.open","location.show","group.message.publish"})ALLOWED.add(name);
    }
    static JSONObject normalize(String raw,String transcript) throws JSONException {
        if(raw==null)throw new JSONException("空模型输出");
        int a=raw.indexOf('{'),b=raw.lastIndexOf('}');
        if(a<0 || b<=a)throw new JSONException("模型没有返回JSON对象");
        JSONObject obj=new JSONObject(raw.substring(a,b+1));
        JSONArray actions=obj.optJSONArray("operations");
        if(actions==null)throw new JSONException("模型缺失operations数组");
        if(actions.length()>6)throw new JSONException("操作步骤超过6条限制");
        JSONArray safeActions=new JSONArray();
        boolean confirm=obj.optBoolean("needs_clarification",false);
        for(int i=0;i<actions.length();i++){
            JSONObject op=actions.optJSONObject(i);
            if(op==null)throw new JSONException("存在无效操作元素");
            String name=op.optString("action","");
            if(!ALLOWED.contains(name))throw new JSONException("非法动作类型："+name);
            JSONObject params=op.optJSONObject("parameters");
            if(params==null)params=new JSONObject();
            if(name.equals("group.message.publish"))confirm=true;
            if(name.startsWith("device.") && !params.has("device_id"))confirm=true;
            JSONObject safe=new JSONObject();
            safe.put("step",i+1);safe.put("action",name);safe.put("parameters",params);
            safeActions.put(safe);
        }
        if(safeActions.length()==0)confirm=true;
        JSONObject message=new JSONObject();
        message.put("schema","voice.semantic.v1");
        message.put("message_id",java.util.UUID.randomUUID().toString());
        message.put("created_at",new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX",java.util.Locale.US).format(new java.util.Date()));
        message.put("source",new JSONObject().put("device","android_local").put("input_mode","voice_or_text"));
        message.put("recognition",new JSONObject().put("text",transcript).put("language","zh-CN"));
        message.put("agent",new JSONObject().put("engine","mediapipe_on_device_llm").put("llm_used",true));
        message.put("semantic",new JSONObject().put("intent",obj.optString("intent","unknown"))
            .put("entities",new JSONObject()).put("operations",safeActions));
        message.put("validation",new JSONObject().put("schema_ready",!confirm)
            .put("requires_confirmation",confirm)
            .put("warnings",new JSONArray(confirm?new String[]{"待核查或需用户确认；仅展示、不执行"}:new String[]{})));
        message.put("execution",new JSONObject().put("state","not_executed")
            .put("reason","semantic_preview_only"));
        return message;
    }
}
