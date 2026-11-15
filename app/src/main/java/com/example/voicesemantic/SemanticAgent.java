package com.example.voicesemantic;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 纯本地、确定性语义Agent（不调用LLM，不执行业务动作）。
 * 通过显式规则提取意图、实体和有序操作；未知或含糊命令只能待澄清。
 * 无Android依赖，可用 javac 独立测试。
 */
public final class SemanticAgent {
    private static final Pattern DEVICE = Pattern.compile(
        "(?:第)?([0-9０-９零〇一二两三四五六七八九十]{1,4})号(?:终端|设备|节点)|(?:终端|设备|节点)(?:编号)?([0-9０-９]{1,4})|(?:终端|设备|节点)([0-9零〇一二两三四五六七八九十]{1,4})号"
    );
    private static final Pattern WAREHOUSE = Pattern.compile(
        "(?i)WH[0-9]{2,6}|(?:第)?([0-9零〇一二两三四五六七八九十]{1,4})号仓库"
    );
    private static final Pattern MINUTES = Pattern.compile(
        "(?:最近|过去)([0-9一二两三四五六七八九十]{1,4})分钟"
    );
    private static final Pattern GROUP = Pattern.compile("(当前工作组|当前群组|工作组|群组|群聊)");

    public static final class Result {
        public final Map<String,Object> message;
        public final int actionCount;
        public final boolean requiresConfirmation;
        public final List<String> warnings;
        private Result(Map<String,Object> m, int n, boolean c, List<String> warnings) {
            this.message=m; this.actionCount=n; this.requiresConfirmation=c; this.warnings=warnings;
        }
    }

    private static Map<String,Object> map(Object... values) {
        Map<String,Object> out = new LinkedHashMap<>();
        for (int i=0; i+1<values.length; i+=2) out.put((String)values[i],values[i+1]);
        return out;
    }

    private static void add(List<Map<String,Object>> actions, String action, Map<String,Object> params) {
        actions.add(map("step", actions.size()+1, "action", action, "parameters", params));
    }

    public Result interpret(String input, String inputMode) {
        String raw = input == null ? "" : input.trim();
        String normalized = raw.replaceAll("[\\s，。；;、！!？?]", "");
        List<String> warnings = new ArrayList<>();
        List<Map<String,Object>> operations = new ArrayList<>();
        Map<String,Object> entities = new LinkedHashMap<>();
        boolean requiresConfirmation = false;

        Matcher deviceMatcher=DEVICE.matcher(normalized);
        String deviceId = null;
        if (deviceMatcher.find()) {
            String digits = deviceMatcher.group(1) != null ? deviceMatcher.group(1) :
                (deviceMatcher.group(2) != null ? deviceMatcher.group(2) : deviceMatcher.group(3));
            int id = chineseNumber(digits);
            if (id > 0 && id <= 9999) {
                deviceId=String.format(Locale.US, "terminal-%03d", id);
                entities.put("device_id",deviceId);
            }
        }
        Matcher warehouseMatcher=WAREHOUSE.matcher(normalized);
        String warehouseId = null;
        if (warehouseMatcher.find()) {
            String matched=warehouseMatcher.group();
            if (matched.toUpperCase(Locale.ROOT).startsWith("WH")) {
                warehouseId=matched.toUpperCase(Locale.ROOT);
            } else if (warehouseMatcher.group(1)!=null) {
                int n=chineseNumber(warehouseMatcher.group(1));
                if(n>0) warehouseId=String.format(Locale.US,"WH%03d",n);
            }
            if (warehouseId!=null) entities.put("warehouse_id",warehouseId);
        }
        String material=null;
        if (has(normalized,"饮用水","水量","瓶装水")) material="water";
        else if (has(normalized,"油料","燃油","柴油")) material="fuel";
        else if (has(normalized,"食品","食物","口粮")) material="food";
        else if (has(normalized,"药品","药材")) material="medicine";
        if (material!=null) entities.put("material_type",material);

        boolean share = has(normalized,"发给","发送给","同步到","共享给","转发给","发到","发送到");
        boolean battery = has(normalized,"电量","电池","剩余电") && has(normalized,"查询","查看","看看","多少","显示");
        boolean status = has(normalized,"设备状态","终端状态","运行状态") && has(normalized,"查询","查看","显示","看看");
        boolean history = has(normalized,"变化","曲线","历史","趋势") && (deviceId!=null || has(normalized,"设备","终端"));
        boolean inventory = has(normalized,"库存","剩余物资","物资数量") && has(normalized,"查询","查看","显示","还有","多少");
        boolean task = has(normalized,"任务") && has(normalized,"查看","打开","列出","显示","查询","看看");
        boolean map = has(normalized,"地图") && has(normalized,"打开","显示","查看");
        boolean location = has(normalized,"当前位置","我的位置","本机位置","当前位置坐标");
        boolean writeOnly = has(normalized,"删除","清空","关闭设备","开启设备","控制设备","启动设备","停止设备");

        if (battery || status) {
            if (deviceId == null) warnings.add("缺少或无法识别设备编号，需要用户确认");
            else {
                String metric=battery ? "battery_level" : "status";
                add(operations,"device.status.query",map("device_id",deviceId,"metric",metric));
            }
        }
        if (history) {
            if (deviceId == null) warnings.add("历史查询缺少设备编号");
            else {
                int duration=10;
                Matcher m=MINUTES.matcher(normalized);
                if(m.find()) {
                    int value=chineseNumber(m.group(1));
                    if(value>0 && value<=1440) duration=value;
                    else warnings.add("无法确认查询时间范围，暂采用10分钟");
                }
                add(operations,"device.history.query",map("device_id",deviceId,"metric",
                    battery ? "battery_level" : "temperature", "duration_minutes",duration));
            }
        }
        if (inventory) {
            if (warehouseId==null) warnings.add("缺少或无法识别仓库编号");
            if (material==null) warnings.add("缺少或无法识别物资类别");
            if (warehouseId!=null && material!=null) add(operations,"inventory.query",
                map("warehouse_id",warehouseId,"material_type",material));
        }
        if (task) {
            String filter=has(normalized,"未完成","待办") ? "pending" : "latest";
            add(operations,"task.list",map("filter",filter));
        }
        if(map) add(operations,"map.open",map("layer","default"));
        if(location) add(operations,"location.show",map("source","device_location"));
        if(share) {
            requiresConfirmation=true;
            String groupId=GROUP.matcher(normalized).find() ? "current_group" : null;
            if (groupId==null) warnings.add("未识别目标工作组或接收者");
            if (operations.isEmpty()) warnings.add("共享前缺少可确认的数据来源");
            if(groupId!=null && !operations.isEmpty()) {
                add(operations,"group.message.publish",map("group_id",groupId,
                    "content_from","previous_result"));
                entities.put("group_ref",groupId);
                warnings.add("发布动作需确认接收组、内容和用户权限；当前仅生成语义，不执行");
            }
        }
        if(writeOnly) {
            requiresConfirmation=true;
            warnings.add("检测到可能改变设备或数据状态的词语：演示版不生成写操作");
        }
        if(operations.isEmpty() && warnings.isEmpty()) warnings.add("未匹配已支持的意图，需要补充语义规则或接入本地LLM");
        boolean valid = !operations.isEmpty() && warnings.isEmpty() && !requiresConfirmation;
        String mainIntent = operations.isEmpty()? "unknown" :
            (operations.size()>1 ? "composite" : (String)operations.get(0).get("action"));
        Map<String,Object> result=map(
            "schema","voice.semantic.v1",
            "message_id", UUID.randomUUID().toString(),
            "created_at",isoNow(),
            "source",map("device","android_local","input_mode",inputMode),
            "recognition",map("text",raw,"language","zh-CN"),
            "agent",map("engine","deterministic_rule_agent","engine_version","0.1.0", "llm_used",false),
            "semantic",map("intent",mainIntent,"entities",entities,"operations",operations),
            "validation",map("schema_ready",valid,"requires_confirmation",requiresConfirmation,
                "warnings",warnings),
            "execution",map("state","not_executed","reason","semantic_preview_only")
        );
        return new Result(result,operations.size(),requiresConfirmation,warnings);
    }
    private static boolean has(String text, String... terms) {
        for(String term:terms) if(text.contains(term)) return true;
        return false;
    }
    private static String isoNow() {
        SimpleDateFormat fmt=new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX",Locale.US);
        fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
        return fmt.format(new Date());
    }
    /** 支持1~99及逐位读出的阿拉伯数字/常用中文数字。 */
    static int chineseNumber(String s) {
        if(s==null || s.isEmpty()) return -1;
        String digits=s.replace('０','0').replace('１','1').replace('２','2').replace('３','3')
            .replace('４','4').replace('５','5').replace('６','6').replace('７','7').replace('８','8').replace('９','9');
        if(digits.matches("[0-9]+")) try {return Integer.parseInt(digits);}catch(NumberFormatException e){return -1;}
        String names="零一二三四五六七八九";
        digits=digits.replace('〇','零').replace('两','二');
        int ten=digits.indexOf('十');
        if(ten>=0) {
            String a=digits.substring(0,ten), b=digits.substring(ten+1);
            int decade=a.isEmpty() ? 1 : single(a.charAt(0),names);
            int tail=b.isEmpty()?0:single(b.charAt(0),names);
            return (decade<0||tail<0)? -1 : decade*10+tail;
        }
        int value=0;
        for(char c:digits.toCharArray()) {
            int n=single(c,names);
            if(n<0)return -1;
            value=value*10+n;
        }
        return value;
    }
    private static int single(char c,String names){return names.indexOf(c);}
}
