package com.example.voicesemantic;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONObject;
import org.json.JSONException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Offline speech-to-semantics V0.4: capture/WAV A-B/RNNoise/SenseVoice/on-device LLM preview. */
public final class MainActivity extends Activity {
    private static final int MIC_PERMISSION=1001,IMPORT_LLM=1100,EXPORT_WAV=1101,IMPORT_LEXICON=1102;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final SemanticAgent fallback=new SemanticAgent();
    private final LocalLlmAgent llm=new LocalLlmAgent();
    private TextView modelStatus,audioStatus,transcriptView,semanticStatus,jsonView,llmStatus,recordingStatus,rawAsrView,cerStatus,lexiconStatus,asrModeStatus;
    private EditText manualInput,referenceInput;
    private Switch systemSwitch,rnSwitch,vadSwitch;
    private TextView vadStatus;
    private RadioGroup asrModeGroup;
    private Button importLexiconBtn,resetLexiconBtn;
    private volatile DomainLexiconRepository.Snapshot lexicon;
    private ProgressBar levelBar;
    private Button startBtn,stopBtn,playRaw,playEnhanced,exportRaw,exportEnhanced;
    private volatile SherpaAsrEngine model;
    private volatile File vadModel;
    private AudioCaptureEngine engine;
    private String lastJson="{}";
    private File lastRaw,lastEnhanced,exportSource;
    private String rawTranscript="",enhancedTranscript="";
    private MediaPlayer player;
    private volatile boolean destroyed=false;

    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        setContentView(R.layout.activity_main);
        modelStatus=findViewById(R.id.modelStatus);
        audioStatus=findViewById(R.id.audioStatus);
        transcriptView=findViewById(R.id.transcriptView);
        semanticStatus=findViewById(R.id.semanticStatus);
        jsonView=findViewById(R.id.jsonView);
        recordingStatus=findViewById(R.id.recordingStatus);
        rawAsrView=findViewById(R.id.rawAsrView);
        referenceInput=findViewById(R.id.referenceInput);
        cerStatus=findViewById(R.id.cerStatus);
        llmStatus=findViewById(R.id.llmStatus);
        manualInput=findViewById(R.id.manualInput);
        systemSwitch=findViewById(R.id.nsSwitch);
        rnSwitch=findViewById(R.id.rnSwitch);
        vadSwitch=findViewById(R.id.vadSwitch);
        vadStatus=findViewById(R.id.vadStatus);
        vadSwitch.setChecked(getSharedPreferences("asr_options",MODE_PRIVATE)
            .getBoolean("neural_vad",true));
        vadStatus.setText(vadSwitch.isChecked() ? "Silero VAD待启动 · 阈值0.50" : "VAD关闭：使用能量分段作对照");
        vadSwitch.setOnCheckedChangeListener((v,enabled)->{
            getSharedPreferences("asr_options",MODE_PRIVATE).edit()
                .putBoolean("neural_vad",enabled).apply();
            vadStatus.setText(enabled ? "Silero VAD待启动" : "VAD关闭：使用能量分段作对照");
        });
        lexiconStatus=findViewById(R.id.lexiconStatus);
        asrModeStatus=findViewById(R.id.asrModeStatus);
        asrModeGroup=findViewById(R.id.asrModeGroup);
        importLexiconBtn=findViewById(R.id.importLexiconBtn);
        resetLexiconBtn=findViewById(R.id.resetLexiconBtn);
        initLexiconUi();
        levelBar=findViewById(R.id.levelBar);
        startBtn=findViewById(R.id.startBtn);
        stopBtn=findViewById(R.id.stopBtn);
        playRaw=findViewById(R.id.playRawBtn);
        playEnhanced=findViewById(R.id.playEnhancedBtn);
        exportRaw=findViewById(R.id.exportRawBtn);
        exportEnhanced=findViewById(R.id.exportEnhancedBtn);
        // Avoid accidentally stacking aggressive processing.
        rnSwitch.setOnCheckedChangeListener((b,on)->{if(on)systemSwitch.setChecked(false);});
        systemSwitch.setOnCheckedChangeListener((b,on)->{if(on)rnSwitch.setChecked(false);});
        rnSwitch.setEnabled(RnnoiseBridge.available());
        if(!RnnoiseBridge.available()){
            rnSwitch.setChecked(false);
            audioStatus.setText("RNNoise原生库未载入，请检查NDK构建日志");
        }
        startBtn.setOnClickListener(v->requestStart());
        stopBtn.setOnClickListener(v->{
            if(engine!=null)engine.stop();
            stopBtn.setEnabled(false);audioStatus.setText("录音停止中，正在生成WAV与最终转写…");
        });
        playRaw.setOnClickListener(v->play(lastRaw));
        playEnhanced.setOnClickListener(v->play(lastEnhanced));
        exportRaw.setOnClickListener(v->export(lastRaw));
        exportEnhanced.setOnClickListener(v->export(lastEnhanced));
        findViewById(R.id.parseBtn).setOnClickListener(v->parseManual());
        findViewById(R.id.calcCerBtn).setOnClickListener(v->calculateCer());
        findViewById(R.id.exampleBtn).setOnClickListener(v->manualInput.setText(
            "查询三号终端的剩余电量，并把结果发给当前工作组"));
        findViewById(R.id.copyBtn).setOnClickListener(v->{
            ClipboardManager cb=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
            cb.setPrimaryClip(ClipData.newPlainText("voice.semantic.v1",lastJson));
            toast("语义JSON已复制到本机剪贴板");
        });
        findViewById(R.id.importLlmBtn).setOnClickListener(v->importModel());
        recordingButtons(false);
        loadSherpa();
        File savedModel=new File(new File(getFilesDir(),"llm"),"semantic.task");
        if(savedModel.isFile())loadLlm(savedModel);
        else llmStatus.setText("未导入本地小模型；使用规则Agent。可导入MediaPipe兼容的.task模型。");
    }
    private void initLexiconUi(){
        try {
            lexicon=DomainLexiconRepository.load(this);
            lexiconStatus.setText(lexicon.summary());
        } catch(Exception e) {
            lexiconStatus.setText("词典不可用："+e.getMessage());
        }
        boolean shortMode=getSharedPreferences("asr_options",MODE_PRIVATE)
            .getBoolean("short_mode",false);
        asrModeGroup.check(shortMode ? R.id.asrModeShort : R.id.asrModeNatural);
        asrModeGroup.setOnCheckedChangeListener((group,id)->{
            boolean shortSelected=id==R.id.asrModeShort;
            getSharedPreferences("asr_options",MODE_PRIVATE).edit()
                .putBoolean("short_mode",shortSelected).apply();
            updateAsrModeStatus();
        });
        updateAsrModeStatus();
        importLexiconBtn.setOnClickListener(v->{
            if(isRecording()){toast("请先停止录音再导入词典");return;}
            Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            startActivityForResult(intent,IMPORT_LEXICON);
        });
        resetLexiconBtn.setOnClickListener(v->{
            if(isRecording()){toast("请先停止录音再恢复词典");return;}
            worker.execute(()->{
                try{
                    DomainLexiconRepository.Snapshot restored=DomainLexiconRepository.reset(this);
                    lexicon=restored;
                    ui.post(()->{if(!destroyed){lexiconStatus.setText(restored.summary());updateAsrModeStatus();}});
                }catch(Exception e){ui.post(()->{if(!destroyed)toast("恢复词典失败："+e.getMessage());});}
            });
        });
    }
    private boolean isRecording(){return engine!=null && engine.isRunning();}
    private void updateAsrModeStatus(){
        if(asrModeGroup.getCheckedRadioButtonId()==R.id.asrModeShort){
            DomainLexiconRepository.Snapshot current=lexicon;
            asrModeStatus.setText(current==null ? "短指令模式：词典不可用，请导入或恢复默认词典"
                : "短指令模式："+current.phraseCount+"条参考短语（SenseVoice不支持Vosk式受限grammar；本版不强制替换识别结果）");
        }else asrModeStatus.setText("自然语言模式：SenseVoice开放识别（离线非流式分段）");
    }
    private void setAsrControlsEnabled(boolean enabled){
        asrModeGroup.setEnabled(enabled);
        for(int i=0;i<asrModeGroup.getChildCount();i++)asrModeGroup.getChildAt(i).setEnabled(enabled);
        importLexiconBtn.setEnabled(enabled);
        resetLexiconBtn.setEnabled(enabled);
        vadSwitch.setEnabled(enabled);
    }
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    private void recordingButtons(boolean enabled){
        playRaw.setEnabled(enabled);playEnhanced.setEnabled(enabled);
        exportRaw.setEnabled(enabled);exportEnhanced.setEnabled(enabled);
    }
    private void loadSherpa(){
        worker.execute(()->{
            try{
                File folder=ModelManager.ensureInstalled(getApplicationContext());
                vadModel=ModelManager.ensureVadInstalled(getApplicationContext());
                model=new SherpaAsrEngine(folder);
                ui.post(()->{if(!destroyed){
                    modelStatus.setText("Sherpa-ONNX SenseVoice INT8 + Silero VAD就绪 · 16kHz");startBtn.setEnabled(true);
                }});
            }catch(Throwable e){ui.post(()->{if(!destroyed){
                modelStatus.setText("无法载入离线ASR："+e.getMessage());startBtn.setEnabled(false);
            }});}
        });
    }
    private void requestStart(){
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},MIC_PERMISSION);
            return;
        }
        startCapture();
    }
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] grants){
        super.onRequestPermissionsResult(code,permissions,grants);
        if(code==MIC_PERMISSION){
            if(grants.length>0&&grants[0]==PackageManager.PERMISSION_GRANTED)startCapture();
            else toast("缺少麦克风权限");
        }
    }
    private void startCapture(){
        if(model==null){toast("离线ASR尚未就绪");return;}
        stopPlayback();
        lastRaw=null;lastEnhanced=null;rawTranscript="";enhancedTranscript="";recordingButtons(false);
        rawAsrView.setText("采集流ASR复核：录音停止后自动处理");
        cerStatus.setText("输入参考文字后，可计算采集/增强两路CER");
        transcriptView.setText("正在采音…");
        semanticStatus.setText("等待语音识别");
        lastJson="{}";jsonView.setText(lastJson);
        final boolean useNeuralVad=vadSwitch.isChecked();
        if(useNeuralVad && vadModel==null){toast("Silero VAD模型尚未装载");return;}
        vadStatus.setText(useNeuralVad ? "Silero VAD初始化中…" : "VAD关闭：使用能量分段作对照");
        final boolean shortMode=asrModeGroup.getCheckedRadioButtonId()==R.id.asrModeShort;
        final DomainLexiconRepository.Snapshot snapshot=lexicon;
        if(shortMode && snapshot==null){toast("领域词典未加载，请恢复默认词典");return;}
        // Snapshot of mode; SenseVoice does NOT support SenseVoice grammar. Never claim hard-constrained decoding.
        final String domainGrammar=shortMode ? snapshot.grammar : null; // retained only as metadata
        AudioCaptureEngine capture=new AudioCaptureEngine(this,model,vadModel,new AudioCaptureEngine.Listener(){
            @Override public void onVadState(boolean speaking,int segments){
                ui.post(()->{if(!destroyed){
                    vadStatus.setText(segments<0 ? "VAD关闭：能量分段对照" :
                        "Silero VAD："+(speaking?"检测到语音":"静音/等待语音")+" · 已切分"+segments+"段");
                }});
            }
            @Override public void onAudioStatus(String s){ui.post(()->{if(!destroyed)audioStatus.setText(s);});}
            @Override public void onLevel(int value,String detail){ui.post(()->{if(!destroyed)levelBar.setProgress(value);});}
            @Override public void onPartial(String s){ui.post(()->{if(!destroyed)transcriptView.setText(s);});}
            @Override public void onFinal(String s){ui.post(()->{
                if(destroyed)return;
                startBtn.setEnabled(true);stopBtn.setEnabled(false);levelBar.setProgress(0);
                setAsrControlsEnabled(true);
                transcriptView.setText(s.isEmpty()?"未识别到清晰语音":s);
                enhancedTranscript=s;
                if(shortMode && snapshot!=null){
                    String hint=ShortCommandHints.suggest(s,snapshot.phrases);
                    asrModeStatus.setText(hint.isEmpty()
                        ? "短指令模式：未找到明确的词典候选；保留SenseVoice原始转写"
                        : "领域词典候选（未自动替换）："+hint);
                }
                if(s.isEmpty())semanticStatus.setText("未识别到语音，请重录或使用文本测试");
                else renderSemantic(s,"voice");
            });}
            @Override public void onError(String msg){ui.post(()->{if(!destroyed){
                startBtn.setEnabled(true);stopBtn.setEnabled(false);
                setAsrControlsEnabled(true);
                audioStatus.setText(msg);toast(msg);
            }});}
            @Override public void onSaved(File raw,File enhanced,String details){ui.post(()->{
                if(destroyed)return;
                lastRaw=raw;lastEnhanced=enhanced;
                recordingButtons(true);
                recordingStatus.setText("已保存两段WAV（48kHz/16bit）\n原始采集流："+raw.getName()+
                    "\n增强输出流："+enhanced.getName()+"\n"+details+
                    "\n注意：原始流可能包含手机厂商的硬件前处理。");
                worker.execute(()->{
                    try{
                        String rawText=WavAsrComparator.transcribe(model,raw,vadModel,useNeuralVad);
                        ui.post(()->{if(!destroyed){
                            rawTranscript=rawText;
                            rawAsrView.setText("采集流ASR复核："+(rawText.isEmpty()?"未识别到":rawText));
                        }});
                    }catch(Exception e){ui.post(()->{if(!destroyed)
                        rawAsrView.setText("采集流ASR复核失败："+e.getMessage());});}
                });
            });}
        });
        if(capture.start(systemSwitch.isChecked(),rnSwitch.isChecked(),useNeuralVad)){
            engine=capture;startBtn.setEnabled(false);stopBtn.setEnabled(true);
            setAsrControlsEnabled(false);
            audioStatus.setText("开始采音…");
        }
    }
    private void calculateCer(){
        String truth=referenceInput.getText().toString().trim();
        if(truth.isEmpty()){toast("先填写人工确认的参考文字");return;}
        if(rawTranscript.isEmpty()||enhancedTranscript.isEmpty()){
            cerStatus.setText("请先录音并完成原始/增强双路识别");return;
        }
        double raw=CerMetrics.cer(truth,rawTranscript);
        double enhanced=CerMetrics.cer(truth,enhancedTranscript);
        cerStatus.setText(String.format(java.util.Locale.US,
            "采集流CER=%.1f%% · 增强流CER=%.1f%%（越低越好）",raw*100,enhanced*100));
    }
    private void parseManual(){
        String text=manualInput.getText().toString().trim();
        if(text.isEmpty()){toast("请先输入中文指令");return;}
        transcriptView.setText(text);renderSemantic(text,"manual_text");
    }
    private void renderSemantic(String text,String inputMode){
        semanticStatus.setText("本地语义Agent处理中…");
        worker.execute(()->{
            String result, status;
            try{
                if(llm.ready()){
                    String proposed=llm.infer(text);
                    JSONObject structured;
                    boolean repaired=false;
                    try{
                        structured=LlmSemanticValidator.normalize(proposed,text);
                    }catch(JSONException firstError){
                        // Do not accept arbitrary generic verbs (e.g. "query").
                        // Ask the on-device model for exactly one constrained repair.
                        String corrected=llm.repair(text,proposed,firstError.getMessage());
                        structured=LlmSemanticValidator.normalize(corrected,text);
                        repaired=true;
                    }
                    structured.getJSONObject("source").put("input_mode",inputMode);
                    result=structured.toString(2);
                    status=repaired
                        ? "本地小模型Agent：协议修正后通过校验 · 仅预览"
                        : "本地小模型Agent：动作协议校验通过 · 仅预览";
                }else{throw new IllegalStateException("未加载LLM");}
            }catch(Exception e){
                SemanticAgent.Result parsed=fallback.interpret(text,inputMode);
                result=new JSONObject(parsed.message).toString();
                try{result=new JSONObject(result).toString(2);}catch(Exception ignored){}
                status="规则Agent后备模式（未运行LLM）"+
                    (llm.ready()?"；小模型输出无效："+e.getMessage():"");
            }
            final String json=result;final String summary=status;
            ui.post(()->{if(!destroyed){
                lastJson=json;jsonView.setText(json);semanticStatus.setText(summary);
            }});
        });
    }
    private void importModel(){
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent,IMPORT_LLM);
    }
    private void loadLlm(File file){
        llmStatus.setText("正在本机加载小模型…");
        worker.execute(()->{
            try{
                llm.load(getApplicationContext(),file);
                ui.post(()->{if(!destroyed)llmStatus.setText("本地LLM已加载："+llm.modelName());});
            }catch(Throwable e){ui.post(()->{if(!destroyed){
                llmStatus.setText("LLM加载失败，保持规则Agent后备模式："+e.getMessage());
            }});}
        });
    }
    private void export(File source){
        if(source==null || !source.isFile()){toast("暂无录音文件");return;}
        exportSource=source;
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("audio/wav");i.putExtra(Intent.EXTRA_TITLE,source.getName());
        startActivityForResult(i,EXPORT_WAV);
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(resultCode!=RESULT_OK || data==null || data.getData()==null)return;
        Uri uri=data.getData();
        if(requestCode==IMPORT_LEXICON){
            if(isRecording()){toast("录音中不可导入词典");return;}
            lexiconStatus.setText("正在导入并校验词典JSON…");
            worker.execute(()->{
                try{
                    DomainLexiconRepository.Snapshot imported=DomainLexiconRepository.importUri(this,uri);
                    lexicon=imported;
                    ui.post(()->{if(!destroyed){
                        lexiconStatus.setText(imported.summary());updateAsrModeStatus();
                        toast("词典导入成功，下次录音生效");
                    }});
                }catch(Exception e){
                    ui.post(()->{if(!destroyed){
                        lexiconStatus.setText("导入失败，原词典仍保留："+e.getMessage());
                        updateAsrModeStatus();
                    }});
                }
            });
        }else if(requestCode==IMPORT_LLM){
            llmStatus.setText("正在导入模型文件…");
            worker.execute(()->{
                try{
                    File dir=new File(getFilesDir(),"llm");
                    if(!dir.exists()&&!dir.mkdirs())throw new IllegalStateException("不能创建LLM目录");
                    File temp=new File(dir,"importing.task");
                    long count=0;
                    try(InputStream in=getContentResolver().openInputStream(uri);
                        OutputStream out=new FileOutputStream(temp)){
                        if(in==null)throw new IllegalStateException("无法读取所选文件");
                        byte[] buf=new byte[65536];int n;
                        while((n=in.read(buf))>=0){
                            count+=n;if(count>3000000000L)throw new IllegalStateException("模型超过3GB限制");
                            out.write(buf,0,n);
                        }
                    }
                    if(count<1000000L)throw new IllegalStateException("模型文件过小，不是有效的.task权重文件");
                    // Release mmapped model before replacing its backing file.
                    llm.close();
                    File target=new File(dir,"semantic.task");
                    if(target.exists()&&!target.delete())throw new IllegalStateException("不能更新旧模型");
                    if(!temp.renameTo(target))throw new IllegalStateException("模型文件移动失败");
                    loadLlm(target);
                }catch(Exception e){ui.post(()->{if(!destroyed)llmStatus.setText("模型导入失败："+e.getMessage());});}
            });
        }else if(requestCode==EXPORT_WAV){
            final File src=exportSource;
            if(src==null)return;
            worker.execute(()->{
                try(InputStream in=new FileInputStream(src);
                    OutputStream out=getContentResolver().openOutputStream(uri)){
                    if(out==null)throw new IllegalStateException("不能写入导出目标");
                    byte[] buf=new byte[65536];int n;
                    while((n=in.read(buf))!=-1)out.write(buf,0,n);
                    ui.post(()->{if(!destroyed)toast("已导出WAV");});
                }catch(Exception e){ui.post(()->{if(!destroyed)toast("导出失败："+e.getMessage());});}
            });
        }
    }
    private void play(File file){
        if(file==null || !file.isFile()){toast("没有可回放的WAV");return;}
        stopPlayback();
        try{
            MediaPlayer mp=new MediaPlayer();
            player=mp;mp.setDataSource(file.getAbsolutePath());
            mp.setOnPreparedListener(MediaPlayer::start);
            mp.setOnCompletionListener(p->stopPlayback());
            mp.setOnErrorListener((p,a,b)->{stopPlayback();return true;});
            mp.prepareAsync();
            audioStatus.setText("播放："+file.getName());
        }catch(Exception e){stopPlayback();toast("回放失败："+e.getMessage());}
    }
    private void stopPlayback(){
        if(player!=null){try{player.release();}catch(Exception ignored){}player=null;}
    }
    @Override protected void onStop(){
        super.onStop();if(engine!=null)engine.stop();stopPlayback();
    }
    @Override protected void onDestroy(){
        destroyed=true;if(engine!=null)engine.stop();stopPlayback();
        worker.shutdown();
        // Avoid destroying Model before worker/ASR thread shutdown; release on process exit.
        super.onDestroy();
    }
}
