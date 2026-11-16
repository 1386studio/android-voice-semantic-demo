package com.example.voicesemantic;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

/** Small, Android-independent Vosk grammar compiler. Never accepts arbitrary commands as business actions. */
final class DomainGrammar {
    static final int MAX_PHRASES = 160;
    static final int MAX_PHRASE_CODEPOINTS = 48;
    private DomainGrammar() {}

    static List<String> validate(Collection<String> phrases) {
        if (phrases == null || phrases.isEmpty()) throw new IllegalArgumentException("领域词表不能为空");
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String raw : phrases) {
            if (raw == null) throw new IllegalArgumentException("短语不能为空");
            String phrase = raw.trim();
            if (phrase.isEmpty()) throw new IllegalArgumentException("词表包含空短语");
            if (phrase.equals("[unk]")) throw new IllegalArgumentException("[unk]由系统设置，不应写入短语列表");
            if (phrase.codePointCount(0, phrase.length()) > MAX_PHRASE_CODEPOINTS)
                throw new IllegalArgumentException("短语超过48个字符：" + phrase.substring(0, Math.min(12, phrase.length())));
            for (int i = 0; i < phrase.length(); i++) {
                if (Character.isISOControl(phrase.charAt(i)))
                    throw new IllegalArgumentException("短语包含换行或控制字符");
            }
            unique.add(phrase);
            if (unique.size() > MAX_PHRASES)
                throw new IllegalArgumentException("词表超过160条短语限制");
        }
        if (unique.isEmpty()) throw new IllegalArgumentException("领域词表不能为空");
        return new ArrayList<>(unique);
    }

    static String compile(Collection<String> phrases, boolean allowUnknown) {
        List<String> checked = validate(phrases);
        StringBuilder json = new StringBuilder("[");
        for (String phrase : checked) {
            if (json.length() > 1) json.append(',');
            appendQuoted(json, phrase);
        }
        if (allowUnknown) {
            if (json.length() > 1) json.append(',');
            appendQuoted(json, "[unk]");
        }
        return json.append(']').toString();
    }

    private static void appendQuoted(StringBuilder out, String s) {
        out.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' || c == '\\') out.append('\\');
            out.append(c);
        }
        out.append('"');
    }
}
