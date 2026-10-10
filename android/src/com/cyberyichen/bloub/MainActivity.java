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
import org.json.JSONException;

/** Packaged UI only; optional authenticated LAN downloads. ToF reads one fixed node. */
public final class MainActivity extends Activity {
    private final Handler ui=new Handler(Looper.getMainLooper());
    private DeviceSettings settings;private MonitorRecorder monitor;private SeatMonitor seat;private Gallery gallery;private GalleryServer galleryServer;
    private WebView web;
    private Lights lights;
    private volatile SensorHub sensors;private TextureView cameraTexture;
    private volatile boolean micEnabled,cameraEnabled,tofEnabled;
    private volatile boolean foreground;private boolean enabled=true,ready;
    private long lastFrame;
    private static final String ORIGIN="https://appassets.androidplatform.net/";

    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);settings=new DeviceSettings(this);monitor=new MonitorRecorder(this);seat=new SeatMonitor(this);gallery=new Gallery(this);gallery.prune();gallery.schedule();galleryServer=new GalleryServer(this,gallery,new GalleryServer.CameraControl(){
            public String status(){SensorHub hub=sensors;try{return new JSONObject(hub==null?"{}":hub.remoteStatus()).put("available",foreground&&cameraEnabled&&checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED).put("saving",gallery.enabled()).toString();}catch(Exception e){return "{}";}}
            public boolean command(String action){SensorHub hub=sensors;return foreground&&cameraEnabled&&hub!=null&&hub.remote(action);}
            public SensorHub.StreamFrame frame(){SensorHub hub=sensors;return hub==null?null:hub.streamFrame();}
            public void heartbeat(){SensorHub hub=sensors;if(hub!=null)hub.streamHeartbeat();}
            public String presence(int days){return seat.snapshot(days);}
            public String detections(){return seat.detections();}
            public String settings(){return settings.get().toString();}
            public String updateSettings(JSONObject delta)throws Exception{return applySettings(delta);}
            public String monitor(){return monitor.info();}
            public void monitorConfig(JSONObject delta)throws Exception{monitor.config.save(delta);monitor.retry();}
            public void monitorRetry(){monitor.retry();}
            public InputStream video(String id)throws IOException{return monitor.open(id);}
            public boolean videoDelete(String id){return monitor.delete(id);}
            public String seatControl(String action,JSONObject data)throws Exception{if(action.equals("region")){JSONArray r=data.getJSONArray("roi");if(r.length()!=4||!Double.isFinite(r.getDouble(0))||!Double.isFinite(r.getDouble(1))||!Double.isFinite(r.getDouble(2))||!Double.isFinite(r.getDouble(3))||r.getDouble(0)<0||r.getDouble(1)<0||r.getDouble(2)>1||r.getDouble(3)>1||r.getDouble(2)-r.getDouble(0)<.15||r.getDouble(3)-r.getDouble(1)<.15)throw new JSONException("区域无效");seat.region((float)r.getDouble(0),(float)r.getDouble(1),(float)r.getDouble(2),(float)r.getDouble(3));return "{\"accepted\":true}";}if(action.equals("label")){String value=data.getString("state");if(!value.matches("empty|occupied"))throw new JSONException("状态无效");return seat.label(value);}if(action.equals("preview")){seat.preview(data.getBoolean("enabled"));if(data.getBoolean("enabled"))ui.post(()->{if(sensors!=null)sensors.seatWake();});return "{\"accepted\":true}";}throw new JSONException("操作无效");}
        });
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
                    if(file.startsWith("observations/")&&file.endsWith(".jpg")){String id=file.substring(13,file.length()-4);Map<String,String> h=new HashMap<>();h.put("Cache-Control","no-store");return new WebResourceResponse("image/jpeg",null,200,"OK",h,gallery.open(id));}
                    if(file.startsWith("thumbnails/")&&file.endsWith(".jpg")){String id=file.substring(11,file.length()-4);Map<String,String> h=new HashMap<>();h.put("Cache-Control","no-store");return new WebResourceResponse("image/jpeg",null,200,"OK",h,gallery.thumbnail(id));}
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
    @Override public void onResume(){super.onResume();foreground=true;monitor.foreground(true);seat.resume();if(galleryServer!=null)galleryServer.start();if(web!=null)web.onResume();lights=new Lights(this,this::status);status(enabled?"IDLE":"OFF");restartSensors();}
    @Override public void onPause(){foreground=false;monitor.foreground(false);seat.pause();if(galleryServer!=null)galleryServer.stop();if(sensors!=null){sensors.stop();sensors=null;}if(lights!=null){lights.stop();lights=null;}if(web!=null)web.onPause();super.onPause();}
    @Override public void onDestroy(){if(monitor!=null)monitor.destroy();if(seat!=null)seat.destroy();if(web!=null){web.removeJavascriptInterface("N5D");web.destroy();web=null;}super.onDestroy();}
    @Override public void onBackPressed(){web.evaluateJavascript("document.dispatchEvent(new KeyboardEvent('keydown',{key:'Escape',bubbles:true}))",null);}

    private void restartSensors(){
        if(sensors!=null){sensors.stop();sensors=null;}
        if(!foreground)return;
        sensors=new SensorHub(this,cameraTexture,micEnabled,cameraEnabled,tofEnabled,seat,monitor,data->{
            if(foreground&&ready&&web!=null)web.evaluateJavascript("window.n5dSensors&&window.n5dSensors("+data.toString()+")",null);
        });
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);if(request==41)restartSensors();}
    private String applySettings(JSONObject delta)throws Exception{
        JSONObject saved=settings.save(delta);ui.post(()->{try{JSONObject d=settings.get();
          boolean newMic=d.getBoolean("mic"),newCamera=d.getBoolean("camera"),newTof=d.getBoolean("tof");String cameraId=d.getString("cameraId");boolean sensorChange=newMic!=micEnabled||newCamera!=cameraEnabled||newTof!=tofEnabled||!getSharedPreferences("sensors",0).getString("camera_id","2").equals(cameraId);
          getSharedPreferences("sensors",0).edit().putString("camera_id",cameraId).putInt("quiet_start",SettingsSpec.minutes(d.getString("quietStart"))).putInt("quiet_end",SettingsSpec.minutes(d.getString("quietEnd"))).apply();seat.cameraChanged(cameraId);
          micEnabled=newMic;cameraEnabled=newCamera;tofEnabled=newTof;enabled=d.getBoolean("lights");gallery.enabled(d.getBoolean("gallerySaving"));if(seat.enabled()!=d.getBoolean("seatEnabled"))seat.enabled(d.getBoolean("seatEnabled"));
          if(!enabled&&lights!=null)lights.release("OFF");WindowManager.LayoutParams a=getWindow().getAttributes();a.screenBrightness=d.getInt("brightness")/100f;getWindow().setAttributes(a);
          ArrayList<String> missing=new ArrayList<>();if(newMic&&checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)missing.add(Manifest.permission.RECORD_AUDIO);if(newCamera&&checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)missing.add(Manifest.permission.CAMERA);
          if(sensorChange)restartSensors();else if(sensors!=null){sensors.quietHours(SettingsSpec.minutes(d.getString("quietStart")),SettingsSpec.minutes(d.getString("quietEnd")));if(delta.has("monitorEnabled"))sensors.monitorChanged();}
          if(!missing.isEmpty())requestPermissions(missing.toArray(new String[0]),41);
          if(web!=null&&ready)web.evaluateJavascript("window.n5dApplySettings&&window.n5dApplySettings("+d.toString()+")",null);
          if(delta.has("galleryServerEnabled")){galleryServer.enabled(d.getBoolean("galleryServerEnabled"));if(foreground&&galleryServer.enabled())galleryServer.start();else ui.postDelayed(()->{if(!galleryServer.enabled())galleryServer.stop();},350);}
        }catch(Exception e){Log.w("BloubN5D","Apply settings",e);}});return saved.toString();
    }
    private final class Bridge {
        @JavascriptInterface public String settingsInfo(){try{return settings.get().put("initialized",settings.initialized()).put("seatEnabled",seat.enabled()).put("gallerySaving",gallery.enabled()).put("galleryServerEnabled",galleryServer.enabled()).toString();}catch(Exception e){return "{}";}}
        @JavascriptInterface public String settingsSave(String json){try{return applySettings(new JSONObject(json));}catch(Exception e){return "{}";}}
        @JavascriptInterface public String monitorInfo(){return monitor.info();}
        @JavascriptInterface public void monitorEnabled(boolean value){try{JSONObject d=new JSONObject().put("monitorEnabled",value);if(value)d.put("camera",true);applySettings(d);}catch(Exception ignored){}}
        @JavascriptInterface public String seatInfo(){return seat.snapshot(7);}
        @JavascriptInterface public String seatFrame(){return seat.frame();}
        @JavascriptInterface public void seatEnabled(boolean value){ui.post(()->{seat.enabled(value);settings.remember("seatEnabled",value);SensorHub hub=sensors;if(hub!=null)hub.seatWake();});}
        @JavascriptInterface public void seatPreview(boolean value){seat.preview(value);ui.post(()->{SensorHub hub=sensors;if(value&&hub!=null)hub.seatWake();});}
        @JavascriptInterface public void seatRegion(float left,float top,float right,float bottom){seat.region(left,top,right,bottom);}
        @JavascriptInterface public String seatLabel(String kind){return seat.label(kind);}

        @JavascriptInterface public String galleryServerInfo(){return galleryServer.info();}
        @JavascriptInterface public void galleryServerEnabled(boolean value){ui.post(()->{galleryServer.enabled(value);settings.remember("galleryServerEnabled",value);if(value&&foreground)galleryServer.start();else ui.postDelayed(()->{if(!galleryServer.enabled())galleryServer.stop();},350);});}
        @JavascriptInterface public void copyGalleryAddress(){ui.post(()->{try{String address=new JSONObject(galleryServer.info()).optString("url");if(!address.isEmpty()){android.content.ClipboardManager clipboard=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Bloub 局域网图库",address));Toast.makeText(MainActivity.this,"下载网址已复制",Toast.LENGTH_SHORT).show();}}catch(Exception ignored){}});}
        @JavascriptInterface public void quietHours(int start,int end){if(start<0||start>=1440||end<0||end>=1440)return;ui.post(()->{getSharedPreferences("sensors",0).edit().putInt("quiet_start",start).putInt("quiet_end",end).apply();SensorHub hub=sensors;if(hub!=null)hub.quietHours(start,end);});}
        @JavascriptInterface public String cameraInfo(){return SensorHub.cameraInfo(MainActivity.this);}
        @JavascriptInterface public void cameraSource(String id){if(!"2".equals(id)&&!"3".equals(id))return;ui.post(()->{String old=getSharedPreferences("sensors",0).getString("camera_id","2");getSharedPreferences("sensors",0).edit().putString("camera_id",id).apply();seat.cameraChanged(id);if(!id.equals(old)&&cameraEnabled)restartSensors();});}
        @JavascriptInterface public String galleryList(long before){return gallery.list(before);}
        @JavascriptInterface public void galleryEnabled(boolean value){gallery.enabled(value);settings.remember("gallerySaving",value);}
        @JavascriptInterface public boolean galleryDelete(String id){return gallery.delete(id);}
        @JavascriptInterface public void galleryClear(){gallery.clear();}

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
            ui.post(()->{WindowManager.LayoutParams a=getWindow().getAttributes();a.screenBrightness=(float)Math.max(.15,Math.min(1,value));getWindow().setAttributes(a);});
        }
        @JavascriptInterface public void frame(String json){
            // Drop before enqueueing; at most one small update per 40 ms from local JS.
            long now=SystemClock.elapsedRealtime();if(now-lastFrame<40||json==null||json.length()>2048)return;lastFrame=now;
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
