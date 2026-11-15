package com.example.voicesemantic;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class ActionCanonicalizerTest {
    private static Set<String> keys(String... names) { return new HashSet<>(Arrays.asList(names)); }
    private static void match(String got,String expected){
        if(!expected.equals(got))throw new AssertionError("Expected "+expected+", got "+got);
    }
    private static void reject(String action,Set<String> keys){
        boolean rejected=false;
        try{ActionCanonicalizer.canonicalize(action,keys);}catch(IllegalArgumentException expected){rejected=true;}
        if(!rejected)throw new AssertionError("Failed to reject ambiguous action: "+action);
    }
    public static void main(String[] args){
        match(ActionCanonicalizer.canonicalize("device.status.query",Collections.emptySet()),"device.status.query");
        match(ActionCanonicalizer.canonicalize("query",keys("device_id","metric")),"device.status.query");
        match(ActionCanonicalizer.canonicalize("query",keys("device_id","metric","duration_minutes")),"device.history.query");
        match(ActionCanonicalizer.canonicalize("query",keys("warehouse_id","material_type")),"inventory.query");
        reject("query",Collections.emptySet());
        reject("query",keys("device_id"));
        reject("query",keys("device_id","metric","warehouse_id","material_type"));
        reject("delete",keys("device_id"));
        System.out.println("ActionCanonicalizerTest PASSED (8 cases)");
    }
}
