package com.example.voicesemantic;

import android.content.Context;
import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Loads an APK default lexicon or an explicitly imported, validated local JSON lexicon. */
final class DomainLexiconRepository {
    private static final String DEFAULT_ASSET = "asr/domain_lexicon.v1.json";
    private static final String CUSTOM_FILE = "asr/domain_lexicon.v1.json";
    private static final int MAX_JSON_BYTES = 65536;

    static final class Snapshot {
        final String name;
        final int phraseCount;
        final int groupCount;
        final String grammar;
        final java.util.List<String> phrases;
        final boolean custom;
        final boolean allowUnknown;
        Snapshot(String name, int phraseCount, int groupCount, String grammar,
                 java.util.List<String> phrases, boolean custom, boolean allowUnknown) {
            this.name=name;this.phraseCount=phraseCount;this.groupCount=groupCount;
            this.grammar=grammar;this.phrases=java.util.Collections.unmodifiableList(new java.util.ArrayList<>(phrases));this.custom=custom;this.allowUnknown=allowUnknown;
        }
        String summary() {
            return (custom ? "已导入词典" : "内置词典") + "：" + name +
                " · " + groupCount + "类 / " + phraseCount + "条" +
                (allowUnknown ? " · 允许词外表达" : " · 仅词典提示");
        }
    }

    static Snapshot load(Context context) throws IOException, JSONException {
        File file = customFile(context);
        if (file.exists()) {
            try (InputStream in = new FileInputStream(file)) {
                return parse(readLimited(in), true);
            } catch (Exception ex) {
                throw new IOException("自定义词典无效；请点“恢复内置词典”：" + ex.getMessage(), ex);
            }
        }
        try (InputStream in = context.getAssets().open(DEFAULT_ASSET)) {
            return parse(readLimited(in), false);
        }
    }

    static Snapshot importUri(Context context, Uri uri) throws Exception {
        byte[] incoming;
        try (InputStream in=context.getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IOException("无法读取JSON文件");
            incoming = readLimited(in);
        }
        Snapshot next = parse(incoming, true); // Reject before touching the previous custom lexicon.
        File target=customFile(context);
        if (!target.getParentFile().exists() && !target.getParentFile().mkdirs())
            throw new IOException("无法创建本地词典目录");
        File temporary = new File(target.getParentFile(), "domain_lexicon.pending.json");
        try {
            try (FileOutputStream out=new FileOutputStream(temporary)) {
                out.write(incoming);
                out.getFD().sync();
            }
            try {
                Files.move(temporary.toPath(), target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            if (temporary.exists()) temporary.delete();
        }
        return next;
    }

    static Snapshot reset(Context context) throws IOException, JSONException {
        File file=customFile(context);
        if (file.exists() && !file.delete()) throw new IOException("无法删除自定义词典");
        return load(context);
    }

    private static File customFile(Context context) {
        return new File(context.getFilesDir(), CUSTOM_FILE);
    }

    private static Snapshot parse(byte[] data, boolean custom) throws JSONException {
        JSONObject root=new JSONObject(new String(data, StandardCharsets.UTF_8));
        if (!"asr.domain_lexicon.v1".equals(root.optString("schema", "")))
            throw new JSONException("schema必须为asr.domain_lexicon.v1");
        String name=root.optString("name", "").trim();
        if (name.isEmpty() || name.length()>64) throw new JSONException("name不能为空且不能超过64字符");
        JSONArray groups=root.optJSONArray("groups");
        if (groups==null || groups.length()==0 || groups.length()>12)
            throw new JSONException("groups数量必须在1到12类之间");
        List<String> all=new ArrayList<>();
        Set<String> ids=new HashSet<>();
        for (int i=0;i<groups.length();i++) {
            JSONObject group=groups.optJSONObject(i);
            if (group==null) throw new JSONException("groups元素必须是对象");
            String id=group.optString("id", "");
            String label=group.optString("label", "");
            if (!id.matches("[a-z][a-z_]{1,31}") || !ids.add(id))
                throw new JSONException("分组id非法或重复：" + id);
            if (label.trim().isEmpty() || label.length()>32)
                throw new JSONException("分组标签不合法：" + id);
            JSONArray phrases=group.optJSONArray("phrases");
            if (phrases==null || phrases.length()==0 || phrases.length()>48)
                throw new JSONException("分组"+id+"短语必须在1到48条之间");
            for (int j=0;j<phrases.length();j++) {
                String phrase=phrases.optString(j, null);
                if (phrase==null || !(phrases.opt(j) instanceof String))
                    throw new JSONException("短语必须是字符串：" + id);
                all.add(phrase);
            }
        }
        try {
            List<String> checked=DomainGrammar.validate(all);
            boolean unknown=root.optBoolean("allow_unknown", true);
            return new Snapshot(name,checked.size(),groups.length(),
                DomainGrammar.compile(checked,unknown),checked,custom,unknown);
        } catch (IllegalArgumentException ex) {
            throw new JSONException(ex.getMessage());
        }
    }

    private static byte[] readLimited(InputStream in) throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        byte[] buffer=new byte[4096];
        int count=0,n;
        while ((n=in.read(buffer))!=-1) {
            count+=n;
            if (count>MAX_JSON_BYTES) throw new IOException("词典JSON超过64KB限制");
            out.write(buffer,0,n);
        }
        if (count==0) throw new IOException("JSON文件为空");
        return out.toByteArray();
    }
}
