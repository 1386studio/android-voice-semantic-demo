package com.example.voicesemantic;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONObject;
import org.json.JSONException;
import org.vosk.Model;
import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 三段式本地Demo：手机采音 -> 离线ASR + 规则型Agent -> 标准语义预览。 */
public final class MainActivity extends Activity {
    private static final int MICROPHONE_PERMISSION=1001;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final ExecutorService loader=Executors.newSingleThreadExecutor();
    private final SemanticAgent semanticAgent=new SemanticAgent();
    private TextView modelStatus,audioStatus,transcriptView,semanticStatus,jsonView;
    private EditText manualInput;
    private Switch nsSwitch;
    private ProgressBar levelBar;
    private Button startBtn,stopBtn;
    private volatile Model model;
    private volatile AudioCaptureEngine engine;
    private volatile String lastJson="{}";
    private volatile boolean destroyed=false;

    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        setContentView(R.layout.activity_main);
        modelStatus=findViewById(R.id.modelStatus);
        audioStatus=findViewById(R.id.audioStatus);
        transcriptView=findViewById(R.id.transcriptView);
        semanticStatus=findViewById(R.id.semanticStatus);
        jsonView=findViewById(R.id.jsonView);
        manualInput=findViewById(R.id.manualInput);
        nsSwitch=findViewById(R.id.nsSwitch);
        levelBar=findViewById(R.id.levelBar);
        startBtn=findViewById(R.id.startBtn);
        stopBtn=findViewById(R.id.stopBtn);
        startBtn.setOnClickListener(v->requestStart());
        stopBtn.setOnClickListener(v->{
            AudioCaptureEngine e=engine;
            if(e!=null)e.stop();
            stopBtn.setEnabled(false);
            audioStatus.setText("已请求停止，正在解码最后音频…");
        });
        findViewById(R.id.parseBtn).setOnClickListener(v->parseManual());
        findViewById(R.id.exampleBtn).setOnClickListener(v->manualInput.setText(
            "查询三号终端的剩余电量，并把结果发给当前工作组"));
        findViewById(R.id.copyBtn).setOnClickListener(v->{
            ClipboardManager clip=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
            clip.setPrimaryClip(ClipData.newPlainText("voice.semantic.v1",lastJson));
            Toast.makeText(this,"已复制JSON（仅本机剪贴板）",Toast.LENGTH_SHORT).show();
        });
        loadModel();
    }

    private void loadModel(){
        loader.execute(()->{
            try{
                File installed=ModelManager.ensureInstalled(getApplicationContext());
                Model loaded=new Model(installed.getAbsolutePath());
                model=loaded;
                ui.post(()->{
                    if(destroyed)return;
                    modelStatus.setText("离线中文ASR就绪（Vosk Small CN），断网可用");
                    startBtn.setEnabled(true);
                });
            }catch(Exception ex){
                ui.post(()->{
                    if(destroyed)return;
                    modelStatus.setText("未载入ASR："+ex.getMessage());
                    startBtn.setEnabled(false);
                });
            }
        });
    }

    private void requestStart(){
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},MICROPHONE_PERMISSION);
            return;
        }
        startCapture();
    }
    @Override public void onRequestPermissionsResult(int code,String[] p,int[] grants){
        super.onRequestPermissionsResult(code,p,grants);
        if(code==MICROPHONE_PERMISSION){
            if(grants.length>0&&grants[0]==PackageManager.PERMISSION_GRANTED)startCapture();
            else Toast.makeText(this,"需要麦克风权限才能采音",Toast.LENGTH_LONG).show();
        }
    }

    private void startCapture(){
        if(model==null){Toast.makeText(this,"请先安装离线语音模型",Toast.LENGTH_SHORT).show();return;}
        transcriptView.setText("正在等待语音输入…");
        semanticStatus.setText("正在采音 / 转写中");
        jsonView.setText("{}");lastJson="{}";
        AudioCaptureEngine instance=new AudioCaptureEngine(model,new AudioCaptureEngine.Listener(){
            @Override public void onAudioStatus(String s){ui.post(()->{if(!destroyed)audioStatus.setText(s);});}
            @Override public void onLevel(int value,String detail){ui.post(()->{
                if(destroyed)return;
                levelBar.setProgress(value);
            });}
            @Override public void onPartial(String text){ui.post(()->{
                if(!destroyed)transcriptView.setText(text);
            });}
            @Override public void onFinal(String text){ui.post(()->{
                if(destroyed)return;
                startBtn.setEnabled(true); stopBtn.setEnabled(false);
                levelBar.setProgress(0);
                transcriptView.setText(text.isEmpty()?"未检测到可识别语音":text);
                audioStatus.setText("离线识别完成，音频未保存");
                if(!text.isEmpty())renderSemantic(text,"voice");
                else semanticStatus.setText("没有识别内容，请重试或使用文本测试");
            });}
            @Override public void onError(String msg){ui.post(()->{
                if(destroyed)return;
                startBtn.setEnabled(true);stopBtn.setEnabled(false);
                audioStatus.setText(msg);
                Toast.makeText(MainActivity.this,msg,Toast.LENGTH_LONG).show();
            });}
        });
        if(instance.start(nsSwitch.isChecked())){
            engine=instance;
            startBtn.setEnabled(false);stopBtn.setEnabled(true);
            audioStatus.setText("采音初始化中…");
        }
    }
    private void parseManual(){
        String text=manualInput.getText().toString().trim();
        if(text.isEmpty()){
            Toast.makeText(this,"先输入一条中文指令",Toast.LENGTH_SHORT).show();return;
        }
        transcriptView.setText(text);
        renderSemantic(text,"manual_text");
    }
    private void renderSemantic(String text,String mode){
        SemanticAgent.Result parsed=semanticAgent.interpret(text,mode);
        JSONObject output=new JSONObject(parsed.message);
        try {
            lastJson=output.toString(2);
        } catch (JSONException ex) {
            lastJson=output.toString();
        }
        jsonView.setText(lastJson);
        semanticStatus.setText("操作步骤："+parsed.actionCount+
            " · "+(parsed.requiresConfirmation?"需要人工确认":"仅生成语义")+
            " · 待核查项："+parsed.warnings.size());
    }
    @Override protected void onStop(){
        super.onStop();
        // 演示版不使用后台麦克风前台服务，离开界面即停止录音。
        AudioCaptureEngine e=engine;
        if(e!=null)e.stop();
    }
    @Override protected void onDestroy(){
        destroyed=true;
        AudioCaptureEngine e=engine;
        if(e!=null)e.stop();
        loader.shutdown();
        // Model不能在识别线程还可能运行时释放；随应用进程回收。
        super.onDestroy();
    }
}
