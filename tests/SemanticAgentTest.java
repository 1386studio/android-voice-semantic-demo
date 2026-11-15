import com.example.voicesemantic.SemanticAgent;
import java.util.List;
import java.util.Map;

public final class SemanticAgentTest {
    private static void check(boolean ok,String what) {
        if(!ok) throw new AssertionError(what);
    }
    @SuppressWarnings("unchecked")
    private static List<Map<String,Object>> actions(SemanticAgent.Result r) {
        Map<String,Object> semantic=(Map<String,Object>)r.message.get("semantic");
        return (List<Map<String,Object>>)semantic.get("operations");
    }
    @SuppressWarnings("unchecked")
    private static Map<String,Object> parameters(Map<String,Object> action){
        return (Map<String,Object>) action.get("parameters");
    }
    public static void main(String[] args){
        SemanticAgent a=new SemanticAgent();
        SemanticAgent.Result r=a.interpret("查询三号终端电量，并把结果发给当前工作组","manual_text");
        check(r.actionCount==2,"双步骤解析");
        check(r.requiresConfirmation,"对外发布必须确认");
        check("device.status.query".equals(actions(r).get(0).get("action")),"电量查询动作");
        check("terminal-003".equals(parameters(actions(r).get(0)).get("device_id")),"中文设备编号解析");
        check("group.message.publish".equals(actions(r).get(1).get("action")),"发布动作");
        check("not_executed".equals(((Map<?,?>)r.message.get("execution")).get("state")),"不实际执行");

        r=a.interpret("查询WH001仓库的饮用水库存","voice");
        check(r.actionCount==1,"库存查询");
        check("WH001".equals(parameters(actions(r).get(0)).get("warehouse_id")),"仓库提取");
        check("water".equals(parameters(actions(r).get(0)).get("material_type")),"物资提取");

        r=a.interpret("打开地图，显示当前位置","voice");
        check(r.actionCount==2,"地图+位置两个操作");

        r=a.interpret("设备七号电量多少","voice");
        check(r.actionCount==1,"设备七号识别");
        check("terminal-007".equals(parameters(actions(r).get(0)).get("device_id")),"设备七号规范化");

        r=a.interpret("请删除所有任务","voice");
        check(r.actionCount==0,"高风险写入不生成执行操作");
        check(r.requiresConfirmation,"高风险指令必须确认");

        r=a.interpret("今天天气如何","voice");
        check(r.actionCount==0 && !r.warnings.isEmpty(),"未知意图不得臆造工具");

        System.out.println("SemanticAgentTest PASSED (6 cases)");
    }
}
