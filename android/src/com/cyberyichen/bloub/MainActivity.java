package com.cyberyichen.bloub;

import android.app.Activity;
import android.Manifest;
import android.content.pm.PackageManager;
import android.widget.FrameLayout;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;
import android.graphics.Color;
import android.os.*;
import android.util.Log;
import android.view.*;
import android.webkit.*;
import java.io.*;
import java.util.*;
import org.json.JSONObject;
import org.json.JSONArray;

/** Offline appliance: only packaged assets can execute, with no Internet permission; the optional ToF helper only reads one fixed node. */
public final class MainActivity extends Activity {
    private final Handler ui=new Handler(Looper.getMainLooper());
    private WebView web;
    private Lights lights;
    private SensorHub sensors;private TextureView cameraTexture;
    private boolean micEnabled,cameraEnabled,tofEnabled;
    private boolean foreground,enabled=true,ready;
    private long lastFrame;
    private static final String ORIGIN="https://appassets.androidplatform.net/";

    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON|WindowManager.LayoutParams.FLAG_FULLSCREEN);
        WindowManager.LayoutParams attrs=getWindow().getAttributes();attrs.screenBrightness=.42f;
        if(Build.VERSION.SDK_INT>=28)try{WindowManager.LayoutParams.class.getField("layoutInDisplayCutoutMode").setInt(attrs,Build.VERSION.SDK_INT>=30?3:1);}catch(Exception e){Log.w("BloubN5D","Cutout flag",e);}
        getWindow().setAttributes(attrs);
        web=new WebView(this);web.setBackgroundColor(Color.rgb(16,29,38));web.setKeepScreenOn(true);
        WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);settings.setMediaPlaybackRequiresUserGesture(true);
        WebView.setWebContentsDebuggingEnabled(true);
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view,String url){return true;}
            @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){
                String url=request.getUrl().toString();
                if(!url.startsWith(ORIGIN))return denied();
                String file=request.getUrl().getPath().substring(1);
                if(file.contains(".."))return denied();
                try{
                    String mime=file.endsWith(".js")?"application/javascript":file.endsWith(".css")?"text/css":file.endsWith(".svg")?"image/svg+xml":"text/html";
                    Map<String,String> headers=new HashMap<>();
                    headers.put("Content-Security-Policy","default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'none'; object-src 'none'; base-uri 'none'");
                    return new WebResourceResponse(mime,"UTF-8",200,"OK",headers,getAssets().open(file));
                }catch(IOException e){return denied();}
            }
            @Override public void onPageFinished(WebView view,String url){Log.i("BloubN5D","PAGE_READY "+url);}
        });
        web.setWebChromeClient(new WebChromeClient(){@Override public boolean onConsoleMessage(ConsoleMessage m){Log.i("BloubWeb",m.messageLevel()+" "+m.message()+" @"+m.lineNumber());return true;}});
        web.addJavascriptInterface(new Bridge(),"N5D");FrameLayout canvas=new FrameLayout(this);
        cameraTexture=new TextureView(this);cameraTexture.setAlpha(.01f);
        canvas.addView(cameraTexture,new FrameLayout.LayoutParams(240,320));canvas.addView(web,new FrameLayout.LayoutParams(-1,-1));
        setContentView(canvas);hideBars();
        // Only an explicit ADB diagnostic launch activates timeline seeking.
        boolean debug=getIntent().getBooleanExtra("diagnostic",false);
        web.loadUrl(ORIGIN+"n5d.html"+(debug?"?debug=1"+(getIntent().getBooleanExtra("rig",false)?"&rig=1":""):""));
    }
    private WebResourceResponse denied(){return new WebResourceResponse("text/plain","UTF-8",new ByteArrayInputStream(new byte[0]));}
    private void hideBars(){getWindow().getDecorView().setSystemUiVisibility(5894|View.SYSTEM_UI_FLAG_LAYOUT_STABLE);}
    private void status(String code){
        Log.i("BloubN5D","LIGHT_STATUS "+code);
        if(web!=null&&ready)web.evaluateJavascript("window.n5dStatus&&window.n5dStatus("+JSONObject.quote(code)+")",null);
    }
    @Override public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);if(focus)hideBars();}
    @Override public void onResume(){super.onResume();foreground=true;if(web!=null)web.onResume();lights=new Lights(this,this::status);status(enabled?"IDLE":"OFF");restartSensors();}
    @Override public void onPause(){foreground=false;if(sensors!=null){sensors.stop();sensors=null;}if(lights!=null){lights.stop();lights=null;}if(web!=null)web.onPause();super.onPause();}
    @Override public void onDestroy(){if(web!=null){web.removeJavascriptInterface("N5D");web.destroy();web=null;}super.onDestroy();}
    @Override public void onBackPressed(){web.evaluateJavascript("document.dispatchEvent(new KeyboardEvent('keydown',{key:'Escape',bubbles:true}))",null);}

    private void restartSensors(){
        if(sensors!=null){sensors.stop();sensors=null;}
        if(!foreground)return;
        sensors=new SensorHub(this,cameraTexture,micEnabled,cameraEnabled,tofEnabled,data->{
            if(foreground&&ready&&web!=null)web.evaluateJavascript("window.n5dSensors&&window.n5dSensors("+data.toString()+")",null);
        });
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);if(request==41)restartSensors();}
    private final class Bridge {
        @JavascriptInterface public void sensors(boolean mic,boolean camera,boolean tof){ui.post(()->{
            micEnabled=mic;cameraEnabled=camera;tofEnabled=tof;
            ArrayList<String> missing=new ArrayList<>();
            if(mic&&checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)missing.add(Manifest.permission.RECORD_AUDIO);
            if(camera&&checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)missing.add(Manifest.permission.CAMERA);
            restartSensors();if(!missing.isEmpty())requestPermissions(missing.toArray(new String[0]),41);
        });}
        @JavascriptInterface public void ready(){ui.post(()->{ready=true;status(enabled?"IDLE":"OFF");});}
        @JavascriptInterface public void enableLights(boolean on){ui.post(()->{enabled=on;if(!on&&lights!=null)lights.release("OFF");status(on?"IDLE":"OFF");});}
        @JavascriptInterface public void brightness(double value){
            if(!Double.isFinite(value))return;
            ui.post(()->{WindowManager.LayoutParams a=getWindow().getAttributes();a.screenBrightness=(float)Math.max(.15,Math.min(.75,value));getWindow().setAttributes(a);});
        }
        @JavascriptInterface public void frame(String json){
            // Drop before enqueueing; at most one small update per 80 ms from local JS.
            long now=SystemClock.elapsedRealtime();if(now-lastFrame<80||json==null||json.length()>2048)return;lastFrame=now;
            try{
                JSONObject o=new JSONObject(json);
                double mix=o.getDouble("mix");boolean active=o.getBoolean("active");
                if(!Double.isFinite(mix)||mix<0||mix>1)return;
                JSONArray rgbData=o.getJSONArray("rgb"),whiteData=o.getJSONArray("white");
                if(rgbData.length()!=72||whiteData.length()!=24)return;
                byte[] rgb=new byte[72],white=new byte[24];
                for(int i=0;i<96;i++){
                    double value=i<72?rgbData.getDouble(i):whiteData.getDouble(i-72);
                    if(!Double.isFinite(value)||value<0||value>255||value!=Math.floor(value))return;
                    if(i<72)rgb[i]=(byte)(int)value;else white[i-72]=(byte)(int)value;
                }
                ui.post(()->{if(foreground&&enabled&&lights!=null)lights.update(active,mix,rgb,white);});
            }catch(Exception e){Log.w("BloubN5D","Ignored malformed frame");}
        }
        @JavascriptInterface public void openProject(){ui.post(()->{
            // Fixed public project link only; no untrusted URL crosses the bridge.
            Intent i=new Intent(Intent.ACTION_VIEW,Uri.parse("https://github.com/Cyber-Yichen/Bloub-N5D"));
            try{startActivity(i);}catch(android.content.ActivityNotFoundException e){
                android.content.ClipboardManager clipboard=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Bloub GitHub","https://github.com/Cyber-Yichen/Bloub-N5D"));
                Toast.makeText(MainActivity.this,"未安装浏览器，项目链接已复制",Toast.LENGTH_LONG).show();
            }
        });}
        @JavascriptInterface public void openRingStudio(){ui.post(()->{Intent i=getPackageManager().getLaunchIntentForPackage("com.codex.ringlab");if(i!=null)startActivity(i);else status("UNAVAILABLE");});}
    }
}
