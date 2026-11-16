package com.example.voicesemantic;
import java.util.Arrays;
public final class ShortCommandHintsTest {
    private static void check(boolean ok,String msg){if(!ok)throw new AssertionError(msg);}
    public static void main(String[] args){
        java.util.List<String> phrases=Arrays.asList("查询三号终端的剩余电量","查看当前任务","查询四号终端的剩余电量");
        check("查询三号终端的剩余电量".equals(ShortCommandHints.suggest("查询三号终端的剩余电量",phrases)),"精确词典候选");
        check(ShortCommandHints.suggest("今天天气怎么样",phrases).isEmpty(),"词典外内容不得强行替换");
        check(ShortCommandHints.suggest("删掉所有任务",phrases).isEmpty(),"危险操作不得误配");
        System.out.println("ShortCommandHintsTest PASSED");
    }
}
