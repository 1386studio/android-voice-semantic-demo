package com.example.voicesemantic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class DomainGrammarTest {
    private static int checks=0;
    private static void ok(boolean condition,String message){
        checks++; if(!condition)throw new AssertionError(message);
    }
    private static void invalid(List<String> items,String message){
        checks++;try {DomainGrammar.compile(items,true);throw new AssertionError(message);}
        catch(IllegalArgumentException expected) { /* expected */ }
    }
    public static void main(String[] args){
        String json=DomainGrammar.compile(Arrays.asList("打开地图","显示当前位置","打开地图"),true);
        ok("[\"打开地图\",\"显示当前位置\",\"[unk]\"]".equals(json),"stable dedupe / unknown");
        ok("[\"打开地图\"]".equals(DomainGrammar.compile(Collections.singletonList("打开地图"),false)),"strict grammar");
        ok(DomainGrammar.validate(Arrays.asList("  查询  ","查询")).size()==1,"trim");
        ok(DomainGrammar.compile(Collections.singletonList("说\"你好\""),true).contains("\\\""),"escape quote");
        ok(DomainGrammar.compile(Collections.singletonList("路径\\测试"),true).contains("\\\\"),"escape backslash");
        invalid(Collections.emptyList(),"empty");
        invalid(Collections.singletonList(" "),"blank");
        invalid(Collections.singletonList("[unk]"),"reserved");
        invalid(Collections.singletonList("包含\n换行"),"control chars");
        invalid(Collections.singletonList(String.join("",Collections.nCopies(49,"号"))),"too long");
        List<String> many=new ArrayList<>();
        for(int i=0;i<161;i++)many.add("测试"+i);
        invalid(many,"too many");
        ok(DomainGrammar.compile(Arrays.asList("设备","终端"),true).startsWith("["),"json array output");
        System.out.println("DomainGrammarTest PASSED ("+checks+" cases)");
    }
}
