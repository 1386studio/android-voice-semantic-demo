package com.example.voicesemantic;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Canonicalizes only unambiguous model synonyms. Never broadens the operation allowlist. */
final class ActionCanonicalizer {
    private static final Set<String> ALLOWED = new HashSet<>(Arrays.asList(
        "device.status.query", "device.history.query", "inventory.query",
        "task.list", "map.open", "location.show", "group.message.publish"));

    private ActionCanonicalizer() {}

    static String canonicalize(String candidate, Set<String> parameterKeys) {
        String action = candidate == null ? "" : candidate.trim();
        if (ALLOWED.contains(action)) return action;
        if (!"query".equals(action) || parameterKeys == null) {
            throw new IllegalArgumentException("非法动作类型：" + action);
        }
        boolean device = parameterKeys.contains("device_id") && parameterKeys.contains("metric");
        boolean inventory = parameterKeys.contains("warehouse_id") && parameterKeys.contains("material_type");
        if (device && !inventory) {
            return parameterKeys.contains("duration_minutes")
                ? "device.history.query" : "device.status.query";
        }
        if (inventory && !device) return "inventory.query";
        // "query" without clear parameters, or with conflicting targets, is never guessed.
        throw new IllegalArgumentException("模糊动作query：必须明确设备状态/历史或库存查询参数");
    }
}
